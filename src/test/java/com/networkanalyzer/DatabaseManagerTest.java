package com.networkanalyzer;

import com.networkanalyzer.database.DatabaseManager;
import com.networkanalyzer.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseManagerTest {
    private String testDbPath;
    private DatabaseManager dbManager;

    @BeforeEach
    void setUp() {
        testDbPath = "data/test_db_" + System.currentTimeMillis() + ".db";
        dbManager = new DatabaseManager("jdbc:sqlite:" + testDbPath);
    }

    @AfterEach
    void tearDown() {
        File file = new File(testDbPath);
        if (file.exists()) {
            file.delete();
        }
    }

    @Test
    @DisplayName("Persist experiment and retrieve it with samples")
    void testSaveAndGetExperiment() {
        Experiment exp = new Experiment();
        String id = UUID.randomUUID().toString();
        exp.setId(id);
        exp.setTestId(UUID.randomUUID().toString());
        exp.setName("TCP Test Run");
        exp.setProtocol(Protocol.TCP);
        exp.setTestMode(TestMode.RTT);
        exp.setStatus(TestStatus.COMPLETED);
        exp.setTargetHost("127.0.0.1");
        exp.setTargetPort(9091);
        exp.setPayloadSize(1024);
        exp.setThroughputMbps(85.5);
        exp.setAvgRttMs(2.15);
        exp.setPacketsSent(100L);
        exp.setPacketsReceived(100L);
        exp.setPacketLossPct(0.0);
        exp.setDeliveryRatioPct(100.0);

        MeasurementSample sample = new MeasurementSample(id, 1L, 2.15, 85.5, System.currentTimeMillis());
        exp.setSamples(Collections.singletonList(sample));

        dbManager.saveExperiment(exp);

        Experiment retrieved = dbManager.getExperiment(id);
        assertNotNull(retrieved);
        assertEquals("TCP Test Run", retrieved.getName());
        assertEquals(85.5, retrieved.getThroughputMbps());
        assertEquals(2.15, retrieved.getAvgRttMs());
        assertEquals(1, retrieved.getSamples().size());

        List<Experiment> list = dbManager.getAllExperiments("TCP", null, null, 10, 0);
        assertEquals(1, list.size());
        assertEquals(1, dbManager.countExperiments("TCP", null, null));
    }
}
