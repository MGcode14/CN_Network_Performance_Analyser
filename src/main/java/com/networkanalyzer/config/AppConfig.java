package com.networkanalyzer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;

public class AppConfig {
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);

    private int httpPort = 8080;
    private String httpHost = "127.0.0.1";
    private int tcpPort = 9091;
    private int udpPort = 9092;
    private String bindHost = "127.0.0.1";
    private String databaseUrl = "jdbc:sqlite:data/network_analyzer.db";

    public AppConfig() {
        loadProperties();
    }

    private void loadProperties() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                httpPort = Integer.parseInt(props.getProperty("server.http.port", "8080"));
                httpHost = props.getProperty("server.http.host", "127.0.0.1");
                tcpPort = Integer.parseInt(props.getProperty("receiver.tcp.port", "9091"));
                udpPort = Integer.parseInt(props.getProperty("receiver.udp.port", "9092"));
                bindHost = props.getProperty("receiver.bind.host", "127.0.0.1");
                databaseUrl = props.getProperty("database.url", "jdbc:sqlite:data/network_analyzer.db");
            }
        } catch (Exception e) {
            logger.warn("Could not load application.properties, using defaults: {}", e.getMessage());
        }
    }

    public int getHttpPort() { return httpPort; }
    public String getHttpHost() { return httpHost; }
    public int getTcpPort() { return tcpPort; }
    public int getUdpPort() { return udpPort; }
    public String getBindHost() { return bindHost; }
    public String getDatabaseUrl() { return databaseUrl; }
}
