package com.networkanalyzer.udp;

import com.networkanalyzer.client.TestProgressListener;
import com.networkanalyzer.exception.NetworkException;
import com.networkanalyzer.metrics.MetricsCalculator;
import com.networkanalyzer.metrics.SampleStatistics;
import com.networkanalyzer.model.*;
import com.networkanalyzer.protocol.MessageType;
import com.networkanalyzer.protocol.NetworkPacket;
import com.networkanalyzer.server.UdpServer;
import com.networkanalyzer.server.UdpTestStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public class UdpTestEngine {
    private static final Logger logger = LoggerFactory.getLogger(UdpTestEngine.class);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final UdpServer localUdpServer; // optional reference for local receiver fallback

    public UdpTestEngine() {
        this.localUdpServer = null;
    }

    public UdpTestEngine(UdpServer localUdpServer) {
        this.localUdpServer = localUdpServer;
    }

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
        exp.setName("UDP " + config.getTestMode() + " (" + config.getPayloadSize() + "B)");
        exp.setProtocol(Protocol.UDP);
        exp.setTestMode(config.getTestMode());
        exp.setStatus(TestStatus.RUNNING);
        exp.setConfig(config);
        exp.setTargetHost(config.getTargetHost());
        exp.setTargetPort(config.getUdpPort());
        exp.setPayloadSize(config.getPayloadSize());
        exp.setLinkCapacityMbps(config.getLinkCapacityMbps());

        long startEpoch = System.currentTimeMillis();
        exp.setStartTimeMs(startEpoch);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(config.getTimeoutMs());
            socket.setSendBufferSize(2 * 1024 * 1024);
            socket.setReceiveBufferSize(2 * 1024 * 1024);

            InetAddress targetAddr = InetAddress.getByName(config.getTargetHost());
            int targetPort = config.getUdpPort();

            // Warm-up phase if configured
            int warmup = config.getWarmupCount();
            if (warmup > 0 && !cancelled.get()) {
                if (listener != null) {
                    listener.onProgress(testIdStr, 0, config.getPacketCount(), 0.0, "Performing UDP warm-up (" + warmup + " packets)...");
                }
                byte[] warmupPayload = new byte[Math.min(config.getPayloadSize(), 512)];
                for (int w = 0; w < warmup; w++) {
                    if (cancelled.get()) break;
                    NetworkPacket req = NetworkPacket.create(MessageType.RTT_REQUEST, testUuid, -1L - w, System.nanoTime(), warmupPayload);
                    byte[] data = req.toByteArray();
                    socket.send(new DatagramPacket(data, data.length, targetAddr, targetPort));
                    try {
                        byte[] recvBuf = new byte[1024];
                        DatagramPacket resp = new DatagramPacket(recvBuf, recvBuf.length);
                        socket.receive(resp);
                    } catch (SocketTimeoutException ignored) {}
                }
            }

            if (cancelled.get()) {
                exp.setStatus(TestStatus.CANCELLED);
                exp.setErrorMessage("Experiment cancelled by user.");
                return finishExperiment(exp);
            }

            if (config.getTestMode() == TestMode.RTT) {
                runRttTest(config, testUuid, targetAddr, targetPort, socket, exp, listener);
            } else {
                // BULK_TRANSFER or PACKET_DELIVERY
                runPacedBulkOrDeliveryTest(config, testUuid, targetAddr, targetPort, socket, exp, listener);
            }

        } catch (Exception e) {
            logger.error("UDP experiment error: {}", e.getMessage(), e);
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

    private void runRttTest(TestConfig config, UUID testUuid, InetAddress targetAddr, int targetPort,
                            DatagramSocket socket, Experiment exp, TestProgressListener listener) throws IOException {
        int count = config.getPacketCount();
        byte[] payload = new byte[config.getPayloadSize()];
        List<Double> rttSamples = new ArrayList<>(count);
        List<MeasurementSample> samples = new ArrayList<>(count);

        long packetsSent = 0;
        long packetsReceived = 0;
        long bytesSent = 0;
        long bytesReceived = 0;

        byte[] recvBuf = new byte[65535];
        long transferStartNanos = System.nanoTime();

        for (int i = 1; i <= count; i++) {
            if (cancelled.get()) {
                exp.setStatus(TestStatus.CANCELLED);
                exp.setErrorMessage("Experiment cancelled during UDP RTT test.");
                break;
            }

            long reqSendNanos = System.nanoTime();
            NetworkPacket req = NetworkPacket.create(MessageType.RTT_REQUEST, testUuid, i, reqSendNanos, payload);
            byte[] sendData = req.toByteArray();
            DatagramPacket sendPkt = new DatagramPacket(sendData, sendData.length, targetAddr, targetPort);
            socket.send(sendPkt);
            packetsSent++;
            bytesSent += config.getPayloadSize();

            try {
                DatagramPacket recvPkt = new DatagramPacket(recvBuf, recvBuf.length);
                socket.receive(recvPkt);
                long respRecvNanos = System.nanoTime();

                NetworkPacket resp = NetworkPacket.fromByteArray(recvPkt.getData(), recvPkt.getOffset(), recvPkt.getLength());
                if (resp.getMessageType() == MessageType.RTT_RESPONSE && resp.getSequenceNumber() == i) {
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
                        listener.onProgress(exp.getTestId(), i, count, progress, String.format("Sample %d/%d UDP RTT: %.2f ms", i, count, sampleRttMs));
                        listener.onSample(exp.getTestId(), sample);
                    }
                }
            } catch (SocketTimeoutException ste) {
                // Packet dropped or delayed beyond timeout
                logger.debug("UDP RTT response timed out for seq {}", i);
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

    private void runPacedBulkOrDeliveryTest(TestConfig config, UUID testUuid, InetAddress targetAddr, int targetPort,
                                           DatagramSocket socket, Experiment exp, TestProgressListener listener) throws IOException {
        int packetCount = config.getPacketCount();
        int payloadSize = config.getPayloadSize();
        byte[] payload = new byte[payloadSize];

        long totalBytesSent = 0;
        long packetsSent = 0;
        List<MeasurementSample> samples = new ArrayList<>();

        long pacingDelayNanos = 0;
        if (config.getSendingRatePps() > 0) {
            pacingDelayNanos = 1_000_000_000L / config.getSendingRatePps();
        }

        long transferStartNanos = System.nanoTime();
        long lastSampleNanos = transferStartNanos;
        long lastSampleBytes = 0;

        for (int i = 1; i <= packetCount; i++) {
            if (cancelled.get()) {
                exp.setStatus(TestStatus.CANCELLED);
                exp.setErrorMessage("Experiment cancelled during UDP bulk transfer.");
                break;
            }

            long sendNanos = System.nanoTime();
            NetworkPacket packet = NetworkPacket.create(MessageType.BULK_DATA, testUuid, i, sendNanos, payload);
            byte[] data = packet.toByteArray();
            DatagramPacket datagram = new DatagramPacket(data, data.length, targetAddr, targetPort);
            socket.send(datagram);
            packetsSent++;
            totalBytesSent += payloadSize;

            if (pacingDelayNanos > 0) {
                LockSupport.parkNanos(pacingDelayNanos);
            }

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
                            String.format("Sent UDP %d/%d packets (%.1f Mbps)", i, packetCount, instantMbps != null ? instantMbps : 0.0));
                    listener.onSample(exp.getTestId(), sample);
                }
            }
        }

        long transferEndNanos = System.nanoTime();
        double durationSec = (transferEndNanos - transferStartNanos) / 1_000_000_000.0;
        double durationMs = (transferEndNanos - transferStartNanos) / 1_000_000.0;

        // Give receiver 500ms grace period to finish queuing incoming datagrams
        try {
            Thread.sleep(300);
        } catch (InterruptedException ignored) {}

        // Query receiver for actual received unique packets and statistics
        long serverPacketsRecv = 0;
        long serverBytesRecv = 0;
        boolean statsObtained = false;

        // Try querying receiver via STATUS_QUERY datagram
        try {
            NetworkPacket query = NetworkPacket.create(MessageType.STATUS_QUERY, testUuid, packetCount + 1, System.nanoTime(), new byte[0]);
            byte[] queryBytes = query.toByteArray();
            socket.send(new DatagramPacket(queryBytes, queryBytes.length, targetAddr, targetPort));

            byte[] replyBuf = new byte[1024];
            DatagramPacket replyPkt = new DatagramPacket(replyBuf, replyBuf.length);
            socket.receive(replyPkt);

            NetworkPacket reply = NetworkPacket.fromByteArray(replyPkt.getData(), replyPkt.getOffset(), replyPkt.getLength());
            if (reply.getMessageType() == MessageType.STATUS_REPLY && reply.getPayloadLength() >= 16) {
                ByteBuffer buf = ByteBuffer.wrap(reply.getPayload());
                long totalRecv = buf.getLong();
                long uniqueRecv = buf.getLong();
                serverPacketsRecv = uniqueRecv;
                serverBytesRecv = uniqueRecv * payloadSize;
                statsObtained = true;
                logger.info("UDP Receiver reported: totalRecv={}, uniqueRecv={}", totalRecv, uniqueRecv);
            }
        } catch (Exception e) {
            logger.debug("Could not obtain UDP receiver stats via datagram query: {}", e.getMessage());
        }

        // Fallback: Check local receiver instance if available
        if (!statsObtained && localUdpServer != null) {
            UdpTestStats stats = localUdpServer.getStats(testUuid.toString());
            if (stats != null) {
                serverPacketsRecv = stats.getUniquePacketsReceived();
                serverBytesRecv = stats.getTotalBytesReceived();
                statsObtained = true;
                logger.info("Retrieved UDP stats directly from local receiver: unique={}", serverPacketsRecv);
            }
        }

        if (!statsObtained) {
            // Target receiver did not respond to status query
            logger.warn("Could not obtain UDP receiver stats. Marking server received as 0.");
            serverPacketsRecv = 0;
            serverBytesRecv = 0;
        }

        exp.setPacketsSent(packetsSent);
        exp.setPacketsReceived(serverPacketsRecv);
        exp.setBytesSent(totalBytesSent);
        exp.setBytesReceived(serverBytesRecv);
        exp.setDurationMs(MetricsCalculator.round(durationMs, 2));

        // Throughput based on successfully received payload bytes
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

    private Experiment finishExperiment(Experiment exp) {
        exp.setEndTimeMs(System.currentTimeMillis());
        if (exp.getDurationMs() == null && exp.getStartTimeMs() != null) {
            exp.setDurationMs((double) (exp.getEndTimeMs() - exp.getStartTimeMs()));
        }
        return exp;
    }
}
