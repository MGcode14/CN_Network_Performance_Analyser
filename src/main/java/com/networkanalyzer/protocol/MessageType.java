package com.networkanalyzer.protocol;

public enum MessageType {
    HANDSHAKE((byte) 0x01),
    HANDSHAKE_ACK((byte) 0x02),
    RTT_REQUEST((byte) 0x03),
    RTT_RESPONSE((byte) 0x04),
    BULK_DATA((byte) 0x05),
    BULK_ACK((byte) 0x06),
    STATUS_QUERY((byte) 0x07),
    STATUS_REPLY((byte) 0x08),
    TERMINATE((byte) 0x09);

    private final byte code;

    MessageType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static MessageType fromCode(byte code) {
        for (MessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown message type code: " + code);
    }
}
