# Network Performance Analyzer — Testing & Verification Guide

This document outlines the automated test suite, verification procedures, and actual test results for the Network Performance Analyzer.

---

## 1. Automated Test Suite

The test suite runs with JUnit 5 and Surefire via the Maven Wrapper:

```cmd
.\mvnw.cmd test
```

### Test Classes & Coverage

1. **`MetricsCalculatorTest`** (`com.networkanalyzer.metrics`):
   - Monotonic nanosecond RTT calculation.
   - Decimal megabit throughput calculation ($B \times 8 / \text{sec} / 10^6$).
   - Binary megabyte transfer rate ($B / 1024^2 / \text{sec}$).
   - Unique packet loss percentage and edge cases (0 sent, 0 received, 100% loss).
   - Packet delivery ratio.
   - Consecutive RTT absolute difference Jitter estimate.
   - Bandwidth capacity utilization calculation.
   - Percentile calculation (Min, Mean, Median, P95, Max, StdDev).
   - Empty sample sets and zero-duration safety handling.

2. **`NetworkPacketTest`** (`com.networkanalyzer.protocol`):
   - TCP binary framing stream read/write.
   - UDP datagram serialization/deserialization.
   - CRC-32 checksum integrity verification.
   - UUID test identifier packing.

3. **`TcpTestEngineTest`** (`com.networkanalyzer`):
   - Starts dedicated TCP receiver socket on port 19091.
   - Measures connection setup latency.
   - Executes real sequential RTT request/response exchanges.
   - Executes real bulk data transfer and verifies byte counts and throughput.

4. **`UdpTestEngineTest`** (`com.networkanalyzer`):
   - Starts dedicated UDP receiver socket on port 19092.
   - Executes real UDP request/response RTT datagram exchanges.
   - Executes paced bulk UDP datagram transmission.
   - Verifies sequence accounting and packet delivery ratio.

5. **`DatabaseManagerTest`** (`com.networkanalyzer`):
   - SQLite initialization and WAL mode activation.
   - Experiment insertion, update, and retrieval.
   - Measurement samples batch insertion.
   - Filtered querying and pagination.

6. **`HttpApiTest`** (`com.networkanalyzer`):
   - Starts JDK HTTP server on port 18080.
   - Verifies `GET /api/health` returns status UP.
   - Verifies `GET /api/server/status` returns receiver states.

---

## 2. Actual Automated Test Execution Output

```text
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.networkanalyzer.DatabaseManagerTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.networkanalyzer.HttpApiTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.networkanalyzer.metrics.MetricsCalculatorTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.networkanalyzer.protocol.NetworkPacketTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.networkanalyzer.TcpTestEngineTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.networkanalyzer.UdpTestEngineTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

---

## 3. End-to-End Verification Checklist

| Test Item | Protocol / Target | Expected Outcome | Result |
| :--- | :--- | :--- | :--- |
| **TCP RTT Measurement** | TCP 127.0.0.1:9091 | Real RTT samples, Jitter computed, 0% loss | **PASS** |
| **TCP Bulk Transfer** | TCP 127.0.0.1:9091 | Bulk bytes transferred, Mbps calculated | **PASS** |
| **UDP RTT Measurement** | UDP 127.0.0.1:9092 | Real datagram RTT, Jitter computed | **PASS** |
| **UDP Datagram Stream** | UDP 127.0.0.1:9092 | Paced datagrams, sequence tracking verified | **PASS** |
| **Protocol Comparison** | BOTH 127.0.0.1 | Side-by-side delta, automated analysis | **PASS** |
| **Database Persistence** | SQLite | Survives restarts, samples retained | **PASS** |
| **Input Validation** | Invalid Port / Host | Immediate error, test rejected | **PASS** |
| **Cancellation** | Active Test | Safely halted, marked CANCELLED | **PASS** |
