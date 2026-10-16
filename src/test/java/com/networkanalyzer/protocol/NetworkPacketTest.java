package com.networkanalyzer.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class NetworkPacketTest {

    @Test
    @DisplayName("Serialize and deserialize NetworkPacket over TCP stream")
    void testTcpSerialization() throws Exception {
        UUID testId = UUID.randomUUID();
        byte[] payload = "Hello Network Performance Analyzer!".getBytes(StandardCharsets.UTF_8);
        NetworkPacket packet = NetworkPacket.create(MessageType.RTT_REQUEST, testId, 42L, 987654321L, payload);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        packet.writeTo(dos);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        DataInputStream dis = new DataInputStream(bais);
        NetworkPacket decoded = NetworkPacket.readFrom(dis);

        assertEquals(MessageType.RTT_REQUEST, decoded.getMessageType());
        assertEquals(testId, decoded.getTestId());
        assertEquals(42L, decoded.getSequenceNumber());
        assertEquals(987654321L, decoded.getTimestamp());
        assertArrayEquals(payload, decoded.getPayload());
        assertTrue(decoded.verifyChecksum());
    }

    @Test
    @DisplayName("Serialize and deserialize NetworkPacket for UDP datagram")
    void testUdpDatagramSerialization() {
        UUID testId = UUID.randomUUID();
        byte[] payload = new byte[1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 128);
        }

        NetworkPacket packet = NetworkPacket.create(MessageType.BULK_DATA, testId, 100L, System.nanoTime(), payload);
        byte[] bytes = packet.toByteArray();

        NetworkPacket decoded = NetworkPacket.fromByteArray(bytes, 0, bytes.length);
        assertEquals(MessageType.BULK_DATA, decoded.getMessageType());
        assertEquals(testId, decoded.getTestId());
        assertEquals(100L, decoded.getSequenceNumber());
        assertArrayEquals(payload, decoded.getPayload());
        assertTrue(decoded.verifyChecksum());
    }
}
