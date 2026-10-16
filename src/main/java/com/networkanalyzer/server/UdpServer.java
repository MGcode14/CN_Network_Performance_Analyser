package com.networkanalyzer.server;

import com.networkanalyzer.exception.NetworkException;
import com.networkanalyzer.protocol.MessageType;
import com.networkanalyzer.protocol.NetworkPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class UdpServer {
    private static final Logger logger = LoggerFactory.getLogger(UdpServer.class);
    private static final int BUFFER_SIZE = 65535;

    private final String bindHost;
    private final int port;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong packetsReceived = new AtomicLong(0);
    private final AtomicLong packetsSent = new AtomicLong(0);
    private final Map<String, UdpTestStats> testStatsMap = new ConcurrentHashMap<>();

    private DatagramSocket datagramSocket;
    private Thread receiveThread;

    public UdpServer(String bindHost, int port) {
        this.bindHost = bindHost;
        this.port = port;
    }

    public synchronized void start() throws NetworkException {
        if (running.get()) {
            logger.warn("UDP Receiver already running on {}:{}", bindHost, port);
            return;
        }

        try {
            InetAddress address = InetAddress.getByName(bindHost);
            datagramSocket = new DatagramSocket(new InetSocketAddress(address, port));
            datagramSocket.setReceiveBufferSize(2 * 1024 * 1024); // 2 MB OS socket buffer
            datagramSocket.setSendBufferSize(2 * 1024 * 1024);
            running.set(true);

            receiveThread = new Thread(this::listenForDatagrams, "udp-receive-thread");
            receiveThread.setDaemon(true);
            receiveThread.start();

            logger.info("UDP Receiver started successfully on {}:{}", bindHost, datagramSocket.getLocalPort());
        } catch (IOException e) {
            running.set(false);
            throw new NetworkException("Failed to bind UDP Receiver on " + bindHost + ":" + port + ": " + e.getMessage(), e);
        }
    }

    private void listenForDatagrams() {
        byte[] buffer = new byte[BUFFER_SIZE];

        while (running.get() && datagramSocket != null && !datagramSocket.isClosed()) {
            try {
                DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
                datagramSocket.receive(datagram);

                packetsReceived.incrementAndGet();

                try {
                    NetworkPacket packet = NetworkPacket.fromByteArray(datagram.getData(), datagram.getOffset(), datagram.getLength());
                    String testIdStr = packet.getTestIdString();

                    if (packet.getMessageType() == MessageType.BULK_DATA) {
                        pruneOldStats();
                        UdpTestStats stats = testStatsMap.computeIfAbsent(testIdStr, UdpTestStats::new);
                        stats.recordPacket(packet.getSequenceNumber(), packet.getPayloadLength(), packet.getTimestamp());
                    } else if (packet.getMessageType() == MessageType.RTT_REQUEST) {
                        // Echo response back to sender
                        NetworkPacket response = NetworkPacket.create(
                                MessageType.RTT_RESPONSE,
                                packet.getTestId(),
                                packet.getSequenceNumber(),
                                packet.getTimestamp(),
                                packet.getPayload()
                        );
                        byte[] responseBytes = response.toByteArray();
                        DatagramPacket responseDatagram = new DatagramPacket(
                                responseBytes,
                                responseBytes.length,
                                datagram.getAddress(),
                                datagram.getPort()
                        );
                        datagramSocket.send(responseDatagram);
                        packetsSent.incrementAndGet();
                    } else if (packet.getMessageType() == MessageType.STATUS_QUERY) {
                        UdpTestStats stats = testStatsMap.get(testIdStr);
                        long totalRecv = stats != null ? stats.getTotalPacketsReceived() : 0L;
                        long uniqueRecv = stats != null ? stats.getUniquePacketsReceived() : 0L;
                        long duplicates = stats != null ? stats.getDuplicatePackets() : 0L;
                        long outOfOrder = stats != null ? stats.getOutOfOrderPackets() : 0L;

                        // Return serialized stats
                        ByteBuffer buf = ByteBuffer.allocate(32);
                        buf.putLong(totalRecv);
                        buf.putLong(uniqueRecv);
                        buf.putLong(duplicates);
                        buf.putLong(outOfOrder);

                        NetworkPacket statusReply = NetworkPacket.create(
                                MessageType.STATUS_REPLY,
                                packet.getTestId(),
                                packet.getSequenceNumber(),
                                System.nanoTime(),
                                buf.array()
                        );
                        byte[] replyBytes = statusReply.toByteArray();
                        DatagramPacket replyDatagram = new DatagramPacket(
                                replyBytes,
                                replyBytes.length,
                                datagram.getAddress(),
                                datagram.getPort()
                        );
                        datagramSocket.send(replyDatagram);
                        packetsSent.incrementAndGet();
                    }
                } catch (Exception e) {
                    logger.debug("Failed to process incoming datagram: {}", e.getMessage());
                }
            } catch (SocketException e) {
                if (!running.get()) {
                    break;
                }
                logger.error("UDP socket error: {}", e.getMessage());
            } catch (IOException e) {
                if (running.get()) {
                    logger.error("UDP I/O error: {}", e.getMessage());
                }
            }
        }
    }

    public synchronized void stop() {
        if (!running.get()) {
            return;
        }
        running.set(false);
        logger.info("Stopping UDP Receiver on {}:{}...", bindHost, port);

        if (datagramSocket != null && !datagramSocket.isClosed()) {
            datagramSocket.close();
        }

        logger.info("UDP Receiver stopped.");
    }

    public UdpTestStats getStats(String testId) {
        return testStatsMap.get(testId);
    }

    public void clearStats(String testId) {
        if (testId != null) {
            testStatsMap.remove(testId);
        }
    }

    private void pruneOldStats() {
        if (testStatsMap.size() > 100) {
            long now = System.nanoTime();
            testStatsMap.entrySet().removeIf(entry -> {
                long elapsed = now - entry.getValue().getLastPacketNanos();
                return elapsed > 300_000_000_000L; // 5 minutes
            });
            if (testStatsMap.size() > 150) {
                var it = testStatsMap.keySet().iterator();
                while (it.hasNext() && testStatsMap.size() > 50) {
                    it.next();
                    it.remove();
                }
            }
        }
    }

    public boolean isRunning() { return running.get(); }
    public String getBindHost() { return bindHost; }
    public int getPort() { return datagramSocket != null ? datagramSocket.getLocalPort() : port; }
    public long getPacketsReceived() { return packetsReceived.get(); }
    public long getPacketsSent() { return packetsSent.get(); }
}
