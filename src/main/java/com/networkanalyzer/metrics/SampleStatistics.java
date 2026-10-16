package com.networkanalyzer.metrics;

public class SampleStatistics {
    private final int count;
    private final Double min;
    private final Double max;
    private final Double mean;
    private final Double median;
    private final Double p95;
    private final Double standardDeviation;

    public SampleStatistics(int count, Double min, Double max, Double mean, Double median, Double p95, Double standardDeviation) {
        this.count = count;
        this.min = min;
        this.max = max;
        this.mean = mean;
        this.median = median;
        this.p95 = p95;
        this.standardDeviation = standardDeviation;
    }

    public static SampleStatistics empty() {
        return new SampleStatistics(0, null, null, null, null, null, null);
    }

    public int getCount() { return count; }
    public Double getMin() { return min; }
    public Double getMax() { return max; }
    public Double getMean() { return mean; }
    public Double getMedian() { return median; }
    public Double getP95() { return p95; }
    public Double getStandardDeviation() { return standardDeviation; }

    @Override
    public String toString() {
        return String.format("Stats[n=%d, min=%.3f, mean=%.3f, med=%.3f, p95=%.3f, max=%.3f, sd=%.3f]",
                count, min != null ? min : 0.0, mean != null ? mean : 0.0,
                median != null ? median : 0.0, p95 != null ? p95 : 0.0,
                max != null ? max : 0.0, standardDeviation != null ? standardDeviation : 0.0);
    }
}
