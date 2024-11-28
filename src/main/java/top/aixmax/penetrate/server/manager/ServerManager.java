package top.aixmax.penetrate.server.manager;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import top.aixmax.penetrate.common.constants.ProtocolConstants;
import top.aixmax.penetrate.server.handler.ExternalHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author wangxu
 * @version 1.0 2024/11/26 10:32
 * @description
 */
@Slf4j
public class ServerManager {

    private final ClientManager clientManager;

    private final Map<Integer, ServerChannel> channelMap;

    public ServerManager(ClientManager clientManager) {
        this.clientManager = clientManager;
        this.channelMap = new ConcurrentHashMap<>();
    }

    /**
     * 启动外部端口监听
     *
     * @param externalPort 端口号
     */
    public void startExternalServer(int externalPort) {
        int processors = Runtime.getRuntime().availableProcessors();
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup(processors);
        ServerBootstrap bootstrap = new ServerBootstrap();

        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.SO_RCVBUF, 1048576) // 1MB 发送缓冲区
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.SO_SNDBUF, 1048576)
                .childHandler(new ExternalHandler(clientManager, externalPort));

        ServerChannel sc = null;
        while (true) {
            try {
                if (sc == null) {
                    sc = new ServerChannel(bossGroup, workerGroup, bootstrap.bind(externalPort).sync().channel(), externalPort);
                    log.info("External server listening on port {}", externalPort);
                } else if (!sc.channel.isActive()) {
                    sc.channel.close().sync();
                    // 启动客户端监听服务器
                    sc = new ServerChannel(bossGroup, workerGroup,
                            bootstrap.bind(externalPort).sync().channel(), externalPort);
                    log.info("External server re listening on port {}", externalPort);
                }
                channelMap.put(externalPort, sc);
                // 每5秒循环一次，确保接收线程存活
                Thread.sleep(ProtocolConstants.waitTime);
            } catch (Exception ex) {
                log.error("Failed to start NAT server", ex);
            }
        }
    }

    @PreDestroy
    public void stop(int externalPort) {
        log.info("Stopping NAT server...");
        ServerChannel sc = channelMap.remove(externalPort);
        try {
            sc.channel.closeFuture().sync();
        } catch (InterruptedException e) {
            log.warn("Interrupted while closing server channels", e);
            Thread.currentThread().interrupt();
        } finally {
            sc.bossGroup.shutdownGracefully();
            sc.workerGroup.shutdownGracefully();
            log.info("NAT server stopped");
        }
    }

    private record ServerChannel(EventLoopGroup bossGroup, EventLoopGroup workerGroup, Channel channel, int port) {
    }

}
