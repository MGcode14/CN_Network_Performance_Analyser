# Network Performance Analyzer — Troubleshooting Guide

This guide covers common Windows and networking issues, error diagnostics, and resolutions.

---

## 1. Port Already in Use (BindException)

**Symptom**:
`Failed to bind TCP Receiver on 127.0.0.1:9091: Address already in use` or `HttpServer bind error on :8080`.

**Cause**:
A previous instance of the application or another local service (e.g., IIS, Tomcat, another network tool) is currently bound to port 8080, 9091, or 9092.

**Resolution**:

1. Run the stop script:

   ```cmd
   .\scripts\stop.bat
   ```

2. Find the process holding the port using PowerShell:

   ```powershell
   Get-NetTCPConnection -LocalPort 8080, 9091, 9092 | Select-Object LocalPort, OwningProcess
   ```

3. Terminate the process by ID:

   ```powershell
   Stop-Process -Id <OwningProcessId> -Force
   ```

4. Alternatively, adjust ports in `src/main/resources/application.properties`:

   ```properties
   server.http.port=8085
   receiver.tcp.port=9095
   receiver.udp.port=9096
   ```

---

## 2. TCP Connection Refused

**Symptom**:
`Connection refused: connect` when running a test.

**Cause**:

1. The TCP receiver server has stopped or failed to bind.
2. The target host or port specified in the configuration form is incorrect.

**Resolution**:

- Look at the header status pill: confirm that `TCP :9091 [ONLINE]` is green.
- If offline, click **Start Receivers** in the header.
- Ensure the **TCP Port** in the configuration form matches the receiver's port (`9091`).

---

## 3. UDP Packet Loss / Timeouts

**Symptom**:
High packet loss (>90%) or socket timeouts on UDP tests.

**Cause**:

1. **Unthrottled UDP Flood**: Sending hundreds of thousands of datagrams with 0 PPS delay can overflow OS socket buffers (`SO_RCVBUF`).
2. **Windows Firewall**: If testing across remote lab machines, Windows Defender Firewall may drop incoming UDP packets on port 9092.

**Resolution**:

- Set **Sending Rate (PPS)** to a paced rate such as `500` or `1000` PPS in the test configuration.
- For remote machine testing, ensure a firewall rule allows inbound UDP traffic on port 9092:

  ```powershell
  # (Run as Administrator if needed on lab machines)
  New-NetFirewallRule -DisplayName "Network Analyzer UDP" -Direction Inbound -LocalPort 9092 -Protocol UDP -Action Allow
  ```

---

## 4. SQLite Database Locked

**Symptom**:
`[SQLITE_BUSY] The database file is locked`.

**Cause**:
Multiple threads attempting exclusive write operations or an external database browser (such as DB Browser for SQLite) holding an exclusive write lock.

**Resolution**:

- The analyzer enables Write-Ahead Logging (`PRAGMA journal_mode=WAL;`), which allows concurrent readers while one writer is active.
- Close any external SQLite viewers or table editors holding the database file `data/network_analyzer.db`.

---

## 5. JDK Not Found

**Symptom**:
`'java' is not recognized as an internal or external command`.

**Resolution**:

- Install Oracle JDK 17, Eclipse Temurin 17, or Microsoft OpenJDK 17.
- Add `%JAVA_HOME%\bin` to your system `PATH` environment variable.
- Verify in PowerShell:

  ```powershell
  java -version
  ```
