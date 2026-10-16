package com.networkanalyzer.validation;

import com.networkanalyzer.exception.ValidationException;
import com.networkanalyzer.model.Protocol;
import com.networkanalyzer.model.TestConfig;

public class ConfigValidator {
    public static final int MIN_PORT = 1;
    public static final int MAX_PORT = 65535;
    public static final int MAX_TCP_PAYLOAD = 10 * 1024 * 1024; // 10MB
    public static final int MAX_UDP_PAYLOAD = 65507; // Max UDP payload over IPv4
    public static final int MAX_PACKET_COUNT = 500000;
    public static final int MAX_SENDING_RATE_PPS = 100000;
    public static final int MIN_TIMEOUT_MS = 100;
    public static final int MAX_TIMEOUT_MS = 60000;

    public static void validate(TestConfig config) throws ValidationException {
        if (config == null) {
            throw new ValidationException("Test configuration cannot be null");
        }

        if (config.getTargetHost() == null || config.getTargetHost().trim().isEmpty()) {
            throw new ValidationException("Target host cannot be empty");
        }

        String host = config.getTargetHost().trim();
        if (host.contains(" ") || host.contains(";")) {
            throw new ValidationException("Invalid target host format: " + host);
        }

        if (config.getProtocol() == Protocol.TCP || config.getProtocol() == Protocol.BOTH) {
            validatePort("TCP", config.getTcpPort());
        }

        if (config.getProtocol() == Protocol.UDP || config.getProtocol() == Protocol.BOTH) {
            validatePort("UDP", config.getUdpPort());
        }

        if (config.getPayloadSize() < 0) {
            throw new ValidationException("Payload size cannot be negative");
        }

        if (config.getProtocol() == Protocol.TCP && config.getPayloadSize() > MAX_TCP_PAYLOAD) {
            throw new ValidationException("TCP payload size exceeds maximum limit of " + MAX_TCP_PAYLOAD + " bytes");
        }

        if ((config.getProtocol() == Protocol.UDP || config.getProtocol() == Protocol.BOTH) && config.getPayloadSize() > MAX_UDP_PAYLOAD) {
            throw new ValidationException("UDP payload size exceeds IPv4 datagram limit of " + MAX_UDP_PAYLOAD + " bytes");
        }

        if (config.getPacketCount() < 1) {
            throw new ValidationException("Packet count must be at least 1");
        }

        if (config.getPacketCount() > MAX_PACKET_COUNT) {
            throw new ValidationException("Packet count exceeds maximum safety limit of " + MAX_PACKET_COUNT);
        }

        if (config.getSendingRatePps() < 0 || config.getSendingRatePps() > MAX_SENDING_RATE_PPS) {
            throw new ValidationException("Sending rate (pps) must be between 0 and " + MAX_SENDING_RATE_PPS);
        }

        if (config.getWarmupCount() < 0 || config.getWarmupCount() > 1000) {
            throw new ValidationException("Warmup count must be between 0 and 1000");
        }

        if (config.getTimeoutMs() < MIN_TIMEOUT_MS || config.getTimeoutMs() > MAX_TIMEOUT_MS) {
            throw new ValidationException("Timeout must be between " + MIN_TIMEOUT_MS + " ms and " + MAX_TIMEOUT_MS + " ms");
        }

        if (config.getLinkCapacityMbps() != null && config.getLinkCapacityMbps() <= 0) {
            throw new ValidationException("Link capacity must be greater than 0 Mbps");
        }

        if (config.getRepetitions() < 1 || config.getRepetitions() > 20) {
            throw new ValidationException("Repetitions must be between 1 and 20");
        }
    }

    private static void validatePort(String protocolName, int port) throws ValidationException {
        if (port < MIN_PORT || port > MAX_PORT) {
            throw new ValidationException(protocolName + " port must be between " + MIN_PORT + " and " + MAX_PORT + " (got " + port + ")");
        }
    }
}
