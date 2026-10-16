package com.networkanalyzer.model;

public class TestConfig {
    private String targetHost = "127.0.0.1";
    private int tcpPort = 9091;
    private int udpPort = 9092;
    private Protocol protocol = Protocol.TCP;
    private TestMode testMode = TestMode.RTT;
    private int payloadSize = 1024; // bytes
    private int packetCount = 100; // packets / requests
    private long totalBytes = 1024 * 100L;
    private int sendingRatePps = 0; // 0 = unthrottled
    private int warmupCount = 5;
    private int timeoutMs = 5000;
    private Double linkCapacityMbps = null; // optional link capacity
    private int repetitions = 1;
    private boolean alternatingOrder = false;

    public TestConfig() {}

    public String getTargetHost() { return targetHost; }
    public void setTargetHost(String targetHost) { this.targetHost = targetHost; }

    public int getTcpPort() { return tcpPort; }
    public void setTcpPort(int tcpPort) { this.tcpPort = tcpPort; }

    public int getUdpPort() { return udpPort; }
    public void setUdpPort(int udpPort) { this.udpPort = udpPort; }

    public Protocol getProtocol() { return protocol; }
    public void setProtocol(Protocol protocol) { this.protocol = protocol; }

    public TestMode getTestMode() { return testMode; }
    public void setTestMode(TestMode testMode) { this.testMode = testMode; }

    public int getPayloadSize() { return payloadSize; }
    public void setPayloadSize(int payloadSize) { this.payloadSize = payloadSize; }

    public int getPacketCount() { return packetCount; }
    public void setPacketCount(int packetCount) { this.packetCount = packetCount; }

    public long getTotalBytes() { return totalBytes; }
    public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }

    public int getSendingRatePps() { return sendingRatePps; }
    public void setSendingRatePps(int sendingRatePps) { this.sendingRatePps = sendingRatePps; }

    public int getWarmupCount() { return warmupCount; }
    public void setWarmupCount(int warmupCount) { this.warmupCount = warmupCount; }

    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }

    public Double getLinkCapacityMbps() { return linkCapacityMbps; }
    public void setLinkCapacityMbps(Double linkCapacityMbps) { this.linkCapacityMbps = linkCapacityMbps; }

    public int getRepetitions() { return repetitions; }
    public void setRepetitions(int repetitions) { this.repetitions = repetitions; }

    public boolean isAlternatingOrder() { return alternatingOrder; }
    public void setAlternatingOrder(boolean alternatingOrder) { this.alternatingOrder = alternatingOrder; }
}
