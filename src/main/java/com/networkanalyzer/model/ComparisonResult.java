package com.networkanalyzer.model;

import java.util.ArrayList;
import java.util.List;

public class ComparisonResult {
    private Experiment tcpExperiment;
    private Experiment udpExperiment;

    private boolean comparable;
    private List<String> warnings = new ArrayList<>();

    // Differences: positive means TCP > UDP, negative means UDP > TCP
    private Double throughputDifferenceMbps;
    private Double throughputDeltaPercent; // relative to TCP

    private Double avgRttDifferenceMs;
    private Double avgRttDeltaPercent;

    private Double packetLossDifferencePct;
    private Double jitterDifferenceMs;
    private Double durationDifferenceMs;

    private String fasterProtocol;
    private String lowerLatencyProtocol;
    private String lowerLossProtocol;

    private String technicalAnalysis;

    public ComparisonResult() {}

    public Experiment getTcpExperiment() { return tcpExperiment; }
    public void setTcpExperiment(Experiment tcpExperiment) { this.tcpExperiment = tcpExperiment; }

    public Experiment getUdpExperiment() { return udpExperiment; }
    public void setUdpExperiment(Experiment udpExperiment) { this.udpExperiment = udpExperiment; }

    public boolean isComparable() { return comparable; }
    public void setComparable(boolean comparable) { this.comparable = comparable; }

    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }

    public Double getThroughputDifferenceMbps() { return throughputDifferenceMbps; }
    public void setThroughputDifferenceMbps(Double throughputDifferenceMbps) { this.throughputDifferenceMbps = throughputDifferenceMbps; }

    public Double getThroughputDeltaPercent() { return throughputDeltaPercent; }
    public void setThroughputDeltaPercent(Double throughputDeltaPercent) { this.throughputDeltaPercent = throughputDeltaPercent; }

    public Double getAvgRttDifferenceMs() { return avgRttDifferenceMs; }
    public void setAvgRttDifferenceMs(Double avgRttDifferenceMs) { this.avgRttDifferenceMs = avgRttDifferenceMs; }

    public Double getAvgRttDeltaPercent() { return avgRttDeltaPercent; }
    public void setAvgRttDeltaPercent(Double avgRttDeltaPercent) { this.avgRttDeltaPercent = avgRttDeltaPercent; }

    public Double getPacketLossDifferencePct() { return packetLossDifferencePct; }
    public void setPacketLossDifferencePct(Double packetLossDifferencePct) { this.packetLossDifferencePct = packetLossDifferencePct; }

    public Double getJitterDifferenceMs() { return jitterDifferenceMs; }
    public void setJitterDifferenceMs(Double jitterDifferenceMs) { this.jitterDifferenceMs = jitterDifferenceMs; }

    public Double getDurationDifferenceMs() { return durationDifferenceMs; }
    public void setDurationDifferenceMs(Double durationDifferenceMs) { this.durationDifferenceMs = durationDifferenceMs; }

    public String getFasterProtocol() { return fasterProtocol; }
    public void setFasterProtocol(String fasterProtocol) { this.fasterProtocol = fasterProtocol; }

    public String getLowerLatencyProtocol() { return lowerLatencyProtocol; }
    public void setLowerLatencyProtocol(String lowerLatencyProtocol) { this.lowerLatencyProtocol = lowerLatencyProtocol; }

    public String getLowerLossProtocol() { return lowerLossProtocol; }
    public void setLowerLossProtocol(String lowerLossProtocol) { this.lowerLossProtocol = lowerLossProtocol; }

    public String getTechnicalAnalysis() { return technicalAnalysis; }
    public void setTechnicalAnalysis(String technicalAnalysis) { this.technicalAnalysis = technicalAnalysis; }
}
