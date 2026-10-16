# Network Performance Analyzer — REST API Reference

The Network Performance Analyzer provides a complete RESTful API built on the standard JDK HTTP server.

Base URL: `http://localhost:8080`

---

## Endpoints

### 1. `GET /api/health`

Checks server health, uptime, memory, and JVM parameters.

- **Response**: `200 OK`

```json
{
  "status": "UP",
  "timestamp": 1791802500000,
  "app": "Network Performance Analyzer",
  "version": "1.0.0",
  "jvmVersion": "17",
  "availableProcessors": 8,
  "freeMemoryBytes": 134217728,
  "totalMemoryBytes": 268435456
}
```

### 2. `GET /api/server/status`

Retrieves network receiver states and statistics.

- **Response**: `200 OK`

```json
{
  "tcpRunning": true,
  "tcpPort": 9091,
  "tcpBindHost": "127.0.0.1",
  "tcpActiveConnections": 0,
  "tcpMessagesProcessed": 150,
  "udpRunning": true,
  "udpPort": 9092,
  "udpBindHost": "127.0.0.1",
  "udpPacketsReceived": 200,
  "udpPacketsSent": 100,
  "httpPort": 8080,
  "httpBindHost": "127.0.0.1",
  "uptimeSeconds": 120
}
```

### 3. `POST /api/server/start`

Starts TCP and UDP network receivers.

- **Response**: `200 OK`

### 4. `POST /api/server/stop`

Stops TCP and UDP network receivers.

- **Response**: `200 OK`

### 5. `POST /api/tests`

Queues and launches a new network performance test in the background executor.

- **Request Body**:

```json
{
  "targetHost": "127.0.0.1",
  "tcpPort": 9091,
  "udpPort": 9092,
  "protocol": "TCP",
  "testMode": "RTT",
  "payloadSize": 1024,
  "packetCount": 100,
  "sendingRatePps": 0,
  "warmupCount": 5,
  "timeoutMs": 5000,
  "linkCapacityMbps": 100.0,
  "alternatingOrder": false
}
```

- **Response**: `202 Accepted`

### 6. `GET /api/tests`

Returns active test status and progress if an experiment is executing.

- **Response**: `200 OK`

### 7. `GET /api/tests/{id}`

Retrieves a specific experiment's details, calculated metrics, and statistical distribution.

- **Response**: `200 OK`

### 8. `POST /api/tests/{id}/cancel`

Requests cancellation of a currently executing test.

- **Response**: `200 OK`

### 9. `GET /api/tests/{id}/samples`

Returns raw measurement time-series samples (RTT, instant throughput) for charting.

- **Response**: `200 OK`

### 10. `GET /api/comparisons?tcpId={id}&udpId={id}`

Computes side-by-side differences, delta percentages, and automated technical analysis between two compatible experiments.

- **Response**: `200 OK`

### 11. `GET /api/history?protocol=TCP&mode=RTT&search=127.0.0.1&limit=20&offset=0`

Returns paginated, filtered experiment history.

- **Response**: `200 OK`

### 12. `GET /api/metrics/summary`

Returns aggregate platform statistics (total tests, completed, overall average RTT, overall average throughput).

- **Response**: `200 OK`

### 13. `GET /api/diagnostics`

Retrieves JVM memory, thread metrics, and recent diagnostic event logs.

- **Response**: `200 OK`
