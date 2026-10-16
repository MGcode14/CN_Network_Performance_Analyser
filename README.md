# Network Performance Analyzer using TCP & UDP

A professional-grade, client-server network performance testing and empirical benchmarking laboratory built with Java 17, raw socket APIs, SQLite persistence, and an interactive modern Network Operations Center (NOC) dashboard.

![Protocol](https://img.shields.io/badge/Protocols-TCP%20%7C%20UDP-00e5ff)
![Java](https://img.shields.io/badge/Java-17%20LTS-3b82f6)
![Build](https://img.shields.io/badge/Build-Maven%20Wrapper-10b981)
![Tests](https://img.shields.io/badge/Tests-16%20Passed-10b981)
![Database](https://img.shields.io/badge/Storage-SQLite%20WAL-f59e0b)

---

## 1. Overview & Capabilities

The **Network Performance Analyzer** enables network engineers and students to conduct empirical, socket-based comparisons of **Transmission Control Protocol (TCP)** and **User Datagram Protocol (UDP)** under configurable network loads and traffic patterns.

Every measurement is collected from actual socket operations across the operating system's network stack. Never hardcoded, simulated, or randomized.

### Measured Metrics

1. **Round-Trip Time (RTT)**: Monotonic nanosecond-resolution request/response latency.
2. **Transfer Throughput**: Application payload transmission rate in decimal **Mbps** ($10^6$ bits/sec).
3. **Effective Data Transfer Rate**: Binary payload throughput in **MB/s** ($1024^2$ bytes/sec).
4. **Packet Loss (%)**: Unique lost packet percentage tracked via sequential numbering.
5. **Packet Delivery Ratio (%)**: Percentage of transmitted unique packets received.
6. **Jitter Estimate (ms)**: Mean absolute difference between consecutive RTT samples.
7. **Connection Setup Latency (ms)**: TCP 3-way handshake duration measured separately from transfer time.
8. **Bandwidth Utilization (%)**: Saturation percentage evaluated against configured link capacity.
9. **Statistical Summaries**: Min, Mean, Median (P50), 95th Percentile (P95), Max, and Standard Deviation.
10. **Head-to-Head Protocol Comparison**: Automatic delta difference calculation and technical analysis.

---

## 2. Technology Stack

- **Backend**: Java 17 LTS.
- **Networking**:
  - `java.net.Socket` and `java.net.ServerSocket` (TCP).
  - `java.net.DatagramSocket` and `java.net.DatagramPacket` (UDP).
- **Web & REST Server**: `com.sun.net.httpserver.HttpServer` (zero external web framework dependencies).
- **Persistence**: SQLite with JDBC (`org.xerial:sqlite-jdbc`) in Write-Ahead Logging (`WAL`) mode.
- **Serialization**: Google Gson (`com.google.code.gson`).
- **Logging**: SLF4J (`org.slf4j:slf4j-api`) with `slf4j-simple`.
- **Testing**: JUnit 5 Jupiter (`org.junit.jupiter:junit-jupiter`).
- **Frontend**: Responsive HTML5, Vanilla CSS3 (Dark NOC aesthetic), Vanilla JS (ES Modules).
- **Charts**: Interactive Chart.js (bundled locally for 100% offline operation).

---

## 3. Project Structure

```
CN_Network_Perform_Analyser/
├── pom.xml
├── mvnw.cmd
├── mvnw
├── README.md
├── .gitignore
├── .mvn/
│   └── wrapper/
│       └── maven-wrapper.properties
├── docs/
│   ├── ARCHITECTURE.md
│   ├── USER_GUIDE.md
│   ├── METRICS_AND_FORMULAS.md
│   ├── TESTING.md
│   ├── TROUBLESHOOTING.md
│   └── API_REFERENCE.md
├── scripts/
│   ├── start.bat
│   ├── stop.bat
│   └── run-tests.bat
├── data/
│   └── network_analyzer.db
├── logs/
│   └── app.log
└── src/
    ├── main/
    │   ├── java/com/networkanalyzer/
    │   │   ├── Application.java
    │   │   ├── analyzer/
    │   │   ├── api/
    │   │   ├── client/
    │   │   ├── config/
    │   │   ├── database/
    │   │   ├── exception/
    │   │   ├── metrics/
    │   │   ├── model/
    │   │   ├── protocol/
    │   │   ├── server/
    │   │   ├── service/
    │   │   ├── tcp/
    │   │   ├── udp/
    │   │   └── validation/
    │   └── resources/
    │       ├── application.properties
    │       └── static/
    │           ├── index.html
    │           ├── css/styles.css
    │           └── js/
    │               ├── app.js
    │               ├── api.js
    │               ├── charts.js
    │               ├── dashboard.js
    │               ├── history.js
    │               └── chart.min.js
    └── test/
        └── java/com/networkanalyzer/
```

---

## 4. Build, Test and Run Instructions (Windows)

### 4.1 Running Automated Tests

Execute the test suite using the included Maven Wrapper:

```cmd
.\mvnw.cmd test
```

Or use the convenience script:

```cmd
.\scripts\run-tests.bat
```

### 4.2 Building Standalone Package

```cmd
.\mvnw.cmd package
```

This produces an executable shaded fat JAR at `target/network-performance-analyzer-1.0.0.jar`.

### 4.3 Starting the Application

Double-click or run:

```cmd
.\scripts\start.bat
```

The startup script validates the Java 17 environment, builds the JAR if needed, starts the backend, and opens the local dashboard URL:

```
http://localhost:8080/
```

### 4.4 Stopping the Application

```cmd
.\scripts\stop.bat
```

Terminates the application processes cleanly without affecting unrelated Java processes.

---

## 5. Binary Protocol Framing

Both TCP streams and UDP datagrams use a documented 44-byte binary header:

- **Magic Bytes**: `0x4E, 0x50` ('NP' for Network Performance).
- **Version**: `0x01`.
- **Message Type**: 1 byte (`0x03` = RTT_REQUEST, `0x04` = RTT_RESPONSE, `0x05` = BULK_DATA, etc.).
- **Test ID**: 16-byte UUID.
- **Sequence Number**: 64-bit Big-Endian integer.
- **Sender Timestamp**: 64-bit nanosecond / epoch timestamp.
- **Payload Length**: 32-bit Big-Endian integer.
- **Checksum**: 32-bit CRC-32 for payload verification.
- **Payload**: Raw byte array of length `payloadLength`.

---

## 6. TCP vs UDP: Why Results Differ

1. **Connection Setup**: TCP requires a 3-way handshake (`SYN`, `SYN-ACK`, `ACK`), causing initial connection setup latency. UDP transmits immediately without state establishment.
2. **Reliability & Overhead**: TCP guarantees delivery and in-order stream reconstruction via acknowledgment packets and retransmissions. This introduces ACK traffic and head-of-line blocking under packet loss. UDP datagrams have zero acknowledgment overhead, achieving lower per-packet latency in favorable conditions, but will experience dropped packets if buffers saturate.
3. **Flow & Congestion Control**: TCP throttles throughput during slow-start and ramps up until window capacity is reached. UDP sends at the requested rate (up to network capacity), meaning unthrottled bursts can result in socket buffer overflows.
