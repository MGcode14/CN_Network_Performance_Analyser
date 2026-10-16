package com.networkanalyzer;

import com.networkanalyzer.model.*;
import com.networkanalyzer.server.UdpServer;
import com.networkanalyzer.udp.UdpTestEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UdpTestEngineTest {
    private static UdpServer udpServer;
    private static final int TEST_PORT = 19092;

    @BeforeAll
    static void setUp() {
        udpServer = new UdpServer("127.0.0.1", TEST_PORT);
        udpServer.start();
    }

    @AfterAll
    static void tearDown() {
        if (udpServer != null) {
            udpServer.stop();
        }
    }

    @Test
    @DisplayName("Run real UDP RTT test against local receiver")
    void testUdpRtt() {
        TestConfig config = new TestConfig();
        config.setTargetHost("127.0.0.1");
        config.setUdpPort(TEST_PORT);
        config.setProtocol(Protocol.UDP);
        config.setTestMode(TestMode.RTT);
        config.setPayloadSize(128);
        config.setPacketCount(15);
        config.setWarmupCount(2);
        config.setTimeoutMs(3000);

        UdpTestEngine engine = new UdpTestEngine(udpServer);
        Experiment exp = engine.runTest(config, null);

        assertNotNull(exp);
        assertEquals(TestStatus.COMPLETED, exp.getStatus());
        assertEquals(15, exp.getPacketsSent());
        assertTrue(exp.getPacketsReceived() > 0);
        assertNotNull(exp.getAvgRttMs());
        assertNotNull(exp.getDeliveryRatioPct());
        assertFalse(exp.getSamples().isEmpty());
    }

    @Test
    @DisplayName("Run real UDP paced bulk transmission")
    void testUdpBulkTransmission() {
        TestConfig config = new TestConfig();
        config.setTargetHost("127.0.0.1");
        config.setUdpPort(TEST_PORT);
        config.setProtocol(Protocol.UDP);
        config.setTestMode(TestMode.BULK_TRANSFER);
        config.setPayloadSize(1024);
        config.setPacketCount(30);
        config.setSendingRatePps(1000); // 1000 pps
        config.setTimeoutMs(3000);

        UdpTestEngine engine = new UdpTestEngine(udpServer);
        Experiment exp = engine.runTest(config, null);

        assertNotNull(exp);
        assertEquals(TestStatus.COMPLETED, exp.getStatus());
        assertEquals(30, exp.getPacketsSent());
        assertTrue(exp.getPacketsReceived() > 0);
        assertNotNull(exp.getThroughputMbps());
    }
}
