package top.aixmax.penetrate.client.handler;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;
import top.aixmax.penetrate.core.protocol.Message;
import top.aixmax.penetrate.core.protocol.MessageFactory;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author wangxu
 * @version 1.0 2024/11/16 18:13
 * @description
 */
@Slf4j
public class DataForwardHandler {
    private final EventLoopGroup group;
    private final Map<Integer, Channel> localConnections = new ConcurrentHashMap<>();
    private final Channel serverChannel;

    public DataForwardHandler(EventLoopGroup group, Channel serverChannel) {
        this.group = group;
        this.serverChannel = serverChannel;
    }

    public void handleData(Message msg) {
        ByteBuffer buffer = ByteBuffer.wrap(msg.getData());
        int localPort = buffer.getInt();
        int dataLength = buffer.getInt();
        byte[] data = new byte[dataLength];
        buffer.get(data);

        Channel localChannel = localConnections.get(localPort);
        if (localChannel != null && localChannel.isActive()) {
            localChannel.writeAndFlush(Unpooled.wrappedBuffer(data));
        } else {
            createLocalConnection(localPort, data);
        }
    }

    private void createLocalConnection(int localPort, byte[] initialData) {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new LocalChannelHandler(localPort));
                    }
                });

        bootstrap.connect("localhost", localPort)
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        Channel localChannel = future.channel();
                        localConnections.put(localPort, localChannel);
                        localChannel.writeAndFlush(Unpooled.wrappedBuffer(initialData));
                    } else {
                        log.error("Failed to connect to local port {}", localPort);
                        sendErrorResponse(localPort);
                    }
                });
    }

    private void sendErrorResponse(int localPort) {
        serverChannel.writeAndFlush(MessageFactory.createErrorMessage(
                "Failed to connect to local port: " + localPort));
    }

    private class LocalChannelHandler extends ChannelInboundHandlerAdapter {
        private final int localPort;

        public LocalChannelHandler(int localPort) {
            this.localPort = localPort;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf buf = (ByteBuf) msg;
            try {
                byte[] data = new byte[buf.readableBytes()];
                buf.readBytes(data);

                // 构建转发数据包
                ByteBuffer buffer = ByteBuffer.allocate(data.length + 8);
                buffer.putInt(localPort);
                buffer.putInt(data.length);
                buffer.put(data);

                serverChannel.writeAndFlush(MessageFactory.createDataMessage(buffer.array()));
            } finally {
                buf.release();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            localConnections.remove(localPort);
            log.debug("Local connection closed for port {}", localPort);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("Error in local connection for port {}", localPort, cause);
            ctx.close();
        }
    }
}
