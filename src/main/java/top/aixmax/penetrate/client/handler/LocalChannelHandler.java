package top.aixmax.penetrate.client.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import top.aixmax.penetrate.client.config.PortMapping;
import top.aixmax.penetrate.client.manager.PortMappingManager;
import top.aixmax.penetrate.common.enums.MessageType;
import top.aixmax.penetrate.core.protocol.Message;
import top.aixmax.penetrate.core.protocol.MessageFactory;

import java.nio.ByteBuffer;

/**
 * @author wangxu
 * @version 1.0 2024/11/16 20:00
 * @description
 */
@Slf4j
public class LocalChannelHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private final PortMapping portMapping;

    private final Channel serverChannel;

    private final PortMappingManager portMappingManager;

    private final Integer serverChannelId;

    public LocalChannelHandler(PortMapping portMapping,
                               Channel serverChannel,
                               PortMappingManager portMappingManager,
                               Integer serverChannelId) {
        this.portMapping = portMapping;
        this.serverChannel = serverChannel;
        this.portMappingManager = portMappingManager;
        this.serverChannelId = serverChannelId;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);

        // 构建数据包
        Message serverMsg = new Message();
        serverMsg.setExternalPort(portMapping.getRemotePort());
        serverMsg.setType(MessageType.DATA);
        serverMsg.setChannelId(serverChannelId);
        serverMsg.setData(data);
        serverChannel.writeAndFlush(Unpooled.copiedBuffer(serverMsg.getBytes()));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        portMappingManager.removeConnection(portMapping.getLocalPort() + "+" + serverChannelId);
        log.debug("Local connection closed for port {}", portMapping.getLocalPort());
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Error in local connection for port {}", portMapping.getLocalPort(), cause);
        portMappingManager.removeConnection(portMapping.getLocalPort() + "+" + serverChannelId);
        ctx.close();
    }

}
