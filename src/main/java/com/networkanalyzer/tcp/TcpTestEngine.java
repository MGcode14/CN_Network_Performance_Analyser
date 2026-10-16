package com.networkanalyzer.tcp;

import com.networkanalyzer.client.TestProgressListener;
import com.networkanalyzer.exception.NetworkException;
import com.networkanalyzer.metrics.MetricsCalculator;
import com.networkanalyzer.metrics.SampleStatistics;
import com.networkanalyzer.model.*;
import com.networkanalyzer.protocol.MessageType;
import com.networkanalyzer.protocol.NetworkPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class TcpTestEngine {
    private static final Logger logger = LoggerFactory.getLogger(TcpTestEngine.class);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public Experiment runTest(TestConfig config, TestProgressListener listener) {
        cancelled.set(false);
        String testIdStr = UUID.randomUUID().toString();
        UUID testUuid = UUID.fromString(testIdStr);

        Experiment exp = new Experiment();
        exp.setId(UUID.randomUUID().toString());
        exp.setTestId(testIdStr);
        exp.setName("TCP " + config.getTestMode() + " (" + config.getPayloadSize() + "B)");
        exp.setProtocol(Protocol.TCP);
        exp.setTestMode(config.getTestMode());
        exp.setStatus(TestStatus.RUNNING);
        exp.setConfig(config);
        exp.setTargetHost(config.getTargetHost());
        exp.setTargetPort(config.getTcpPort());
        exp.setPayloadSize(config.getPayloadSize());
        exp.setLinkCapacityMbps(config.getLinkCapacityMbps());

        long startEpoch = System.currentTimeMillis();
        exp.setStartTimeMs(startEpoch);

        // Step 1: Measure Connection Setup Time
        long connSetupStart = System.nanoTime();

        if (listener != null) {
            listener.onProgress(testIdStr, 0, config.getPacketCount(), 0.0, "Establishing TCP connection to " + config.getTargetHost() + ":" + config.getTcpPort());
        }

        try (Socket socket = new Socket()) {
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(config.getTimeoutMs());

            try {
                socket.connect(new InetSocketAddress(config.getTargetHost(), config.getTcpPort()), config.getTimeoutMs());
            } catch (IOException e) {
                throw new NetworkException("Failed to connect to TCP target " + config.getTargetHost() + ":" + config.getTcpPort() + " - " + e.getMessage(), e);
            }

            long connSetupNanos = System.nanoTime() - connSetupStart;
            double connSetupMs = MetricsCalculator.round(connSetupNanos / 1_000_000.0, 3);
            exp.setConnectionSetupTimeMs(connSetupMs);
            logger.info("TCP connection established in {} ms", connSetupMs);

            try (BufferedOutputStream bos = new BufferedOutputStream(socket.getOutputStream(), 65536);
                 DataOutputStream dos = new DataOutputStream(bos);
                 BufferedInputStream bis = new BufferedInputStream(socket.getInputStream(), 65536);
                 DataInputStream dis = new DataInputStream(bis)) {

                // Optional Warm-up Phase
                int warmup = config.getWarmupCount();
                if (warmup > 0 && !cancelled.get()) {
                    if (listener != null) {
                        listener.onProgress(testIdStr, 0, config.getPacketCount(), 0.0, "Performing warm-up (" + warmup + " packets)...");
                    }
                    byte[] warmupPayload = new byte[Math.min(config.getPayloadSize(), 1024)];
                    for (int w = 0; w < warmup; w++) {
                        if (cancelled.get()) break;
                        NetworkPacket req = NetworkPacket.create(MessageType.RTT_REQUEST, testUuid, -1L - w, System.nanoTime(), warmupPayload);
                        req.writeTo(dos);
                        NetworkPacket resp = NetworkPacket.readFrom(dis);
                    }
                }

                if (cancelled.get()) {
                    exp.setStatus(TestStatus.CANCELLED);
                    exp.setErrorMessage("Experiment cancelled by user.");
                    return finishExperiment(exp);
                }

                if (config.getTestMode() == TestMode.RTT) {
                    runRttTest(config, testUuid, dos, dis, exp, listener);
                } else if (config.getTestMode() == TestMode.BULK_TRANSFER) {
                    runBulkTransferTest(config, testUuid, dos, dis, exp, listener);
                } else {
                    // Packet Delivery mode over TCP
                    runPacketDeliveryTest(config, testUuid, dos, dis, exp, listener);
                }

            }
        } catch (Exception e) {
            logger.error("TCP experiment error: {}", e.getMessage(), e);
            if (cancelled.get()) {
                exp.setStatus(TestStatus.CANCELLED);
                exp.setErrorMessage("Experiment cancelled by user.");
            } else {
                exp.setStatus(TestStatus.FAILED);
                exp.setErrorMessage(e.getMessage());
            }
        }

        return finishExperiment(exp);
    }

    private void runRttTest(TestConfig config, UUID testUuid, DataOutputStream dos, DataInputStream dis,
                            Experiment exp, TestProgressListener listener) throws IOException {
        int count = config.getPacketCount();
        byte[] payload = new byte[config.getPayloadSize()];
        List<Double> rttSamples = new ArrayList<>(count);
        List<MeasurementSample> samples = new ArrayList<>(count);

        long packetsSent = 0;
        long packetsReceived = 0;
        long bytesSent = 0;
        long bytesReceived = 0;

        long transferStartNanos = System.nanoTime();

        for (int i = 1; i <= count; i++) {
            if (cancelled.get()) {
                exp.setStatus(TestStatus.CANCELLED);
                exp.setErrorMessage("Experiment cancelled during RTT test.");
                break;
            }

            long reqSendNanos = System.nanoTime();
            NetworkPacket req = NetworkPacket.create(MessageType.RTT_REQUEST, testUuid, i, reqSendNanos, payload);
            req.writeTo(dos);
            packetsSent++;
            bytesSent += config.getPayloadSize();

            try {
                NetworkPacket resp = NetworkPacket.readFrom(dis);
                long respRecvNanos = System.nanoTime();
                packetsReceived++;
                bytesReceived += resp.getPayloadLength();

                double sampleRttMs = MetricsCalculator.calculateRttMs(reqSendNanos, respRecvNanos);
                rttSamples.add(sampleRttMs);

                MeasurementSample sample = new MeasurementSample(
                        exp.getId(),
                        i,
                        sampleRttMs,
                        null,
                        System.currentTimeMillis()
                );
                samples.add(sample);

                if (listener != null) {
                    double progress = (i * 100.0) / count;
                    listener.onProgress(exp.getTestId(), i, count, progress, String.format("Sample %d/%d RTT: %.2f ms", i, count, sampleRttMs));
                    listener.onSample(exp.getTestId(), sample);
                }
            } catch (SocketTimeoutException ste) {
                logger.warn("TCP RTT request timed out for sequence {}", i);
            }
        }

        long transferDurationNanos = System.nanoTime() - transferStartNanos;
        double durationSec = transferDurationNanos / 1_000_000_000.0;
        double durationMs = transferDurationNanos / 1_000_000.0;

        exp.setPacketsSent(packetsSent);
        exp.setPacketsReceived(packetsReceived);
        exp.setBytesSent(bytesSent);
        exp.setBytesReceived(bytesReceived);
        exp.setDurationMs(MetricsCalculator.round(durationMs, 2));

        SampleStatistics stats = MetricsCalculator.calculateStatistics(rttSamples);
        exp.setAvgRttMs(stats.getMean());
        exp.setMinRttMs(stats.getMin());
        exp.setMaxRttMs(stats.getMax());
        exp.setMedianRttMs(stats.getMedian());
        exp.setP95RttMs(stats.getP95());
        exp.setJitterMs(MetricsCalculator.calculateJitterMs(rttSamples));

        exp.setThroughputMbps(MetricsCalculator.calculateThroughputMbps(bytesReceived, durationSec));
        exp.setDataRateMBs(MetricsCalculator.calculateDataTransferRateMBs(bytesReceived, durationSec));
        exp.setPacketLossPct(MetricsCalculator.calculatePacketLossPct(packetsSent, packetsReceived));
        exp.setDeliveryRatioPct(MetricsCalculator.calculatePacketDeliveryRatioPct(packetsSent, packetsReceived));
        exp.setBandwidthUtilPct(MetricsCalculator.calculateBandwidthUtilizationPct(exp.getThroughputMbps(), config.getLinkCapacityMbps()));

        exp.setSamples(samples);
        if (exp.getStatus() == TestStatus.RUNNING) {
            exp.setStatus(TestStatus.COMPLETED);
        }
    }

    private void runBulkTransferTest(TestConfig config, UUID testUuid, DataOutputStream dos, DataInputStream dis,
                                     Experiment exp, TestProgressListener listener) throws IOException {
        int packetCount = config.getPacketCount();
        int payloadSize = config.getPayloadSize();
        byte[] payload = new byte[payloadSize];

        long totalBytesSent = 0;
        long packetsSent = 0;
        List<MeasurementSample> samples = new ArrayList<>();

        long transferStartNanos = System.nanoTime();
        long lastSampleNanos = transferStartNanos;
        long lastSampleBytes = 0;

        for (int i = 1; i <= packetCount; i++) {
            if (cancelled.get()) {
                exp.setStatus(TestStatus.CANCELLED);
                exp.setErrorMessage("Experiment cancelled during bulk transfer.");
                break;
            }

            NetworkPacket packet = NetworkPacket.create(MessageType.BULK_DATA, testUuid, i, System.nanoTime(), payload);
            packet.writeTo(dos);
            packetsSent++;
            totalBytesSent += payloadSize;

            long nowNanos = System.nanoTime();
            if ((nowNanos - lastSampleNanos) >= 100_000_000L || i == packetCount) { // sample every 100ms
                double intervalSec = (nowNanos - lastSampleNanos) / 1_000_000_000.0;
                long intervalBytes = totalBytesSent - lastSampleBytes;
                Double instantMbps = MetricsCalculator.calculateThroughputMbps(intervalBytes, intervalSec);

                MeasurementSample sample = new MeasurementSample(
                        exp.getId(),
                        i,
                        null,
                        instantMbps,
                        System.currentTimeMillis()
                );
                samples.add(sample);
                lastSampleNanos = nowNanos;
                lastSampleBytes = totalBytesSent;

                if (listener != null) {
                    double progress = (i * 100.0) / packetCount;
                    listener.onProgress(exp.getTestId(), i, packetCount, progress,
                            String.format("Transferred %d/%d packets (%.1f Mbps)", i, packetCount, instantMbps != null ? instantMbps : 0.0));
                    listener.onSample(exp.getTestId(), sample);
                }
            }
        }

        // Send TERMINATE packet to ask for BULK_ACK
        NetworkPacket term = NetworkPacket.create(MessageType.TERMINATE, testUuid, packetCount + 1, System.nanoTime(), new byte[0]);
        term.writeTo(dos);

        NetworkPacket ack = NetworkPacket.readFrom(dis);
        long transferEndNanos = System.nanoTime();
        double durationSec = (transferEndNanos - transferStartNanos) / 1_000_000_000.0;
        double durationMs = (transferEndNanos - transferStartNanos) / 1_000_000.0;

        long serverPacketsRecv = packetsSent;
        long serverBytesRecv = totalBytesSent;
        if (ack != null && ack.getPayloadLength() >= 16) {
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(ack.getPayload());
            serverPacketsRecv = buf.getLong();
            serverBytesRecv = buf.getLong();
        }

        exp.setPacketsSent(packetsSent);
        exp.setPacketsReceived(serverPacketsRecv);
        exp.setBytesSent(totalBytesSent);
        exp.setBytesReceived(serverBytesRecv);
        exp.setDurationMs(MetricsCalculator.round(durationMs, 2));

        exp.setThroughputMbps(MetricsCalculator.calculateThroughputMbps(serverBytesRecv, durationSec));
        exp.setDataRateMBs(MetricsCalculator.calculateDataTransferRateMBs(serverBytesRecv, durationSec));
        exp.setPacketLossPct(MetricsCalculator.calculatePacketLossPct(packetsSent, serverPacketsRecv));
        exp.setDeliveryRatioPct(MetricsCalculator.calculatePacketDeliveryRatioPct(packetsSent, serverPacketsRecv));
        exp.setBandwidthUtilPct(MetricsCalculator.calculateBandwidthUtilizationPct(exp.getThroughputMbps(), config.getLinkCapacityMbps()));

        exp.setSamples(samples);
        if (exp.getStatus() == TestStatus.RUNNING) {
            exp.setStatus(TestStatus.COMPLETED);
        }
    }

    private void runPacketDeliveryTest(TestConfig config, UUID testUuid, DataOutputStream dos, DataInputStream dis,
                                      Experiment exp, TestProgressListener listener) throws IOException {
        // TCP guarantees ordered reliable delivery
        runRttTest(config, testUuid, dos, dis, exp, listener);
    }

    private Experiment finishExperiment(Experiment exp) {
        exp.setEndTimeMs(System.currentTimeMillis());
        if (exp.getDurationMs() == null && exp.getStartTimeMs() != null) {
            exp.setDurationMs((double) (exp.getEndTimeMs() - exp.getStartTimeMs()));
        }
        return exp;
    }
}
