package com.networkanalyzer.protocol;

import com.networkanalyzer.exception.NetworkException;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;
import java.util.zip.CRC32;

public class NetworkPacket {
    public static final short MAGIC_NUMBER = 0x4E50; // 'N', 'P'
    public static final byte PROTOCOL_VERSION = 0x01;
    public static final int HEADER_SIZE = 44; // 2 + 1 + 1 + 16 + 8 + 8 + 4 + 4
    public static final int MAX_PAYLOAD_SIZE = 10 * 1024 * 1024; // 10 MB

    private final byte version;
    private final MessageType messageType;
    private final UUID testId;
    private final long sequenceNumber;
    private final long timestamp; // nanoseconds or epoch millis
    private final int payloadLength;
    private final long checksum; // CRC32
    private final byte[] payload;

    public NetworkPacket(byte version, MessageType messageType, UUID testId, long sequenceNumber, long timestamp, byte[] payload) {
        this.version = version;
        this.messageType = messageType;
        this.testId = testId != null ? testId : new UUID(0L, 0L);
        this.sequenceNumber = sequenceNumber;
        this.timestamp = timestamp;
        this.payload = payload != null ? payload : new byte[0];
        this.payloadLength = this.payload.length;

        if (this.payloadLength > MAX_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("Payload size " + this.payloadLength + " exceeds limit " + MAX_PAYLOAD_SIZE);
        }

        CRC32 crc = new CRC32();
        if (this.payload.length > 0) {
            crc.update(this.payload);
        }
        this.checksum = crc.getValue();
    }

    private NetworkPacket(byte version, MessageType messageType, UUID testId, long sequenceNumber, long timestamp, int payloadLength, long checksum, byte[] payload) {
        this.version = version;
        this.messageType = messageType;
        this.testId = testId;
        this.sequenceNumber = sequenceNumber;
        this.timestamp = timestamp;
        this.payloadLength = payloadLength;
        this.checksum = checksum;
        this.payload = payload;
    }

    public static NetworkPacket create(MessageType messageType, UUID testId, long sequenceNumber, long timestamp, byte[] payload) {
        return new NetworkPacket(PROTOCOL_VERSION, messageType, testId, sequenceNumber, timestamp, payload);
    }

    public static NetworkPacket create(MessageType messageType, String testIdStr, long sequenceNumber, long timestamp, byte[] payload) {
        UUID uuid;
        try {
            uuid = testIdStr != null ? UUID.fromString(testIdStr) : UUID.randomUUID();
        } catch (IllegalArgumentException e) {
            uuid = UUID.nameUUIDFromBytes(testIdStr.getBytes());
        }
        return new NetworkPacket(PROTOCOL_VERSION, messageType, uuid, sequenceNumber, timestamp, payload);
    }

    public byte getVersion() { return version; }
    public MessageType getMessageType() { return messageType; }
    public UUID getTestId() { return testId; }
    public String getTestIdString() { return testId != null ? testId.toString() : ""; }
    public long getSequenceNumber() { return sequenceNumber; }
    public long getTimestamp() { return timestamp; }
    public int getPayloadLength() { return payloadLength; }
    public long getChecksum() { return checksum; }
    public byte[] getPayload() { return payload; }

    public boolean verifyChecksum() {
        if (payload == null || payload.length == 0) {
            return checksum == 0;
        }
        CRC32 crc = new CRC32();
        crc.update(payload);
        return crc.getValue() == checksum;
    }

    public void writeTo(DataOutputStream out) throws IOException {
        out.writeShort(MAGIC_NUMBER);
        out.writeByte(version);
        out.writeByte(messageType.getCode());
        out.writeLong(testId.getMostSignificantBits());
        out.writeLong(testId.getLeastSignificantBits());
        out.writeLong(sequenceNumber);
        out.writeLong(timestamp);
        out.writeInt(payloadLength);
        out.writeInt((int) (checksum & 0xFFFFFFFFL));
        if (payloadLength > 0) {
            out.write(payload, 0, payloadLength);
        }
        out.flush();
    }

    public static NetworkPacket readFrom(DataInputStream in) throws IOException {
        short magic = in.readShort();
        if (magic != MAGIC_NUMBER) {
            throw new NetworkException("Invalid magic bytes: 0x" + Integer.toHexString(magic & 0xFFFF));
        }

        byte version = in.readByte();
        if (version != PROTOCOL_VERSION) {
            throw new NetworkException("Unsupported protocol version: " + version);
        }

        byte typeCode = in.readByte();
        MessageType type = MessageType.fromCode(typeCode);

        long mostSig = in.readLong();
        long leastSig = in.readLong();
        UUID testId = new UUID(mostSig, leastSig);

        long seqNum = in.readLong();
        long timestamp = in.readLong();
        int payloadLen = in.readInt();

        if (payloadLen < 0 || payloadLen > MAX_PAYLOAD_SIZE) {
            throw new NetworkException("Invalid payload length: " + payloadLen);
        }

        long checksum = in.readInt() & 0xFFFFFFFFL;

        byte[] payloadBytes = new byte[payloadLen];
        if (payloadLen > 0) {
            in.readFully(payloadBytes);
        }

        NetworkPacket packet = new NetworkPacket(version, type, testId, seqNum, timestamp, payloadLen, checksum, payloadBytes);
        if (!packet.verifyChecksum()) {
            throw new NetworkException("Packet checksum verification failed for seq " + seqNum);
        }
        return packet;
    }

    public byte[] toByteArray() {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + payloadLength);
        buffer.putShort(MAGIC_NUMBER);
        buffer.put(version);
        buffer.put(messageType.getCode());
        buffer.putLong(testId.getMostSignificantBits());
        buffer.putLong(testId.getLeastSignificantBits());
        buffer.putLong(sequenceNumber);
        buffer.putLong(timestamp);
        buffer.putInt(payloadLength);
        buffer.putInt((int) (checksum & 0xFFFFFFFFL));
        if (payloadLength > 0) {
            buffer.put(payload, 0, payloadLength);
        }
        return buffer.array();
    }

    public static NetworkPacket fromByteArray(byte[] data, int offset, int length) {
        if (length < HEADER_SIZE) {
            throw new NetworkException("Datagram length " + length + " is smaller than header size " + HEADER_SIZE);
        }

        ByteBuffer buffer = ByteBuffer.wrap(data, offset, length);
        short magic = buffer.getShort();
        if (magic != MAGIC_NUMBER) {
            throw new NetworkException("Invalid datagram magic bytes: 0x" + Integer.toHexString(magic & 0xFFFF));
        }

        byte ver = buffer.get();
        if (ver != PROTOCOL_VERSION) {
            throw new NetworkException("Unsupported protocol version in datagram: " + ver);
        }

        byte typeCode = buffer.get();
        MessageType type = MessageType.fromCode(typeCode);

        long mostSig = buffer.getLong();
        long leastSig = buffer.getLong();
        UUID testId = new UUID(mostSig, leastSig);

        long seqNum = buffer.getLong();
        long timestamp = buffer.getLong();
        int payloadLen = buffer.getInt();

        if (payloadLen < 0 || payloadLen > (length - HEADER_SIZE)) {
            throw new NetworkException("Invalid datagram payload length: " + payloadLen + " (available: " + (length - HEADER_SIZE) + ")");
        }

        long checksum = buffer.getInt() & 0xFFFFFFFFL;

        byte[] payloadBytes = new byte[payloadLen];
        if (payloadLen > 0) {
            buffer.get(payloadBytes, 0, payloadLen);
        }

        NetworkPacket packet = new NetworkPacket(ver, type, testId, seqNum, timestamp, payloadLen, checksum, payloadBytes);
        if (!packet.verifyChecksum()) {
            throw new NetworkException("Datagram CRC32 checksum mismatch for seq " + seqNum);
        }
        return packet;
    }

    @Override
    public String toString() {
        return "NetworkPacket{" +
                "type=" + messageType +
                ", testId=" + testId +
                ", seqNum=" + sequenceNumber +
                ", len=" + payloadLength +
                '}';
    }
}
