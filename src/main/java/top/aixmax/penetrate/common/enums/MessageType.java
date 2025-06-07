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
    DISCONNECT_ACK((byte) 11),
    CONNECT((byte) 12),
    CONNECT_ACK((byte) 13);

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

    @Override
    public String toString() {
        return name() + "(" + value + ")";
    }
}
