package top.aixmax.penetrate.common.enums;

/**
 * @author wangxu
 * @version 1.0 2024/11/16 18:02
 * @description 消息类型枚举
 */
public enum MessageType {
    UNKNOWN((byte) 0),
    REGISTER((byte) 1),
    REGISTER_ACK((byte) 2),
    HEARTBEAT((byte) 3),
    HEARTBEAT_ACK((byte) 4),
    DATA((byte) 5),
    DATA_ACK((byte) 6),
    ERROR((byte) 7),
    PORT_MAPPING((byte) 8),
    PORT_MAPPING_ACK((byte) 9),
    DISCONNECT((byte) 10),
    DISCONNECT_ACK((byte) 11);

    private final byte value;

    MessageType(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }

    public static MessageType valueOf(byte value) {
        for (MessageType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return UNKNOWN;
    }

    /**
     * 检查是否是确认类型的消息
     */
    public boolean isAck() {
        return this == REGISTER_ACK
                || this == HEARTBEAT_ACK
                || this == DATA_ACK
                || this == PORT_MAPPING_ACK
                || this == DISCONNECT_ACK;
    }

    /**
     * 获取对应的确认消息类型
     */
    public MessageType getAckType() {
        switch (this) {
            case REGISTER:
                return REGISTER_ACK;
            case HEARTBEAT:
                return HEARTBEAT_ACK;
            case DATA:
                return DATA_ACK;
            case PORT_MAPPING:
                return PORT_MAPPING_ACK;
            case DISCONNECT:
                return DISCONNECT_ACK;
            default:
                return UNKNOWN;
        }
    }

    /**
     * 检查是否需要确认的消息类型
     */
    public boolean needsAck() {
        return this == REGISTER
                || this == HEARTBEAT
                || this == DATA
                || this == PORT_MAPPING
                || this == DISCONNECT;
    }

    @Override
    public String toString() {
        return name() + "(" + value + ")";
    }
}
