package com.networkanalyzer.server;

import com.networkanalyzer.model.ServerStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkServerManager {
    private static final Logger logger = LoggerFactory.getLogger(NetworkServerManager.class);

    private final String bindHost;
    private final int defaultTcpPort;
    private final int defaultUdpPort;

    private TcpServer tcpServer;
    private UdpServer udpServer;
    private final long startTimeMillis = System.currentTimeMillis();

    public NetworkServerManager(String bindHost, int defaultTcpPort, int defaultUdpPort) {
        this.bindHost = bindHost;
        this.defaultTcpPort = defaultTcpPort;
        this.defaultUdpPort = defaultUdpPort;
        this.tcpServer = new TcpServer(bindHost, defaultTcpPort);
        this.udpServer = new UdpServer(bindHost, defaultUdpPort);
    }

    public synchronized void startAll() {
        startTcp();
        startUdp();
    }

    public synchronized void stopAll() {
        stopTcp();
        stopUdp();
    }

    public synchronized void startTcp() {
        try {
            if (tcpServer == null || !tcpServer.isRunning()) {
                tcpServer = new TcpServer(bindHost, defaultTcpPort);
                tcpServer.start();
            }
        } catch (Exception e) {
            logger.error("Failed to start TCP server: {}", e.getMessage(), e);
            throw e;
        }
    }

    public synchronized void stopTcp() {
        if (tcpServer != null && tcpServer.isRunning()) {
            tcpServer.stop();
        }
    }

    public synchronized void startUdp() {
        try {
            if (udpServer == null || !udpServer.isRunning()) {
                udpServer = new UdpServer(bindHost, defaultUdpPort);
                udpServer.start();
            }
        } catch (Exception e) {
            logger.error("Failed to start UDP server: {}", e.getMessage(), e);
            throw e;
        }
    }

    public synchronized void stopUdp() {
        if (udpServer != null && udpServer.isRunning()) {
            udpServer.stop();
        }
    }

    public ServerStatus getStatus(int httpPort, String httpHost) {
        ServerStatus status = new ServerStatus();
        if (tcpServer != null) {
            status.setTcpRunning(tcpServer.isRunning());
            status.setTcpPort(tcpServer.getPort());
            status.setTcpBindHost(tcpServer.getBindHost());
            status.setTcpActiveConnections(tcpServer.getActiveConnections());
            status.setTcpMessagesProcessed(tcpServer.getMessagesProcessed());
        }
        if (udpServer != null) {
            status.setUdpRunning(udpServer.isRunning());
            status.setUdpPort(udpServer.getPort());
            status.setUdpBindHost(udpServer.getBindHost());
            status.setUdpPacketsReceived(udpServer.getPacketsReceived());
            status.setUdpPacketsSent(udpServer.getPacketsSent());
        }
        status.setHttpPort(httpPort);
        status.setHttpBindHost(httpHost);
        status.setUptimeSeconds((System.currentTimeMillis() - startTimeMillis) / 1000);
        return status;
    }

    public TcpServer getTcpServer() { return tcpServer; }
    public UdpServer getUdpServer() { return udpServer; }
}
