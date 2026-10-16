package com.networkanalyzer.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MetricsCalculatorTest {

    @Test
    @DisplayName("Calculate RTT from nanosecond timestamps")
    void testCalculateRtt() {
        long startNanos = 1_000_000_000L;
        long endNanos = 1_015_500_000L; // +15.5 ms
        double rtt = MetricsCalculator.calculateRttMs(startNanos, endNanos);
        assertEquals(15.5, rtt, 0.001);

        // Clocks out of order edge case
        assertEquals(0.0, MetricsCalculator.calculateRttMs(endNanos, startNanos));
    }

    @Test
    @DisplayName("Calculate Throughput in Mbps (decimal megabits)")
    void testCalculateThroughput() {
        // 1,000,000 bytes transferred in 1 second
        // 1,000,000 * 8 / 1 / 1,000,000 = 8.0 Mbps
        Double throughput = MetricsCalculator.calculateThroughputMbps(1_000_000L, 1.0);
        assertNotNull(throughput);
        assertEquals(8.0, throughput, 0.001);

        // Edge case: zero duration
        assertNull(MetricsCalculator.calculateThroughputMbps(1_000_000L, 0.0));
        assertNull(MetricsCalculator.calculateThroughputMbps(1_000_000L, -1.0));
    }

    @Test
    @DisplayName("Calculate Data Transfer Rate in MB/s")
    void testCalculateDataTransferRate() {
        // 1048576 bytes in 1.0 second = 1.0 MB/s
        Double rate = MetricsCalculator.calculateDataTransferRateMBs(1024 * 1024L, 1.0);
        assertNotNull(rate);
        assertEquals(1.0, rate, 0.001);

        assertNull(MetricsCalculator.calculateDataTransferRateMBs(100L, 0.0));
    }

    @Test
    @DisplayName("Calculate Packet Loss and Delivery Ratio")
    void testPacketLossAndDeliveryRatio() {
        // 100 sent, 95 received -> 5% loss, 95% delivery ratio
        Double loss = MetricsCalculator.calculatePacketLossPct(100, 95);
        assertNotNull(loss);
        assertEquals(5.0, loss, 0.01);

        Double delivery = MetricsCalculator.calculatePacketDeliveryRatioPct(100, 95);
        assertNotNull(delivery);
        assertEquals(95.0, delivery, 0.01);

        // 0 sent -> null
        assertNull(MetricsCalculator.calculatePacketLossPct(0, 0));
        assertNull(MetricsCalculator.calculatePacketDeliveryRatioPct(0, 0));

        // 100 sent, 0 received -> 100% loss, 0% delivery
        assertEquals(100.0, MetricsCalculator.calculatePacketLossPct(100, 0), 0.01);
        assertEquals(0.0, MetricsCalculator.calculatePacketDeliveryRatioPct(100, 0), 0.01);
    }

    @Test
    @DisplayName("Calculate Jitter from consecutive RTT differences")
    void testCalculateJitter() {
        // Samples: 10.0, 12.0, 11.0, 15.0
        // Differences: |12-10| = 2, |11-12| = 1, |15-11| = 4
        // Mean difference = (2 + 1 + 4) / 3 = 7 / 3 = 2.333 ms
        List<Double> samples = Arrays.asList(10.0, 12.0, 11.0, 15.0);
        Double jitter = MetricsCalculator.calculateJitterMs(samples);
        assertNotNull(jitter);
        assertEquals(2.333, jitter, 0.001);

        // Single sample or empty
        assertNull(MetricsCalculator.calculateJitterMs(Collections.singletonList(10.0)));
        assertNull(MetricsCalculator.calculateJitterMs(Collections.emptyList()));
        assertNull(MetricsCalculator.calculateJitterMs(null));
    }

    @Test
    @DisplayName("Calculate Bandwidth Utilization")
    void testCalculateBandwidthUtilization() {
        // 50 Mbps measured throughput on a 100 Mbps link -> 50%
        Double util = MetricsCalculator.calculateBandwidthUtilizationPct(50.0, 100.0);
        assertNotNull(util);
        assertEquals(50.0, util, 0.01);

        // Null link capacity
        assertNull(MetricsCalculator.calculateBandwidthUtilizationPct(50.0, null));
        // Zero link capacity
        assertNull(MetricsCalculator.calculateBandwidthUtilizationPct(50.0, 0.0));
    }

    @Test
    @DisplayName("Calculate Statistical Summary and Percentiles")
    void testCalculateStatistics() {
        List<Double> samples = Arrays.asList(10.0, 20.0, 30.0, 40.0, 50.0);
        SampleStatistics stats = MetricsCalculator.calculateStatistics(samples);

        assertEquals(5, stats.getCount());
        assertEquals(10.0, stats.getMin());
        assertEquals(50.0, stats.getMax());
        assertEquals(30.0, stats.getMean());
        assertEquals(30.0, stats.getMedian());
        assertTrue(stats.getP95() >= 45.0 && stats.getP95() <= 50.0);
        assertTrue(stats.getStandardDeviation() > 0.0);

        // Empty list
        SampleStatistics emptyStats = MetricsCalculator.calculateStatistics(Collections.emptyList());
        assertEquals(0, emptyStats.getCount());
        assertNull(emptyStats.getMin());
    }
}
