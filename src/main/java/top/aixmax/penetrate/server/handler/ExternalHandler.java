package top.aixmax.penetrate.server.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import top.aixmax.penetrate.server.manager.ClientManager;

/**
 * @author wangxu
 * @version 1.0 2024/11/16 23:19
 * @description
 */
@Slf4j
@ChannelHandler.Sharable
public class ExternalHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private final ClientManager clientManager;

    private final int port;

    public ExternalHandler(ClientManager clientManager, int port) {
        this.clientManager = clientManager;
        this.port = port;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // 处理新的外部连接
        log.debug("New external connection from: {}", ctx.channel().remoteAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        // 处理外部请求数据
        // 将数据转发给对应的客户端
        clientManager.handleExternalData(ctx.channel(), msg, port);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // 处理连接断开
        log.debug("External connection closed: {}", ctx.channel().remoteAddress());
        clientManager.handleExternalDisconnect(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Error in external connection", cause);
        ctx.close();
    }
}
