package com.networkanalyzer.model;

public class ServerStatus {
    private boolean tcpRunning;
    private int tcpPort;
    private String tcpBindHost;
    private long tcpActiveConnections;
    private long tcpMessagesProcessed;

    private boolean udpRunning;
    private int udpPort;
    private String udpBindHost;
    private long udpPacketsReceived;
    private long udpPacketsSent;

    private int httpPort;
    private String httpBindHost;
    private long uptimeSeconds;

    public ServerStatus() {}

    public boolean isTcpRunning() { return tcpRunning; }
    public void setTcpRunning(boolean tcpRunning) { this.tcpRunning = tcpRunning; }

    public int getTcpPort() { return tcpPort; }
    public void setTcpPort(int tcpPort) { this.tcpPort = tcpPort; }

    public String getTcpBindHost() { return tcpBindHost; }
    public void setTcpBindHost(String tcpBindHost) { this.tcpBindHost = tcpBindHost; }

    public long getTcpActiveConnections() { return tcpActiveConnections; }
    public void setTcpActiveConnections(long tcpActiveConnections) { this.tcpActiveConnections = tcpActiveConnections; }

    public long getTcpMessagesProcessed() { return tcpMessagesProcessed; }
    public void setTcpMessagesProcessed(long tcpMessagesProcessed) { this.tcpMessagesProcessed = tcpMessagesProcessed; }

    public boolean isUdpRunning() { return udpRunning; }
    public void setUdpRunning(boolean udpRunning) { this.udpRunning = udpRunning; }

    public int getUdpPort() { return udpPort; }
    public void setUdpPort(int udpPort) { this.udpPort = udpPort; }

    public String getUdpBindHost() { return udpBindHost; }
    public void setUdpBindHost(String udpBindHost) { this.udpBindHost = udpBindHost; }

    public long getUdpPacketsReceived() { return udpPacketsReceived; }
    public void setUdpPacketsReceived(long udpPacketsReceived) { this.udpPacketsReceived = udpPacketsReceived; }

    public long getUdpPacketsSent() { return udpPacketsSent; }
    public void setUdpPacketsSent(long udpPacketsSent) { this.udpPacketsSent = udpPacketsSent; }

    public int getHttpPort() { return httpPort; }
    public void setHttpPort(int httpPort) { this.httpPort = httpPort; }

    public String getHttpBindHost() { return httpBindHost; }
    public void setHttpBindHost(String httpBindHost) { this.httpBindHost = httpBindHost; }

    public long getUptimeSeconds() { return uptimeSeconds; }
    public void setUptimeSeconds(long uptimeSeconds) { this.uptimeSeconds = uptimeSeconds; }
}
