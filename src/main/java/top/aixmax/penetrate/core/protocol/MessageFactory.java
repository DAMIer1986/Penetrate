package top.aixmax.penetrate.core.protocol;

import top.aixmax.penetrate.common.enums.MessageType;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * @author wangxu
 * @version 1.0 2024/11/16 18:06
 * @description
 */
public class MessageFactory {

    public static byte[] createRegisterMessage(String data) {
        return Message.create()
                .setType(MessageType.REGISTER)
                .setData(data.getBytes(StandardCharsets.UTF_8))
                .getBytes();
    }

    public static byte[] createRegisterAckMessage() {
        return Message.create()
                .setType(MessageType.REGISTER_ACK)
                .setData(new byte[0])
                .getBytes();
    }

    public static byte[] createHeartbeatMessage() {
        return Message.create()
                .setType(MessageType.HEARTBEAT)
                .setData(new byte[0])
                .getBytes();
    }

    public static byte[] createHeartbeatAckMessage() {
        return Message.create()
                .setType(MessageType.HEARTBEAT_ACK)
                .setData(new byte[0])
                .getBytes();
    }

    public static byte[] createDataMessage(byte[] data) {
        return Message.create()
                .setType(MessageType.DATA)
                .setData(data)
                .getBytes();
    }

    public static byte[] createDataAckMessage(int sequence) {
        return Message.create()
                .setType(MessageType.DATA_ACK)
                .setData(new byte[]{(byte) (sequence >>> 24),
                        (byte) (sequence >>> 16),
                        (byte) (sequence >>> 8),
                        (byte) sequence})
                .getBytes();
    }

    public static byte[] createErrorMessage(String errorMessage) {
        return Message.create()
                .setType(MessageType.ERROR)
                .setData(errorMessage.getBytes(StandardCharsets.UTF_8))
                .getBytes();
    }

    /**
     * 创建端口映射确认消息
     */
    public static byte[] createPortMappingAckMessage() {
        return Message.create()
                .setType(MessageType.PORT_MAPPING_ACK)
                .setData(new byte[0])
                .getBytes();
    }

    /**
     * 创建端口映射消息
     */
    public static byte[] createPortMappingMessage(int localPort, int remotePort) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putInt(localPort);
        buffer.putInt(remotePort);
        return Message.create()
                .setType(MessageType.PORT_MAPPING)
                .setData(buffer.array())
                .getBytes();
    }
}
