package top.aixmax.penetrate.core.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import top.aixmax.penetrate.common.constants.ProtocolConstants;
import top.aixmax.penetrate.common.enums.MessageType;
import top.aixmax.penetrate.common.utils.ByteUtils;
import top.aixmax.penetrate.core.protocol.Message;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author wangxu
 * @version 1.0 2024/11/16 18:06
 * @description
 */
@Slf4j
public abstract class AbstractMessageHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private final Map<ChannelHandlerContext, byte[]> tempMapBytes = new ConcurrentHashMap<>();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf byteBuf) {
        byte[] bytes = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(bytes);
        byte[] tempBytes = tempMapBytes.get(ctx);

        if (tempBytes != null) {
            byte[] temp = new byte[tempBytes.length + bytes.length];
            System.arraycopy(tempBytes, 0, temp, 0, tempBytes.length);
            System.arraycopy(bytes, 0, temp, tempBytes.length, bytes.length);
            bytes = temp;
        }

        if (bytes.length < ProtocolConstants.minLength) {
            tempMapBytes.put(ctx, bytes);
            return;
        }
        int startIndex = 0;
        while (bytes[startIndex] != ProtocolConstants.start) {
            startIndex++;
            if (startIndex == bytes.length) {
                tempMapBytes.remove(ctx);
                return;
            }
        }

        if (bytes.length - startIndex < ProtocolConstants.minLength) {
            tempBytes = new byte[bytes.length - startIndex];
            tempMapBytes.put(ctx, tempBytes);
            System.arraycopy(bytes, startIndex, tempBytes, 0, bytes.length - startIndex);
            return;
        } else if (startIndex != 0) {
            byte[] temp = new byte[bytes.length + startIndex];
            System.arraycopy(bytes, startIndex, temp, 0, bytes.length - startIndex);
            bytes = temp;
        }

        int dateLength = ByteUtils.bytesToInt(bytes, 10);
        do {
            if (dateLength + ProtocolConstants.minLength == bytes.length) {
                tempMapBytes.remove(ctx);
                pushBytes(bytes, ctx);
                return;
            } else if (dateLength + ProtocolConstants.minLength > bytes.length) {
                tempMapBytes.put(ctx, bytes);
                return;
            }

            byte[] temp = new byte[dateLength + ProtocolConstants.minLength];
            tempBytes = new byte[bytes.length - dateLength - ProtocolConstants.minLength];

            System.arraycopy(bytes, 0, temp, 0, dateLength + ProtocolConstants.minLength);
            System.arraycopy(bytes, dateLength + ProtocolConstants.minLength,
                    tempBytes, 0, tempBytes.length);

            pushBytes(temp, ctx);

            if (tempBytes.length >= ProtocolConstants.minLength) {
                dateLength = ByteUtils.bytesToInt(tempBytes, 10);
            }
            bytes = tempBytes;
        } while (true);
    }

    protected void handleRegister(ChannelHandlerContext ctx, Message msg) {
    }

    protected void handleRegisterAck(ChannelHandlerContext ctx, Message msg) {
    }

    protected void handleHeartbeat(ChannelHandlerContext ctx) {
    }

    protected void handleHeartbeatAck(ChannelHandlerContext ctx) {
    }

    protected void handleData(ChannelHandlerContext ctx, Message msg) {
    }

    protected void handleDataAck(ChannelHandlerContext ctx, Message msg) {
    }

    protected void handleError(ChannelHandlerContext ctx, Message msg) {
    }

    protected void handleConnect(ChannelHandlerContext ctx, Message msg) {

    }

    protected void handleDisconnect(ChannelHandlerContext ctx, Message msg) {

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Channel exception caught", cause);
        ctx.close();
    }

    /**
     * 发送数据
     *
     * @param bytes 数据包
     * @param ctx   管道
     */
    private void pushBytes(byte[] bytes, ChannelHandlerContext ctx) {
        if (bytes[bytes.length - 1] != ProtocolConstants.end) {
            log.warn("Package is not complete! {}", bytes);
            return;
        }

        MessageType type = MessageType.valueOf(bytes[1]);

        byte[] data = new byte[bytes.length - ProtocolConstants.minLength];
        if (data.length != 0) {
            System.arraycopy(bytes, 14, data, 0, bytes.length - ProtocolConstants.minLength);
        }


        Message message = new Message();
        message.setType(type);
        message.setChannelId(ByteUtils.bytesToInt(bytes, 2));
        message.setExternalPort(ByteUtils.bytesToInt(bytes, 6));
        message.setData(data);
        try {
            switch (type) {
                case REGISTER: {
                    handleRegister(ctx, message);
                    break;
                }

                case REGISTER_ACK: {
                    handleRegisterAck(ctx, message);
                    break;
                }
                case HEARTBEAT: {
                    handleHeartbeat(ctx);
                    break;
                }
                case HEARTBEAT_ACK: {
                    handleHeartbeatAck(ctx);
                    break;
                }
                case DATA: {
                    handleData(ctx, message);
                    break;
                }
                case DATA_ACK: {
                    handleDataAck(ctx, message);
                    break;
                }
                case ERROR: {
                    handleError(ctx, message);
                    break;
                }
                case CONNECT: {
                    handleConnect(ctx, message);
                    break;
                }
                case DISCONNECT: {
                    handleDisconnect(ctx, message);
                    break;
                }
                default: log.warn("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            log.error("Error handling message: {}", type, e);
            ctx.fireExceptionCaught(e);
        }
    }
}
