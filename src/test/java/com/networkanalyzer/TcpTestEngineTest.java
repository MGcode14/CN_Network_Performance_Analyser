package com.networkanalyzer;

import com.networkanalyzer.model.*;
import com.networkanalyzer.server.TcpServer;
import com.networkanalyzer.tcp.TcpTestEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TcpTestEngineTest {
    private static TcpServer tcpServer;
    private static final int TEST_PORT = 19091;

    @BeforeAll
    static void setUp() {
        tcpServer = new TcpServer("127.0.0.1", TEST_PORT);
        tcpServer.start();
    }

    @AfterAll
    static void tearDown() {
        if (tcpServer != null) {
            tcpServer.stop();
        }
    }

    @Test
    @DisplayName("Run real TCP RTT test against local receiver")
    void testTcpRtt() {
        TestConfig config = new TestConfig();
        config.setTargetHost("127.0.0.1");
        config.setTcpPort(TEST_PORT);
        config.setProtocol(Protocol.TCP);
        config.setTestMode(TestMode.RTT);
        config.setPayloadSize(128);
        config.setPacketCount(10);
        config.setWarmupCount(2);
        config.setTimeoutMs(3000);

        TcpTestEngine engine = new TcpTestEngine();
        Experiment exp = engine.runTest(config, null);

        assertNotNull(exp);
        assertEquals(TestStatus.COMPLETED, exp.getStatus());
        assertEquals(10, exp.getPacketsSent());
        assertEquals(10, exp.getPacketsReceived());
        assertEquals(0.0, exp.getPacketLossPct());
        assertNotNull(exp.getAvgRttMs());
        assertTrue(exp.getAvgRttMs() >= 0.0);
        assertNotNull(exp.getConnectionSetupTimeMs());
        assertTrue(exp.getConnectionSetupTimeMs() >= 0.0);
        assertNotNull(exp.getThroughputMbps());
        assertTrue(exp.getThroughputMbps() > 0.0);
        assertFalse(exp.getSamples().isEmpty());
    }

    @Test
    @DisplayName("Run real TCP bulk transfer test")
    void testTcpBulkTransfer() {
        TestConfig config = new TestConfig();
        config.setTargetHost("127.0.0.1");
        config.setTcpPort(TEST_PORT);
        config.setProtocol(Protocol.TCP);
        config.setTestMode(TestMode.BULK_TRANSFER);
        config.setPayloadSize(4096);
        config.setPacketCount(25);
        config.setWarmupCount(0);
        config.setTimeoutMs(3000);

        TcpTestEngine engine = new TcpTestEngine();
        Experiment exp = engine.runTest(config, null);

        assertNotNull(exp);
        assertEquals(TestStatus.COMPLETED, exp.getStatus());
        assertEquals(25, exp.getPacketsSent());
        assertEquals(25 * 4096, exp.getBytesSent());
        assertEquals(25 * 4096, exp.getBytesReceived());
        assertNotNull(exp.getThroughputMbps());
        assertTrue(exp.getThroughputMbps() > 0.0);
    }
}
