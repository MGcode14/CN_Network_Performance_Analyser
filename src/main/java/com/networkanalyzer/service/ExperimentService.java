package com.networkanalyzer.service;

import com.networkanalyzer.analyzer.ComparisonEngine;
import com.networkanalyzer.client.TestProgressListener;
import com.networkanalyzer.database.DatabaseManager;
import com.networkanalyzer.exception.ValidationException;
import com.networkanalyzer.metrics.MetricsCalculator;
import com.networkanalyzer.model.*;
import com.networkanalyzer.server.NetworkServerManager;
import com.networkanalyzer.tcp.TcpTestEngine;
import com.networkanalyzer.udp.UdpTestEngine;
import com.networkanalyzer.validation.ConfigValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

public class ExperimentService {
    private static final Logger logger = LoggerFactory.getLogger(ExperimentService.class);

    private final DatabaseManager databaseManager;
    private final NetworkServerManager serverManager;
    private final ExecutorService testExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "experiment-runner");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, Experiment> activeExperiments = new ConcurrentHashMap<>();
    private final Map<String, Double> activeProgressMap = new ConcurrentHashMap<>();
    private final Map<String, String> activeStatusMessageMap = new ConcurrentHashMap<>();

    private volatile TcpTestEngine activeTcpEngine = null;
    private volatile UdpTestEngine activeUdpEngine = null;

    public ExperimentService(DatabaseManager databaseManager, NetworkServerManager serverManager) {
        this.databaseManager = databaseManager;
        this.serverManager = serverManager;
    }

    public synchronized Experiment startExperiment(TestConfig config) throws ValidationException {
        ConfigValidator.validate(config);

        if (!activeExperiments.isEmpty()) {
            throw new IllegalStateException("An experiment is currently in progress. Please wait for it to complete or cancel it.");
        }

        String testId = UUID.randomUUID().toString();
        Experiment placeholder = new Experiment();
        placeholder.setId(UUID.randomUUID().toString());
        placeholder.setTestId(testId);
        placeholder.setName(config.getProtocol() + " " + config.getTestMode());
        placeholder.setProtocol(config.getProtocol());
        placeholder.setTestMode(config.getTestMode());
        placeholder.setStatus(TestStatus.PENDING);
        placeholder.setConfig(config);
        placeholder.setTargetHost(config.getTargetHost());
        placeholder.setTargetPort(config.getProtocol() == Protocol.TCP ? config.getTcpPort() : config.getUdpPort());
        placeholder.setPayloadSize(config.getPayloadSize());
        placeholder.setStartTimeMs(System.currentTimeMillis());

        activeExperiments.put(placeholder.getId(), placeholder);
        activeProgressMap.put(placeholder.getId(), 0.0);
        activeStatusMessageMap.put(placeholder.getId(), "Queued for execution");

        testExecutor.submit(() -> executeExperiment(placeholder, config));
        return placeholder;
    }

    private void executeExperiment(Experiment placeholder, TestConfig config) {
        try {
            placeholder.setStatus(TestStatus.RUNNING);
            databaseManager.logServerEvent("TEST_START", "Started " + config.getProtocol() + " test: " + placeholder.getName(), "Host: " + config.getTargetHost());

            TestProgressListener listener = (tId, current, total, percent, msg) -> {
                activeProgressMap.put(placeholder.getId(), percent);
                activeStatusMessageMap.put(placeholder.getId(), msg);
            };

            if (config.getProtocol() == Protocol.TCP) {
                activeTcpEngine = new TcpTestEngine();
                Experiment result = activeTcpEngine.runTest(config, listener);
                result.setId(placeholder.getId());
                completeExperiment(result);
            } else if (config.getProtocol() == Protocol.UDP) {
                activeUdpEngine = new UdpTestEngine(serverManager.getUdpServer());
                Experiment result = activeUdpEngine.runTest(config, listener);
                result.setId(placeholder.getId());
                completeExperiment(result);
            } else if (config.getProtocol() == Protocol.BOTH) {
                executeComparisonPair(placeholder, config, listener);
            }
        } catch (Exception e) {
            logger.error("Error executing experiment: {}", e.getMessage(), e);
            placeholder.setStatus(TestStatus.FAILED);
            placeholder.setErrorMessage(e.getMessage());
            completeExperiment(placeholder);
        } finally {
            activeTcpEngine = null;
            activeUdpEngine = null;
            activeExperiments.remove(placeholder.getId());
            activeProgressMap.remove(placeholder.getId());
            activeStatusMessageMap.remove(placeholder.getId());
        }
    }

    private void executeComparisonPair(Experiment placeholder, TestConfig config, TestProgressListener listener) {
        // Run both protocols sequentially (or alternating order if specified)
        boolean tcpFirst = !config.isAlternatingOrder();

        TestConfig tcpConfig = cloneConfigForProtocol(config, Protocol.TCP);
        TestConfig udpConfig = cloneConfigForProtocol(config, Protocol.UDP);

        Experiment exp1;
        Experiment exp2;

        if (tcpFirst) {
            activeTcpEngine = new TcpTestEngine();
            exp1 = activeTcpEngine.runTest(tcpConfig, listener);
            databaseManager.saveExperiment(exp1);

            // Brief pause between protocols
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}

            activeUdpEngine = new UdpTestEngine(serverManager.getUdpServer());
            exp2 = activeUdpEngine.runTest(udpConfig, listener);
            databaseManager.saveExperiment(exp2);
        } else {
            activeUdpEngine = new UdpTestEngine(serverManager.getUdpServer());
            exp1 = activeUdpEngine.runTest(udpConfig, listener);
            databaseManager.saveExperiment(exp1);

            try { Thread.sleep(300); } catch (InterruptedException ignored) {}

            activeTcpEngine = new TcpTestEngine();
            exp2 = activeTcpEngine.runTest(tcpConfig, listener);
            databaseManager.saveExperiment(exp2);
        }

        // Aggregate main placeholder
        Experiment tcpExp = tcpFirst ? exp1 : exp2;
        Experiment udpExp = tcpFirst ? exp2 : exp1;

        boolean tcpFailed = tcpExp.getStatus() == TestStatus.FAILED || tcpExp.getStatus() == TestStatus.CANCELLED;
        boolean udpFailed = udpExp.getStatus() == TestStatus.FAILED || udpExp.getStatus() == TestStatus.CANCELLED;

        if (tcpFailed && udpFailed) {
            placeholder.setStatus(TestStatus.FAILED);
            placeholder.setErrorMessage("Both TCP and UDP tests failed: TCP (" + tcpExp.getErrorMessage() + "), UDP (" + udpExp.getErrorMessage() + ")");
        } else if (tcpFailed) {
            placeholder.setStatus(TestStatus.FAILED);
            placeholder.setErrorMessage("TCP test failed: " + tcpExp.getErrorMessage());
        } else if (udpFailed) {
            placeholder.setStatus(TestStatus.FAILED);
            placeholder.setErrorMessage("UDP test failed: " + udpExp.getErrorMessage());
        } else {
            placeholder.setStatus(TestStatus.COMPLETED);
            placeholder.setErrorMessage(null); // Keep error message clean for successful runs
        }

        placeholder.setEndTimeMs(System.currentTimeMillis());
        placeholder.setDurationMs((double) (placeholder.getEndTimeMs() - placeholder.getStartTimeMs()));

        long sentTotal = (tcpExp.getPacketsSent() != null ? tcpExp.getPacketsSent() : 0) + (udpExp.getPacketsSent() != null ? udpExp.getPacketsSent() : 0);
        long recvTotal = (tcpExp.getPacketsReceived() != null ? tcpExp.getPacketsReceived() : 0) + (udpExp.getPacketsReceived() != null ? udpExp.getPacketsReceived() : 0);
        placeholder.setPacketsSent(sentTotal);
        placeholder.setPacketsReceived(recvTotal);
        placeholder.setPacketLossPct(MetricsCalculator.calculatePacketLossPct(sentTotal, recvTotal));
        placeholder.setDeliveryRatioPct(MetricsCalculator.calculatePacketDeliveryRatioPct(sentTotal, recvTotal));

        if (tcpExp.getThroughputMbps() != null && udpExp.getThroughputMbps() != null) {
            placeholder.setThroughputMbps(MetricsCalculator.round((tcpExp.getThroughputMbps() + udpExp.getThroughputMbps()) / 2.0, 2));
        } else {
            placeholder.setThroughputMbps(tcpExp.getThroughputMbps() != null ? tcpExp.getThroughputMbps() : udpExp.getThroughputMbps());
        }

        if (tcpExp.getAvgRttMs() != null && udpExp.getAvgRttMs() != null) {
            placeholder.setAvgRttMs(MetricsCalculator.round((tcpExp.getAvgRttMs() + udpExp.getAvgRttMs()) / 2.0, 2));
        } else {
            placeholder.setAvgRttMs(tcpExp.getAvgRttMs() != null ? tcpExp.getAvgRttMs() : udpExp.getAvgRttMs());
        }

        List<MeasurementSample> combinedSamples = new ArrayList<>();
        if (tcpExp.getSamples() != null) combinedSamples.addAll(tcpExp.getSamples());
        if (udpExp.getSamples() != null) combinedSamples.addAll(udpExp.getSamples());
        placeholder.setSamples(combinedSamples);

        databaseManager.saveExperiment(placeholder);
        databaseManager.logServerEvent("TEST_COMPLETE", "Finished dual-protocol comparison experiment", "TCP: " + tcpExp.getId() + ", UDP: " + udpExp.getId());
    }

    private TestConfig cloneConfigForProtocol(TestConfig original, Protocol p) {
        TestConfig c = new TestConfig();
        c.setTargetHost(original.getTargetHost());
        c.setTcpPort(original.getTcpPort());
        c.setUdpPort(original.getUdpPort());
        c.setProtocol(p);
        c.setTestMode(original.getTestMode());
        c.setPayloadSize(original.getPayloadSize());
        c.setPacketCount(original.getPacketCount());
        c.setTotalBytes(original.getTotalBytes());
        c.setSendingRatePps(original.getSendingRatePps());
        c.setWarmupCount(original.getWarmupCount());
        c.setTimeoutMs(original.getTimeoutMs());
        c.setLinkCapacityMbps(original.getLinkCapacityMbps());
        return c;
    }

    private void completeExperiment(Experiment exp) {
        try {
            databaseManager.saveExperiment(exp);
            databaseManager.logServerEvent("TEST_COMPLETE", "Completed experiment: " + exp.getName() + " (" + exp.getStatus() + ")",
                    "Throughput: " + exp.getThroughputMbps() + " Mbps, RTT: " + exp.getAvgRttMs() + " ms");
        } catch (Exception e) {
            logger.error("Failed to persist experiment: {}", e.getMessage(), e);
        }
    }

    public boolean cancelExperiment(String id) {
        if (activeTcpEngine != null) {
            activeTcpEngine.cancel();
        }
        if (activeUdpEngine != null) {
            activeUdpEngine.cancel();
        }
        Experiment active = activeExperiments.get(id);
        if (active != null) {
            active.setStatus(TestStatus.CANCELLED);
            active.setErrorMessage("Cancellation requested by user.");
            databaseManager.logServerEvent("TEST_CANCELLED", "User cancelled experiment: " + id, null);
            return true;
        }
        return false;
    }

    public Experiment getExperiment(String id) {
        if (activeExperiments.containsKey(id)) {
            return activeExperiments.get(id);
        }
        return databaseManager.getExperiment(id);
    }

    public Map<String, Object> getActiveTestStatus() {
        if (activeExperiments.isEmpty()) {
            return Collections.emptyMap();
        }
        Map.Entry<String, Experiment> entry = activeExperiments.entrySet().iterator().next();
        String id = entry.getKey();
        Experiment exp = entry.getValue();

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("active", true);
        status.put("id", id);
        status.put("testId", exp.getTestId());
        status.put("name", exp.getName());
        status.put("protocol", exp.getProtocol().name());
        status.put("progress", activeProgressMap.getOrDefault(id, 0.0));
        status.put("message", activeStatusMessageMap.getOrDefault(id, "Processing..."));
        return status;
    }

    public ComparisonResult compareExperiments(String tcpExpId, String udpExpId) {
        Experiment tcpExp = databaseManager.getExperiment(tcpExpId);
        Experiment udpExp = databaseManager.getExperiment(udpExpId);
        return ComparisonEngine.compare(tcpExp, udpExp);
    }

    public List<Experiment> getHistory(String protocol, String testMode, String search, int limit, int offset) {
        return databaseManager.getAllExperiments(protocol, testMode, search, limit, offset);
    }

    public int getHistoryCount(String protocol, String testMode, String search) {
        return databaseManager.countExperiments(protocol, testMode, search);
    }

    public List<MeasurementSample> getSamples(String experimentId, int limit) {
        return databaseManager.getSamplesForExperiment(experimentId, limit);
    }

    public Map<String, Object> getMetricsSummary() {
        return databaseManager.getMetricsSummary();
    }

    public List<Map<String, Object>> getRecentEvents(int limit) {
        return databaseManager.getRecentServerEvents(limit);
    }

    public void shutdown() {
        testExecutor.shutdownNow();
    }
}
