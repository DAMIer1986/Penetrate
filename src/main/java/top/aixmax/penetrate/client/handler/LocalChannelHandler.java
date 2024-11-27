package top.aixmax.penetrate.client.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import top.aixmax.penetrate.client.config.PortMapping;
import top.aixmax.penetrate.client.manager.PortMappingManager;
import top.aixmax.penetrate.core.protocol.MessageFactory;

import java.nio.ByteBuffer;

/**
 * @author wangxu
 * @version 1.0 2024/11/16 20:00
 * @description
 */
@Slf4j
public class LocalChannelHandler extends ChannelInboundHandlerAdapter {
    private final PortMapping portMapping;
    private final Channel serverChannel;
    private final PortMappingManager portMappingManager;
    private final String channelId;

    public LocalChannelHandler(PortMapping portMapping,
                               Channel serverChannel,
                               PortMappingManager portMappingManager) {
        this.portMapping = portMapping;
        this.serverChannel = serverChannel;
        this.portMappingManager = portMappingManager;
        this.channelId = generateChannelId();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        portMappingManager.addConnection(portMapping.getLocalPort(), ctx.channel());
        log.debug("New local connection established for port {}", portMapping.getLocalPort());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        try {
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);

            // 构建数据包
            ByteBuffer buffer = ByteBuffer.allocate(8 + data.length);
            buffer.putInt(portMapping.getRemotePort()); // 远程端口
            buffer.putInt(data.length);
            buffer.put(data);

            serverChannel.writeAndFlush(MessageFactory.createDataMessage(buffer.array()));
        } finally {
            buf.release();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        portMappingManager.removeConnection(portMapping.getLocalPort(), ctx.channel());
        log.debug("Local connection closed for port {}", portMapping.getLocalPort());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            log.debug("Channel idle timeout, closing connection for port {}",
                    portMapping.getLocalPort());
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Error in local connection for port {}",
                portMapping.getLocalPort(), cause);
        ctx.close();
    }

    private String generateChannelId() {
        return String.format("%d-%d", portMapping.getLocalPort(),
                System.nanoTime());
    }
}
