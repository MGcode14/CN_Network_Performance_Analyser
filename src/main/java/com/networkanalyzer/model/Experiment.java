package com.networkanalyzer.model;

import java.util.ArrayList;
import java.util.List;

public class Experiment {
    private String id;
    private String testId;
    private String name;
    private Protocol protocol;
    private TestMode testMode;
    private TestStatus status = TestStatus.PENDING;
    private TestConfig config;

    private String targetHost;
    private int targetPort;
    private int payloadSize;

    private Double connectionSetupTimeMs;
    private Long startTimeMs;
    private Long endTimeMs;
    private Double durationMs;

    private Long packetsSent;
    private Long packetsReceived;
    private Long bytesSent;
    private Long bytesReceived;

    private Double throughputMbps;
    private Double dataRateMBs;

    private Double avgRttMs;
    private Double minRttMs;
    private Double maxRttMs;
    private Double medianRttMs;
    private Double p95RttMs;
    private Double jitterMs;

    private Double packetLossPct;
    private Double deliveryRatioPct;
    private Double bandwidthUtilPct;
    private Double linkCapacityMbps;

    private String errorMessage;
    private long createdAt = System.currentTimeMillis();

    private List<MeasurementSample> samples = new ArrayList<>();

    public Experiment() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTestId() { return testId; }
    public void setTestId(String testId) { this.testId = testId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Protocol getProtocol() { return protocol; }
    public void setProtocol(Protocol protocol) { this.protocol = protocol; }

    public TestMode getTestMode() { return testMode; }
    public void setTestMode(TestMode testMode) { this.testMode = testMode; }

    public TestStatus getStatus() { return status; }
    public void setStatus(TestStatus status) { this.status = status; }

    public TestConfig getConfig() { return config; }
    public void setConfig(TestConfig config) { this.config = config; }

    public String getTargetHost() { return targetHost; }
    public void setTargetHost(String targetHost) { this.targetHost = targetHost; }

    public int getTargetPort() { return targetPort; }
    public void setTargetPort(int targetPort) { this.targetPort = targetPort; }

    public int getPayloadSize() { return payloadSize; }
    public void setPayloadSize(int payloadSize) { this.payloadSize = payloadSize; }

    public Double getConnectionSetupTimeMs() { return connectionSetupTimeMs; }
    public void setConnectionSetupTimeMs(Double connectionSetupTimeMs) { this.connectionSetupTimeMs = connectionSetupTimeMs; }

    public Long getStartTimeMs() { return startTimeMs; }
    public void setStartTimeMs(Long startTimeMs) { this.startTimeMs = startTimeMs; }

    public Long getEndTimeMs() { return endTimeMs; }
    public void setEndTimeMs(Long endTimeMs) { this.endTimeMs = endTimeMs; }

    public Double getDurationMs() { return durationMs; }
    public void setDurationMs(Double durationMs) { this.durationMs = durationMs; }

    public Long getPacketsSent() { return packetsSent; }
    public void setPacketsSent(Long packetsSent) { this.packetsSent = packetsSent; }

    public Long getPacketsReceived() { return packetsReceived; }
    public void setPacketsReceived(Long packetsReceived) { this.packetsReceived = packetsReceived; }

    public Long getBytesSent() { return bytesSent; }
    public void setBytesSent(Long bytesSent) { this.bytesSent = bytesSent; }

    public Long getBytesReceived() { return bytesReceived; }
    public void setBytesReceived(Long bytesReceived) { this.bytesReceived = bytesReceived; }

    public Double getThroughputMbps() { return throughputMbps; }
    public void setThroughputMbps(Double throughputMbps) { this.throughputMbps = throughputMbps; }

    public Double getDataRateMBs() { return dataRateMBs; }
    public void setDataRateMBs(Double dataRateMBs) { this.dataRateMBs = dataRateMBs; }

    public Double getAvgRttMs() { return avgRttMs; }
    public void setAvgRttMs(Double avgRttMs) { this.avgRttMs = avgRttMs; }

    public Double getMinRttMs() { return minRttMs; }
    public void setMinRttMs(Double minRttMs) { this.minRttMs = minRttMs; }

    public Double getMaxRttMs() { return maxRttMs; }
    public void setMaxRttMs(Double maxRttMs) { this.maxRttMs = maxRttMs; }

    public Double getMedianRttMs() { return medianRttMs; }
    public void setMedianRttMs(Double medianRttMs) { this.medianRttMs = medianRttMs; }

    public Double getP95RttMs() { return p95RttMs; }
    public void setP95RttMs(Double p95RttMs) { this.p95RttMs = p95RttMs; }

    public Double getJitterMs() { return jitterMs; }
    public void setJitterMs(Double jitterMs) { this.jitterMs = jitterMs; }

    public Double getPacketLossPct() { return packetLossPct; }
    public void setPacketLossPct(Double packetLossPct) { this.packetLossPct = packetLossPct; }

    public Double getDeliveryRatioPct() { return deliveryRatioPct; }
    public void setDeliveryRatioPct(Double deliveryRatioPct) { this.deliveryRatioPct = deliveryRatioPct; }

    public Double getBandwidthUtilPct() { return bandwidthUtilPct; }
    public void setBandwidthUtilPct(Double bandwidthUtilPct) { this.bandwidthUtilPct = bandwidthUtilPct; }

    public Double getLinkCapacityMbps() { return linkCapacityMbps; }
    public void setLinkCapacityMbps(Double linkCapacityMbps) { this.linkCapacityMbps = linkCapacityMbps; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public List<MeasurementSample> getSamples() { return samples; }
    public void setSamples(List<MeasurementSample> samples) { this.samples = samples; }
}
