# Network Performance Analyzer — Metrics and Mathematical Formulas

This document details the exact measurement models, mathematical formulas, timing mechanisms, and statistical calculations implemented in the Network Performance Analyzer.

---

## 1. Timing and Clocks

All elapsed-time calculations for latency and duration utilize Java's monotonic nanosecond timer:

```java
System.nanoTime()
```

- **Monotonicity**: Unlike `System.currentTimeMillis()`, `System.nanoTime()` is not subject to wall-clock adjustments, NTP time slews, or daylight saving shifts.
- **Conversion**: Nanosecond differences are converted to milliseconds using:
  $$\text{Elapsed (ms)} = \frac{\text{EndNanos} - \text{StartNanos}}{1{,}000{,}000.0}$$
  And to seconds for throughput calculations using:
  $$\text{Elapsed (s)} = \frac{\text{EndNanos} - \text{StartNanos}}{1{,}000{,}000{,}000.0}$$

---

## 2. Core Metrics & Formulas

### 2.1 Round-Trip Time (RTT)

The round-trip time measures the elapsed interval from the transmission of an application-layer request packet to the reception of its corresponding acknowledgment/response packet:
$$\text{RTT (ms)} = \frac{T_{\text{recv}} - T_{\text{send}}}{1{,}000{,}000.0}$$

> **Distinction Note**: A sequential RTT measurement covers both forward and return paths, serialization, OS socket buffering, and server application processing. It is **not** labelled as one-way latency.

### 2.2 Throughput (Mbps)

Throughput measures the rate of successfully delivered application payload data over the transmission duration:
$$\text{Throughput (Mbps)} = \frac{\text{Payload Bytes Transferred} \times 8}{\text{Elapsed Seconds} \times 1{,}000{,}000.0}$$

- **Standard**: In computer networking (RFC specifications and telecommunications), Mbps denotes decimal megabits ($10^6$ bits), not binary mebibits ($2^{20}$ bits).
- **Application Payload Scope**: Only application-layer payload bytes are factored into the numerator; protocol framing headers are excluded to measure true *goodput*.

### 2.3 Effective Data Transfer Rate (MB/s)

Measures payload transfer rate in binary megabytes per second:
$$\text{Data Transfer Rate (MB/s)} = \frac{\text{Payload Bytes Transferred}}{1024 \times 1024 \times \text{Elapsed Seconds}}$$

### 2.4 Packet Loss Percentage (%)

Packet loss is computed from tracked sequence numbers and unique packet accounting:
$$\text{Packet Loss (\%)} = \frac{\text{Sent Unique Packets} - \text{Received Unique Packets}}{\text{Sent Unique Packets}} \times 100$$

- If $\text{Sent Unique Packets} = 0$, the metric is reported as `N/A`.
- Duplicates and out-of-order deliveries are tracked separately and do not inflate the unique packet count.

### 2.5 Packet Delivery Ratio (%)

$$\text{Packet Delivery Ratio (\%)} = \frac{\text{Received Unique Packets}}{\text{Sent Unique Packets}} \times 100$$

### 2.6 Jitter Estimation (RTT Variation)

Jitter is calculated as the mean of absolute differences between consecutive RTT samples:
$$\text{Jitter (ms)} = \frac{1}{N - 1} \sum_{i=1}^{N-1} | \text{RTT}_{i+1} - \text{RTT}_i |$$

- For $N < 2$, jitter is marked as `N/A`.
- **Note**: This represents round-trip delay variation. One-way packet delay variation (PDV / RFC 3393) requires synchronized sender/receiver clocks (e.g., GPS or PTP), which are not present on local uncalibrated machines.

### 2.7 Bandwidth Utilization (%)

Evaluates how much of a configured link's theoretical bandwidth was saturated by the measured throughput:
$$\text{Bandwidth Utilization (\%)} = \frac{\text{Measured Throughput (Mbps)}}{\text{Known Link Capacity (Mbps)}} \times 100$$

- This calculation is performed **only** when the user specifies a positive link capacity (e.g., 100 Mbps or 1000 Mbps). Measured throughput is never substituted for capacity.

---

## 3. Statistical Summaries and Percentiles

For a sample set of observations $X = \{x_1, x_2, \dots, x_N\}$:

1. **Minimum**: $\min(X)$
2. **Maximum**: $\max(X)$
3. **Mean**: $\mu = \frac{1}{N} \sum_{i=1}^N x_i$
4. **Standard Deviation**:
   $$s = \sqrt{\frac{1}{N - 1} \sum_{i=1}^N (x_i - \mu)^2} \quad (\text{for } N > 1)$$
5. **Median & 95th Percentile**:
   Calculated using linear interpolation on the sorted array at index:
   $$i = \frac{P}{100} \times (N - 1)$$
   $$\text{Value} = x_{\lfloor i \rfloor} + (i - \lfloor i \rfloor) \times (x_{\lceil i \rceil} - x_{\lfloor i \rfloor})$$
