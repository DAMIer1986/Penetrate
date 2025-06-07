package top.aixmax.penetrate.server.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import top.aixmax.penetrate.server.manager.ClientManager;

/**
 * 外部连接处理器
 * 处理来自外部客户端的连接请求和数据传输
 *
 * @author wangxu
 * @version 1.0 2024/11/16 23:19
 */
@Slf4j
@ChannelHandler.Sharable
public class ExternalHandler extends SimpleChannelInboundHandler<ByteBuf> {
    /** 客户端管理器，用于管理客户端连接和数据转发 */
    private final ClientManager clientManager;

    /** 外部服务端口 */
    private final int port;

    /**
     * 构造函数
     * @param clientManager 客户端管理器
     * @param port 外部服务端口
     */
    public ExternalHandler(ClientManager clientManager, int port) {
        this.clientManager = clientManager;
        this.port = port;
    }

    /**
     * 处理新的外部连接
     * 当有新的外部客户端连接时，发送空数据包通知内部客户端建立连接
     * @param ctx 通道上下文
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // 处理新的外部连接
        log.info("New external connection from: {} for port {}", ctx.channel().remoteAddress(), port);
        // 发送空数据包通知客户端有新连接
        clientManager.handleExternalData(ctx.channel(), Unpooled.EMPTY_BUFFER, port);
    }

    /**
     * 处理接收到的数据
     * 将外部客户端的数据转发给内部客户端
     * @param ctx 通道上下文
     * @param msg 接收到的数据
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        // 处理外部请求数据
        if (msg.readableBytes() > 0) {
            log.debug("Received {} bytes from external connection on port {}", msg.readableBytes(), port);
            clientManager.handleExternalData(ctx.channel(), msg, port);
        }
    }

    /**
     * 处理连接断开
     * 当外部连接断开时，通知内部客户端关闭对应的连接
     * @param ctx 通道上下文
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // 处理连接断开
        log.info("External connection closed: {} for port {}", ctx.channel().remoteAddress(), port);
        clientManager.handleExternalDisconnect(ctx.channel());
    }

    /**
     * 处理异常
     * 当发生异常时，记录错误并关闭连接
     * @param ctx 通道上下文
     * @param cause 异常信息
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Error in external connection for port {}: {}", port, cause.getMessage());
        ctx.close();
    }
}
