package com.networkanalyzer.api;

import com.google.gson.Gson;
import com.networkanalyzer.config.AppConfig;
import com.networkanalyzer.exception.ValidationException;
import com.networkanalyzer.model.*;
import com.networkanalyzer.server.NetworkServerManager;
import com.networkanalyzer.service.ExperimentService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

public class HttpServerApp {
    private static final Logger logger = LoggerFactory.getLogger(HttpServerApp.class);

    private final AppConfig config;
    private final NetworkServerManager serverManager;
    private final ExperimentService experimentService;
    private final Gson gson = new Gson();

    private HttpServer httpServer;

    public HttpServerApp(AppConfig config, NetworkServerManager serverManager, ExperimentService experimentService) {
        this.config = config;
        this.serverManager = serverManager;
        this.experimentService = experimentService;
    }

    public void start() throws IOException {
        InetSocketAddress address = new InetSocketAddress(config.getHttpHost(), config.getHttpPort());
        httpServer = HttpServer.create(address, 0);
        httpServer.setExecutor(Executors.newFixedThreadPool(16));

        // Register API Routes
        httpServer.createContext("/api/health", new HealthHandler());
        httpServer.createContext("/api/server/status", new ServerStatusHandler());
        httpServer.createContext("/api/server/start", new ServerStartHandler());
        httpServer.createContext("/api/server/stop", new ServerStopHandler());
        httpServer.createContext("/api/tests", new TestsHandler());
        httpServer.createContext("/api/comparisons", new ComparisonsHandler());
        httpServer.createContext("/api/history", new HistoryHandler());
        httpServer.createContext("/api/metrics/summary", new MetricsSummaryHandler());
        httpServer.createContext("/api/diagnostics", new DiagnosticsHandler());

        // Static Web Dashboard files
        httpServer.createContext("/", new StaticFileHandler());

        httpServer.start();
        logger.info("HTTP Server running at http://{}:{}/", config.getHttpHost(), config.getHttpPort());
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(1);
            logger.info("HTTP Server stopped.");
        }
    }

    // --- Handlers ---

    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange);
                return;
            }
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("status", "UP");
            resp.put("timestamp", System.currentTimeMillis());
            resp.put("app", "Network Performance Analyzer");
            resp.put("version", "1.0.0");
            resp.put("jvmVersion", System.getProperty("java.version"));
            resp.put("availableProcessors", Runtime.getRuntime().availableProcessors());
            resp.put("freeMemoryBytes", Runtime.getRuntime().freeMemory());
            resp.put("totalMemoryBytes", Runtime.getRuntime().totalMemory());
            sendJsonResponse(exchange, 200, resp);
        }
    }

    private class ServerStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange);
                return;
            }
            ServerStatus status = serverManager.getStatus(config.getHttpPort(), config.getHttpHost());
            sendJsonResponse(exchange, 200, status);
        }
    }

    private class ServerStartHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange);
                return;
            }
            try {
                serverManager.startAll();
                ServerStatus status = serverManager.getStatus(config.getHttpPort(), config.getHttpHost());
                sendJsonResponse(exchange, 200, Map.of("message", "Receivers started successfully", "status", status));
            } catch (Exception e) {
                sendErrorResponse(exchange, 500, "Failed to start receivers: " + e.getMessage());
            }
        }
    }

    private class ServerStopHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange);
                return;
            }
            serverManager.stopAll();
            ServerStatus status = serverManager.getStatus(config.getHttpPort(), config.getHttpHost());
            sendJsonResponse(exchange, 200, Map.of("message", "Receivers stopped successfully", "status", status));
        }
    }

    private class TestsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            // POST /api/tests -> Start new test
            if (path.equals("/api/tests") && "POST".equalsIgnoreCase(method)) {
                String body = readRequestBody(exchange);
                try {
                    TestConfig testConfig = gson.fromJson(body, TestConfig.class);
                    if (testConfig == null) {
                        sendErrorResponse(exchange, 400, "Invalid JSON payload");
                        return;
                    }
                    Experiment exp = experimentService.startExperiment(testConfig);
                    sendJsonResponse(exchange, 202, exp);
                } catch (ValidationException ve) {
                    sendErrorResponse(exchange, 400, "Validation error: " + ve.getMessage());
                } catch (IllegalStateException ise) {
                    sendErrorResponse(exchange, 409, ise.getMessage());
                } catch (Exception e) {
                    logger.error("Failed to start experiment: {}", e.getMessage(), e);
                    sendErrorResponse(exchange, 500, "Internal error: " + e.getMessage());
                }
                return;
            }

            // GET /api/tests -> Get active test progress or recent list
            if (path.equals("/api/tests") && "GET".equalsIgnoreCase(method)) {
                Map<String, Object> active = experimentService.getActiveTestStatus();
                sendJsonResponse(exchange, 200, active);
                return;
            }

            // Subpaths: /api/tests/{id}, /api/tests/{id}/cancel, /api/tests/{id}/samples
            if (path.startsWith("/api/tests/")) {
                String sub = path.substring("/api/tests/".length());
                String[] parts = sub.split("/");
                String expId = parts[0];

                if (parts.length == 1 && "GET".equalsIgnoreCase(method)) {
                    Experiment exp = experimentService.getExperiment(expId);
                    if (exp != null) {
                        sendJsonResponse(exchange, 200, exp);
                    } else {
                        sendErrorResponse(exchange, 404, "Experiment not found: " + expId);
                    }
                    return;
                }

                if (parts.length == 2 && parts[1].equals("cancel") && "POST".equalsIgnoreCase(method)) {
                    boolean cancelled = experimentService.cancelExperiment(expId);
                    sendJsonResponse(exchange, 200, Map.of("cancelled", cancelled, "id", expId));
                    return;
                }

                if (parts.length == 2 && parts[1].equals("samples") && "GET".equalsIgnoreCase(method)) {
                    List<MeasurementSample> samples = experimentService.getSamples(expId, 1000);
                    sendJsonResponse(exchange, 200, samples);
                    return;
                }
            }

            sendErrorResponse(exchange, 404, "Endpoint not found");
        }
    }

    private class ComparisonsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange);
                return;
            }

            Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
            String tcpId = query.get("tcpId");
            String udpId = query.get("udpId");

            if (tcpId == null || udpId == null) {
                sendErrorResponse(exchange, 400, "Missing required query parameters 'tcpId' and 'udpId'");
                return;
            }

            ComparisonResult result = experimentService.compareExperiments(tcpId, udpId);
            sendJsonResponse(exchange, 200, result);
        }
    }

    private class HistoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange);
                return;
            }

            Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
            String protocol = query.getOrDefault("protocol", "ALL");
            String mode = query.getOrDefault("mode", "ALL");
            String search = query.getOrDefault("search", "");
            int limit = parseIntOrDefault(query.get("limit"), 50);
            int offset = parseIntOrDefault(query.get("offset"), 0);

            List<Experiment> history = experimentService.getHistory(protocol, mode, search, limit, offset);
            int total = experimentService.getHistoryCount(protocol, mode, search);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("total", total);
            resp.put("limit", limit);
            resp.put("offset", offset);
            resp.put("items", history);

            sendJsonResponse(exchange, 200, resp);
        }
    }

    private class MetricsSummaryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange);
                return;
            }
            Map<String, Object> summary = experimentService.getMetricsSummary();
            sendJsonResponse(exchange, 200, summary);
        }
    }

    private class DiagnosticsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange);
                return;
            }

            Map<String, Object> diag = new LinkedHashMap<>();
            diag.put("serverStatus", serverManager.getStatus(config.getHttpPort(), config.getHttpHost()));
            diag.put("activeTest", experimentService.getActiveTestStatus());
            diag.put("recentEvents", experimentService.getRecentEvents(20));
            diag.put("jvmFreeMemoryMB", Runtime.getRuntime().freeMemory() / (1024 * 1024));
            diag.put("jvmTotalMemoryMB", Runtime.getRuntime().totalMemory() / (1024 * 1024));
            diag.put("jvmMaxMemoryMB", Runtime.getRuntime().maxMemory() / (1024 * 1024));
            diag.put("activeThreads", Thread.activeCount());

            sendJsonResponse(exchange, 200, diag);
        }
    }

    // --- Static File Handler ---

    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            // Sanitize path against directory traversal
            path = path.replace("\\", "/");
            if (path.contains("..")) {
                sendErrorResponse(exchange, 403, "Forbidden");
                return;
            }

            String resourcePath = "static" + path;
            InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);

            // Also check on disk if running from target or during development
            if (is == null) {
                File localFile = new File("src/main/resources/" + resourcePath);
                if (localFile.exists() && localFile.isFile()) {
                    is = new FileInputStream(localFile);
                }
            }

            if (is == null) {
                sendErrorResponse(exchange, 404, "File not found: " + path);
                return;
            }

            String contentType = getContentType(path);
            byte[] bytes = is.readAllBytes();
            is.close();

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private String getContentType(String path) {
            if (path.endsWith(".html")) return "text/html; charset=UTF-8";
            if (path.endsWith(".css")) return "text/css; charset=UTF-8";
            if (path.endsWith(".js")) return "application/javascript; charset=UTF-8";
            if (path.endsWith(".json")) return "application/json; charset=UTF-8";
            if (path.endsWith(".svg")) return "image/svg+xml";
            if (path.endsWith(".png")) return "image/png";
            if (path.endsWith(".ico")) return "image/x-icon";
            return "application/octet-stream";
        }
    }

    // --- Utility Methods ---

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        String json = gson.toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("error", true);
        err.put("statusCode", statusCode);
        err.put("message", message);
        err.put("timestamp", System.currentTimeMillis());
        sendJsonResponse(exchange, statusCode, err);
    }

    private void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
        sendErrorResponse(exchange, 405, "Method Not Allowed");
    }

    private Map<String, String> parseQueryParams(String query) {
        if (query == null || query.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new HashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                map.put(key, value);
            }
        }
        return map;
    }

    private int parseIntOrDefault(String val, int defaultVal) {
        if (val == null) return defaultVal;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
