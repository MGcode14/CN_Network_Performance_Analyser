package com.networkanalyzer.server;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class UdpTestStats {
    private final String testId;
    private final AtomicLong totalPacketsReceived = new AtomicLong(0);
    private final AtomicLong totalBytesReceived = new AtomicLong(0);
    private final AtomicLong duplicatePackets = new AtomicLong(0);
    private final AtomicLong outOfOrderPackets = new AtomicLong(0);
    private final Set<Long> receivedSequences = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private volatile long lastSequenceNumber = 0;
    private volatile long firstPacketNanos = 0;
    private volatile long lastPacketNanos = 0;

    public UdpTestStats(String testId) {
        this.testId = testId;
    }

    public synchronized void recordPacket(long seqNum, int payloadLength, long timestampNanos) {
        long now = System.nanoTime();
        if (firstPacketNanos == 0) {
            firstPacketNanos = now;
        }
        lastPacketNanos = now;

        totalPacketsReceived.incrementAndGet();
        totalBytesReceived.addAndGet(payloadLength);

        if (!receivedSequences.add(seqNum)) {
            duplicatePackets.incrementAndGet();
        } else {
            if (seqNum < lastSequenceNumber) {
                outOfOrderPackets.incrementAndGet();
            }
            if (seqNum > lastSequenceNumber) {
                lastSequenceNumber = seqNum;
            }
        }
    }

    public String getTestId() { return testId; }
    public long getTotalPacketsReceived() { return totalPacketsReceived.get(); }
    public long getUniquePacketsReceived() { return receivedSequences.size(); }
    public long getTotalBytesReceived() { return totalBytesReceived.get(); }
    public long getDuplicatePackets() { return duplicatePackets.get(); }
    public long getOutOfOrderPackets() { return outOfOrderPackets.get(); }
    public long getLastSequenceNumber() { return lastSequenceNumber; }
    public long getFirstPacketNanos() { return firstPacketNanos; }
    public long getLastPacketNanos() { return lastPacketNanos; }

    public double getElapsedDurationSeconds() {
        if (firstPacketNanos == 0 || lastPacketNanos <= firstPacketNanos) {
            return 0.0;
        }
        return (lastPacketNanos - firstPacketNanos) / 1_000_000_000.0;
    }
}
