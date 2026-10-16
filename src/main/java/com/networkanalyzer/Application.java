package com.networkanalyzer;

import com.networkanalyzer.api.HttpServerApp;
import com.networkanalyzer.config.AppConfig;
import com.networkanalyzer.database.DatabaseManager;
import com.networkanalyzer.server.NetworkServerManager;
import com.networkanalyzer.service.ExperimentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private static HttpServerApp httpApp;
    private static NetworkServerManager serverManager;
    private static ExperimentService experimentService;

    public static void main(String[] args) {
        printBanner();

        try {
            AppConfig config = new AppConfig();
            logger.info("Initializing Network Performance Analyzer v1.0.0...");

            // 1. Initialize SQLite Database
            DatabaseManager databaseManager = new DatabaseManager(config.getDatabaseUrl());
            databaseManager.logServerEvent("SYSTEM_START", "Network Performance Analyzer initialized", null);

            // 2. Initialize and start Network Receivers (TCP & UDP)
            serverManager = new NetworkServerManager(config.getBindHost(), config.getTcpPort(), config.getUdpPort());
            serverManager.startAll();

            // 3. Initialize Experiment Service
            experimentService = new ExperimentService(databaseManager, serverManager);

            // 4. Initialize and start HTTP Server / REST API & Dashboard
            httpApp = new HttpServerApp(config, serverManager, experimentService);
            httpApp.start();

            // 5. Register Graceful Shutdown Hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down Network Performance Analyzer...");
                if (httpApp != null) httpApp.stop();
                if (serverManager != null) serverManager.stopAll();
                if (experimentService != null) experimentService.shutdown();
                logger.info("Shutdown completed cleanly.");
            }, "shutdown-hook"));

            System.out.println("================================================================================");
            System.out.println("  Network Performance Analyzer is RUNNING!");
            System.out.println("  Dashboard URL : http://localhost:" + config.getHttpPort() + "/");
            System.out.println("  TCP Receiver  : " + config.getBindHost() + ":" + config.getTcpPort());
            System.out.println("  UDP Receiver  : " + config.getBindHost() + ":" + config.getUdpPort());
            System.out.println("  Database      : " + config.getDatabaseUrl());
            System.out.println("================================================================================");

        } catch (Exception e) {
            logger.error("Failed to start application: {}", e.getMessage(), e);
            System.err.println("FATAL: Application failed to start: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void printBanner() {
        System.out.println("""
            ================================================================================
             _   _      _                      _      ____             __     _
            | \\ | | ___| |___      _____  _ __| | __ |  _ \\ ___ _ __ / _|   / \\   _ __ ___
            |  \\| |/ _ \\ __\\ \\ /\\ / / _ \\| '__| |/ / | |_) / _ \\ '__| |_   / _ \\ | '_ ` _ \\
            | |\\  |  __/ |_ \\ V  V / (_) | |  |   <  |  __/  __/ |  |  _| / ___ \\| | | | | |
            |_| \\_|\\___|\\__| \\_/\\_/ \\___/|_|  |_|\\_\\ |_|   \\___|_|  |_|  /_/   \\_\\_| |_| |_|
                           NETWORK PERFORMANCE ANALYZER (TCP & UDP) v1.0.0
            ================================================================================
        """);
    }
}
