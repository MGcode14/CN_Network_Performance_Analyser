package com.networkanalyzer.model;

public class MeasurementSample {
    private Long id;
    private String experimentId;
    private long sampleSeq;
    private Double rttMs;
    private Double instantThroughputMbps;
    private long timestampMs;

    public MeasurementSample() {}

    public MeasurementSample(String experimentId, long sampleSeq, Double rttMs, Double instantThroughputMbps, long timestampMs) {
        this.experimentId = experimentId;
        this.sampleSeq = sampleSeq;
        this.rttMs = rttMs;
        this.instantThroughputMbps = instantThroughputMbps;
        this.timestampMs = timestampMs;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getExperimentId() { return experimentId; }
    public void setExperimentId(String experimentId) { this.experimentId = experimentId; }

    public long getSampleSeq() { return sampleSeq; }
    public void setSampleSeq(long sampleSeq) { this.sampleSeq = sampleSeq; }

    public Double getRttMs() { return rttMs; }
    public void setRttMs(Double rttMs) { this.rttMs = rttMs; }

    public Double getInstantThroughputMbps() { return instantThroughputMbps; }
    public void setInstantThroughputMbps(Double instantThroughputMbps) { this.instantThroughputMbps = instantThroughputMbps; }

    public long getTimestampMs() { return timestampMs; }
    public void setTimestampMs(long timestampMs) { this.timestampMs = timestampMs; }
}
