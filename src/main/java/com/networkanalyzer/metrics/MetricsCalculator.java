package com.networkanalyzer.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MetricsCalculator {

    public static double calculateRttMs(long startNanos, long endNanos) {
        if (endNanos < startNanos) {
            return 0.0;
        }
        return (endNanos - startNanos) / 1_000_000.0;
    }

    public static Double calculateThroughputMbps(long payloadBytes, double durationSeconds) {
        if (durationSeconds <= 0.0 || payloadBytes < 0) {
            return null;
        }
        // Formula: Bytes * 8 / seconds / 1,000,000 (decimal megabits per second)
        double mbps = (payloadBytes * 8.0) / durationSeconds / 1_000_000.0;
        return round(mbps, 3);
    }

    public static Double calculateDataTransferRateMBs(long payloadBytes, double durationSeconds) {
        if (durationSeconds <= 0.0 || payloadBytes < 0) {
            return null;
        }
        // Formula: Bytes / (1024 * 1024) / seconds (MB/s)
        double rate = (payloadBytes / (1024.0 * 1024.0)) / durationSeconds;
        return round(rate, 3);
    }

    public static Double calculatePacketLossPct(long sentUnique, long receivedUnique) {
        if (sentUnique <= 0) {
            return null;
        }
        long lost = Math.max(0, sentUnique - receivedUnique);
        double lossPct = (lost * 100.0) / sentUnique;
        return round(Math.min(100.0, lossPct), 2);
    }

    public static Double calculatePacketDeliveryRatioPct(long sentUnique, long receivedUnique) {
        if (sentUnique <= 0) {
            return null;
        }
        double ratio = (Math.min(sentUnique, Math.max(0, receivedUnique)) * 100.0) / sentUnique;
        return round(ratio, 2);
    }

    public static Double calculateJitterMs(List<Double> rttSamples) {
        if (rttSamples == null || rttSamples.size() < 2) {
            return null;
        }
        double sumDiff = 0.0;
        int diffCount = 0;
        for (int i = 1; i < rttSamples.size(); i++) {
            Double prev = rttSamples.get(i - 1);
            Double curr = rttSamples.get(i);
            if (prev != null && curr != null) {
                sumDiff += Math.abs(curr - prev);
                diffCount++;
            }
        }
        if (diffCount == 0) {
            return null;
        }
        return round(sumDiff / diffCount, 3);
    }

    public static Double calculateBandwidthUtilizationPct(Double measuredThroughputMbps, Double knownLinkCapacityMbps) {
        if (measuredThroughputMbps == null || knownLinkCapacityMbps == null || knownLinkCapacityMbps <= 0.0) {
            return null;
        }
        double util = (measuredThroughputMbps / knownLinkCapacityMbps) * 100.0;
        return round(util, 2);
    }

    public static SampleStatistics calculateStatistics(List<Double> samples) {
        if (samples == null || samples.isEmpty()) {
            return SampleStatistics.empty();
        }

        List<Double> valid = new ArrayList<>();
        double sum = 0.0;
        for (Double s : samples) {
            if (s != null && !Double.isNaN(s) && !Double.isInfinite(s)) {
                valid.add(s);
                sum += s;
            }
        }

        int count = valid.size();
        if (count == 0) {
            return SampleStatistics.empty();
        }

        Collections.sort(valid);

        double min = valid.get(0);
        double max = valid.get(count - 1);
        double mean = sum / count;

        double median = calculatePercentile(valid, 50.0);
        double p95 = calculatePercentile(valid, 95.0);

        double sumSquares = 0.0;
        for (double val : valid) {
            sumSquares += Math.pow(val - mean, 2);
        }
        double stdDev = count > 1 ? Math.sqrt(sumSquares / (count - 1)) : 0.0;

        return new SampleStatistics(
                count,
                round(min, 3),
                round(max, 3),
                round(mean, 3),
                round(median, 3),
                round(p95, 3),
                round(stdDev, 3)
        );
    }

    public static double calculatePercentile(List<Double> sortedSamples, double percentile) {
        if (sortedSamples == null || sortedSamples.isEmpty()) {
            return 0.0;
        }
        if (percentile <= 0.0) {
            return sortedSamples.get(0);
        }
        if (percentile >= 100.0) {
            return sortedSamples.get(sortedSamples.size() - 1);
        }

        int n = sortedSamples.size();
        double index = (percentile / 100.0) * (n - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);

        if (lower == upper) {
            return sortedSamples.get(lower);
        }

        double fraction = index - lower;
        return sortedSamples.get(lower) + fraction * (sortedSamples.get(upper) - sortedSamples.get(lower));
    }

    public static double round(double value, int places) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        long factor = (long) Math.pow(10, places);
        return (double) Math.round(value * factor) / factor;
    }
}
