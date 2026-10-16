# Network Performance Analyzer — User Guide

This guide walks through configuring, executing, and evaluating network performance experiments using the Network Performance Analyzer dashboard.

---

## 1. Quick Start

1. Start the analyzer by double-clicking `scripts/start.bat` or executing:

   ```cmd
   .\scripts\start.bat
   ```

2. Open your web browser to:

   ```
   http://localhost:8080/
   ```

3. Observe the top status bar:
   - `TCP :9091 [ONLINE]`
   - `UDP :9092 [ONLINE]`
   - `API :8080`

---

## 2. Running an Experiment

### Step 1: Select Protocol

- **TCP**: Connection-oriented, guaranteed reliable delivery, ordered byte stream.
- **UDP**: Connectionless datagram transmission, low latency, no transport-level ACKs.
- **BOTH**: Automatically runs both TCP and UDP sequentially under matching parameters for side-by-side comparison.

### Step 2: Choose Test Mode

- **RTT Latency**: Sequentially sends request packets and measures exact round-trip response latency using high-resolution monotonic timers. Computes Min, Mean, Median, 95th Percentile, and Jitter.
- **Bulk Throughput**: Transfers continuous or paced payloads to measure maximum transfer throughput in Mbps and MB/s.
- **Packet Delivery**: Evaluates sequence loss, delivery ratio, duplicate detection, and out-of-order arrival rates.

### Step 3: Configure Parameters

- **Target Host**: Enter `127.0.0.1` for local loopback testing, or the IP address of a remote lab machine running the receiver.
- **Payload Size**: Choose from presets (64 B, 512 B, 1 KB, 8 KB, 32 KB, 64 KB) or enter a custom byte count.
- **Packet Count**: Number of packets or test iterations (default: 100).
- **Sending Rate (PPS)**: For UDP, optionally set a packet rate limiter in packets per second (0 = unthrottled burst).
- **Warm-up Packets**: Number of discarded pre-test packets to warm up socket caches and thread pools.
- **Link Capacity (Mbps)**: Optional nominal bandwidth (e.g., 100 Mbps) to calculate bandwidth utilization percentage.

### Step 4: Execute & Monitor

- Click **Run Experiment**.
- Monitor the live progress bar and status feed.
- Inspect the real-time performance cards (Average RTT, Throughput, Packet Loss, Jitter, Delivery Ratio, Duration).
- Review the generated Chart.js visualizations (RTT timeline, Throughput profile, Packet delivery doughnut).

---

## 3. Comparing TCP and UDP

1. Navigate to the **TCP vs UDP Comparison** tab.
2. Select a completed TCP experiment and UDP experiment from the dropdown selectors.
3. Click **Compare Protocols**.
4. Review:
   - **Automated Technical Analysis**: Explains the protocol trade-offs observed under the tested configuration.
   - **Delta Differences**: Throughput difference (Mbps), Latency difference (ms), Packet loss delta.
   - **Protocol Advantage Badges**: Highlights the faster or lower-latency protocol.
   - **Comparability Warnings**: Alerts if configurations differed in payload size, test mode, or duration.

---

## 4. History and Data Export

1. Open the **Experiment History** tab.
2. Filter experiments by Protocol (`ALL`, `TCP`, `UDP`, `BOTH`) or Test Mode.
3. Use the search bar to locate experiments by ID, target host, or label.
4. Click **Details** to open the modal containing complete sample distributions and summary statistics.
5. Click **Export JSON** to download raw experiment metrics for reporting or external analysis.
