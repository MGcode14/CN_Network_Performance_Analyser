package com.networkanalyzer.analyzer;

import com.networkanalyzer.metrics.MetricsCalculator;
import com.networkanalyzer.model.ComparisonResult;
import com.networkanalyzer.model.Experiment;

import java.util.ArrayList;
import java.util.List;

public class ComparisonEngine {

    public static ComparisonResult compare(Experiment tcpExp, Experiment udpExp) {
        ComparisonResult res = new ComparisonResult();
        res.setTcpExperiment(tcpExp);
        res.setUdpExperiment(udpExp);

        if (tcpExp == null || udpExp == null) {
            res.setComparable(false);
            res.getWarnings().add("Both TCP and UDP experiments must be provided for comparison.");
            res.setTechnicalAnalysis("Incomplete comparison: one or both experiments are missing.");
            return res;
        }

        List<String> warnings = new ArrayList<>();
        boolean comparable = true;

        if (tcpExp.getPayloadSize() != udpExp.getPayloadSize()) {
            comparable = false;
            warnings.add(String.format("Payload sizes differ: TCP tested with %d B, UDP tested with %d B.",
                    tcpExp.getPayloadSize(), udpExp.getPayloadSize()));
        }

        if (tcpExp.getTestMode() != udpExp.getTestMode()) {
            comparable = false;
            warnings.add(String.format("Test modes differ: TCP was '%s', UDP was '%s'.",
                    tcpExp.getTestMode(), udpExp.getTestMode()));
        }

        if (!tcpExp.getTargetHost().equalsIgnoreCase(udpExp.getTargetHost())) {
            comparable = false;
            warnings.add(String.format("Target hosts differ: TCP targeted '%s', UDP targeted '%s'.",
                    tcpExp.getTargetHost(), udpExp.getTargetHost()));
        }

        res.setComparable(comparable);
        res.setWarnings(warnings);

        // Throughput comparison
        if (tcpExp.getThroughputMbps() != null && udpExp.getThroughputMbps() != null) {
            double diff = MetricsCalculator.round(tcpExp.getThroughputMbps() - udpExp.getThroughputMbps(), 3);
            res.setThroughputDifferenceMbps(diff);
            if (udpExp.getThroughputMbps() > 0) {
                double deltaPct = MetricsCalculator.round(((tcpExp.getThroughputMbps() - udpExp.getThroughputMbps()) / udpExp.getThroughputMbps()) * 100.0, 2);
                res.setThroughputDeltaPercent(deltaPct);
            }
            if (tcpExp.getThroughputMbps() > udpExp.getThroughputMbps()) {
                res.setFasterProtocol("TCP");
            } else if (udpExp.getThroughputMbps() > tcpExp.getThroughputMbps()) {
                res.setFasterProtocol("UDP");
            } else {
                res.setFasterProtocol("EQUAL");
            }
        }

        // Latency / RTT comparison
        if (tcpExp.getAvgRttMs() != null && udpExp.getAvgRttMs() != null) {
            double diff = MetricsCalculator.round(tcpExp.getAvgRttMs() - udpExp.getAvgRttMs(), 3);
            res.setAvgRttDifferenceMs(diff);
            if (udpExp.getAvgRttMs() > 0) {
                double deltaPct = MetricsCalculator.round(((tcpExp.getAvgRttMs() - udpExp.getAvgRttMs()) / udpExp.getAvgRttMs()) * 100.0, 2);
                res.setAvgRttDeltaPercent(deltaPct);
            }
            if (tcpExp.getAvgRttMs() < udpExp.getAvgRttMs()) {
                res.setLowerLatencyProtocol("TCP");
            } else if (udpExp.getAvgRttMs() < tcpExp.getAvgRttMs()) {
                res.setLowerLatencyProtocol("UDP");
            } else {
                res.setLowerLatencyProtocol("EQUAL");
            }
        }

        // Loss comparison
        if (tcpExp.getPacketLossPct() != null && udpExp.getPacketLossPct() != null) {
            double diff = MetricsCalculator.round(tcpExp.getPacketLossPct() - udpExp.getPacketLossPct(), 2);
            res.setPacketLossDifferencePct(diff);
            if (tcpExp.getPacketLossPct() < udpExp.getPacketLossPct()) {
                res.setLowerLossProtocol("TCP");
            } else if (udpExp.getPacketLossPct() < tcpExp.getPacketLossPct()) {
                res.setLowerLossProtocol("UDP");
            } else {
                res.setLowerLossProtocol("EQUAL");
            }
        }

        // Jitter comparison
        if (tcpExp.getJitterMs() != null && udpExp.getJitterMs() != null) {
            res.setJitterDifferenceMs(MetricsCalculator.round(tcpExp.getJitterMs() - udpExp.getJitterMs(), 3));
        }

        // Duration difference
        if (tcpExp.getDurationMs() != null && udpExp.getDurationMs() != null) {
            res.setDurationDifferenceMs(MetricsCalculator.round(tcpExp.getDurationMs() - udpExp.getDurationMs(), 2));
        }

        res.setTechnicalAnalysis(generateAnalysis(res, tcpExp, udpExp));
        return res;
    }

    private static String generateAnalysis(ComparisonResult res, Experiment tcpExp, Experiment udpExp) {
        StringBuilder sb = new StringBuilder();

        if (!res.isComparable()) {
            sb.append("Note: Test parameters vary between runs, so absolute cross-protocol conclusions should be treated with caution. ");
        }

        sb.append(String.format("Under the tested configuration (%s mode, %d B payload): ",
                tcpExp.getTestMode(), tcpExp.getPayloadSize()));

        if ("TCP".equals(res.getFasterProtocol())) {
            sb.append(String.format("TCP achieved higher measured throughput (%.2f Mbps vs UDP %.2f Mbps). ",
                    tcpExp.getThroughputMbps(), udpExp.getThroughputMbps()));
        } else if ("UDP".equals(res.getFasterProtocol())) {
            sb.append(String.format("UDP achieved higher measured throughput (%.2f Mbps vs TCP %.2f Mbps). ",
                    udpExp.getThroughputMbps(), tcpExp.getThroughputMbps()));
        }

        if ("UDP".equals(res.getLowerLatencyProtocol())) {
            sb.append(String.format("UDP exhibited lower round-trip latency (%.2f ms vs TCP %.2f ms), benefiting from absence of connection state and per-segment acknowledgment overhead. ",
                    udpExp.getAvgRttMs(), tcpExp.getAvgRttMs()));
        } else if ("TCP".equals(res.getLowerLatencyProtocol())) {
            sb.append(String.format("TCP exhibited lower round-trip latency (%.2f ms vs UDP %.2f ms). ",
                    tcpExp.getAvgRttMs(), udpExp.getAvgRttMs()));
        }

        if (udpExp.getPacketLossPct() != null && udpExp.getPacketLossPct() > 0) {
            sb.append(String.format("UDP recorded %.2f%% packet loss, whereas TCP recorded %.2f%% packet loss due to transport-layer reliability mechanisms. ",
                    udpExp.getPacketLossPct(), tcpExp.getPacketLossPct() != null ? tcpExp.getPacketLossPct() : 0.0));
        } else if (udpExp.getPacketLossPct() != null && udpExp.getPacketLossPct() == 0.0) {
            sb.append("Both protocols recorded 100% packet delivery without socket-level packet loss on the tested link. ");
        }

        return sb.toString().trim();
    }
}
