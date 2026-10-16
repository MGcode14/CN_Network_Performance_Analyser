package com.networkanalyzer.server;

import com.networkanalyzer.exception.NetworkException;
import com.networkanalyzer.protocol.MessageType;
import com.networkanalyzer.protocol.NetworkPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class TcpServer {
    private static final Logger logger = LoggerFactory.getLogger(TcpServer.class);

    private final String bindHost;
    private final int port;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong messagesProcessed = new AtomicLong(0);
    private final Set<Socket> activeSockets = ConcurrentHashMap.newKeySet();

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private ExecutorService clientThreadPool;

    public TcpServer(String bindHost, int port) {
        this.bindHost = bindHost;
        this.port = port;
    }

    public synchronized void start() throws NetworkException {
        if (running.get()) {
            logger.warn("TCP Server already running on {}:{}", bindHost, port);
            return;
        }

        try {
            InetAddress address = InetAddress.getByName(bindHost);
            serverSocket = new ServerSocket(port, 50, address);
            serverSocket.setReuseAddress(true);
            running.set(true);

            clientThreadPool = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "tcp-client-worker");
                t.setDaemon(true);
                return t;
            });

            acceptThread = new Thread(this::listenForClients, "tcp-accept-thread");
            acceptThread.setDaemon(true);
            acceptThread.start();

            logger.info("TCP Receiver started successfully on {}:{}", bindHost, serverSocket.getLocalPort());
        } catch (IOException e) {
            running.set(false);
            throw new NetworkException("Failed to bind TCP Receiver on " + bindHost + ":" + port + ": " + e.getMessage(), e);
        }
    }

    private void listenForClients() {
        while (running.get() && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setSoTimeout(10000); // 10 second idle timeout
                clientSocket.setTcpNoDelay(true);
                activeSockets.add(clientSocket);
                clientThreadPool.submit(() -> handleClient(clientSocket));
            } catch (SocketException e) {
                if (!running.get()) {
                    // Normal server shutdown
                    break;
                }
                logger.error("TCP accept error: {}", e.getMessage());
            } catch (IOException e) {
                if (running.get()) {
                    logger.error("TCP accept I/O error: {}", e.getMessage(), e);
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        long clientBytesReceived = 0;
        long clientPacketsReceived = 0;
        long startTimeNanos = System.nanoTime();

        try (socket;
             BufferedInputStream bis = new BufferedInputStream(socket.getInputStream(), 65536);
             DataInputStream dis = new DataInputStream(bis);
             BufferedOutputStream bos = new BufferedOutputStream(socket.getOutputStream(), 65536);
             DataOutputStream dos = new DataOutputStream(bos)) {

            while (running.get() && !socket.isClosed()) {
                NetworkPacket request;
                try {
                    request = NetworkPacket.readFrom(dis);
                } catch (EOFException e) {
                    // Client closed stream normally
                    break;
                } catch (SocketException e) {
                    // Client connection reset or closed
                    break;
                }

                messagesProcessed.incrementAndGet();

                if (request.getMessageType() == MessageType.RTT_REQUEST) {
                    clientPacketsReceived++;
                    clientBytesReceived += request.getPayloadLength();
                    // Echo back with matching testId, seqNum, timestamp, and optional payload
                    NetworkPacket response = NetworkPacket.create(
                            MessageType.RTT_RESPONSE,
                            request.getTestId(),
                            request.getSequenceNumber(),
                            request.getTimestamp(),
                            request.getPayload()
                    );
                    response.writeTo(dos);
                } else if (request.getMessageType() == MessageType.BULK_DATA) {
                    clientPacketsReceived++;
                    clientBytesReceived += request.getPayloadLength();
                    // Bulk data packet - consume payload without immediate response
                } else if (request.getMessageType() == MessageType.TERMINATE) {
                    // Client finished bulk transfer - respond with BULK_ACK
                    long durationNanos = System.nanoTime() - startTimeNanos;
                    ByteBuffer payloadBuf = ByteBuffer.allocate(24);
                    payloadBuf.putLong(clientPacketsReceived);
                    payloadBuf.putLong(clientBytesReceived);
                    payloadBuf.putLong(durationNanos);

                    NetworkPacket ack = NetworkPacket.create(
                            MessageType.BULK_ACK,
                            request.getTestId(),
                            request.getSequenceNumber(),
                            System.nanoTime(),
                            payloadBuf.array()
                    );
                    ack.writeTo(dos);
                    break;
                } else if (request.getMessageType() == MessageType.HANDSHAKE) {
                    NetworkPacket ack = NetworkPacket.create(
                            MessageType.HANDSHAKE_ACK,
                            request.getTestId(),
                            0L,
                            System.nanoTime(),
                            new byte[0]
                    );
                    ack.writeTo(dos);
                }
            }
        } catch (Exception e) {
            logger.debug("TCP client session ended: {}", e.getMessage());
        } finally {
            activeSockets.remove(socket);
            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException ignored) {}
        }
    }

    public synchronized void stop() {
        if (!running.get()) {
            return;
        }
        running.set(false);
        logger.info("Stopping TCP Receiver on {}:{}...", bindHost, port);

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.error("Error closing TCP server socket: {}", e.getMessage());
        }

        for (Socket s : activeSockets) {
            try {
                if (!s.isClosed()) {
                    s.close();
                }
            } catch (IOException ignored) {}
        }
        activeSockets.clear();

        if (clientThreadPool != null) {
            clientThreadPool.shutdownNow();
        }

        logger.info("TCP Receiver stopped.");
    }

    public boolean isRunning() { return running.get(); }
    public String getBindHost() { return bindHost; }
    public int getPort() { return serverSocket != null ? serverSocket.getLocalPort() : port; }
    public long getMessagesProcessed() { return messagesProcessed.get(); }
    public int getActiveConnections() { return activeSockets.size(); }
}
