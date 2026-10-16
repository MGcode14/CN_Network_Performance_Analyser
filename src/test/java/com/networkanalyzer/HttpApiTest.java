package com.networkanalyzer;

import com.networkanalyzer.api.HttpServerApp;
import com.networkanalyzer.config.AppConfig;
import com.networkanalyzer.database.DatabaseManager;
import com.networkanalyzer.server.NetworkServerManager;
import com.networkanalyzer.service.ExperimentService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

public class HttpApiTest {
    private static HttpServerApp httpApp;
    private static NetworkServerManager serverManager;
    private static DatabaseManager dbManager;
    private static ExperimentService expService;
    private static final String TEST_DB = "data/test_http_api.db";
    private static final int HTTP_PORT = 18080;

    @BeforeAll
    static void setUp() throws Exception {
        AppConfig config = new AppConfig();
        // Override config ports for test
        java.lang.reflect.Field portField = AppConfig.class.getDeclaredField("httpPort");
        portField.setAccessible(true);
        portField.setInt(config, HTTP_PORT);

        dbManager = new DatabaseManager("jdbc:sqlite:" + TEST_DB);
        serverManager = new NetworkServerManager("127.0.0.1", 19095, 19096);
        serverManager.startAll();
        expService = new ExperimentService(dbManager, serverManager);

        httpApp = new HttpServerApp(config, serverManager, expService);
        httpApp.start();
    }

    @AfterAll
    static void tearDown() {
        if (httpApp != null) httpApp.stop();
        if (serverManager != null) serverManager.stopAll();
        if (expService != null) expService.shutdown();
        new File(TEST_DB).delete();
    }

    @Test
    @DisplayName("Verify /api/health endpoint returns UP")
    void testHealthEndpoint() throws Exception {
        URL url = new URL("http://127.0.0.1:" + HTTP_PORT + "/api/health");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        assertEquals(200, conn.getResponseCode());

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line = reader.readLine();
        assertTrue(line.contains("\"status\":\"UP\""));
    }

    @Test
    @DisplayName("Verify /api/server/status returns receiver states")
    void testServerStatusEndpoint() throws Exception {
        URL url = new URL("http://127.0.0.1:" + HTTP_PORT + "/api/server/status");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        assertEquals(200, conn.getResponseCode());

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line = reader.readLine();
        assertTrue(line.contains("\"tcpRunning\":true"));
        assertTrue(line.contains("\"udpRunning\":true"));
    }
}
