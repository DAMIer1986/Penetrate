package top.aixmax.penetrate.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import top.aixmax.penetrate.common.constants.ProtocolConstants;
import top.aixmax.penetrate.server.config.ServerConfig;
import top.aixmax.penetrate.server.handler.ServerChannelHandler;
import top.aixmax.penetrate.server.manager.ClientManager;


/**
 * @author wangxu
 * @version 1.0 2024/11/16 20:07
 * @description
 */
@Slf4j
public class NatServer {
    private final ServerConfig config;
    private final ClientManager clientManager;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private Channel clientServerChannel;

    public NatServer(ServerConfig config) {
        int processors = Runtime.getRuntime().availableProcessors();
        this.config = config;
        this.clientManager = new ClientManager(config);
        this.bossGroup = new EpollEventLoopGroup(1);
        this.workerGroup = new EpollEventLoopGroup(processors);
    }

    @PostConstruct
    public void start() {
        if (!config.isEnabled()) {
            log.info("NAT server is disabled");
            return;
        }

        // 新建独立的监听线程，监听线程自我恢复
        new Thread(() -> {
            try {
                // 启动客户端监听服务器
                startClientServer();
                log.info("NAT server started successfully");
            } catch (Exception e) {
                log.error("Failed to start NAT server", e);
                throw new RuntimeException("Failed to start NAT server", e);
            }
        }).start();
    }

    /**
     * 启动客户端服务
     */
    private void startClientServer() {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(EpollServerSocketChannel.class)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.SO_RCVBUF, 1048576) // 1MB 发送缓冲区
                .option(ChannelOption.SO_BACKLOG, 128)
                .option(ChannelOption.MAX_MESSAGES_PER_WRITE, 1048576) // 1M
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.SO_SNDBUF, 1048576)
                .childHandler(new ServerChannelHandler(clientManager));

        while (true) {
            try {
                if (clientServerChannel == null) {
                    clientServerChannel = bootstrap.bind(config.getClientPort()).sync().channel();
                    log.info("Server Client Connect Port:{}", config.getClientPort());
                } else if (!clientServerChannel.isActive()) {
                    clientServerChannel.close().sync();
                    // 启动客户端监听服务器
                    clientServerChannel = bootstrap.bind(config.getClientPort()).sync().channel();
                }
                // 每5秒循环一次，确保接收线程存活
                Thread.sleep(ProtocolConstants.waitTime);
            } catch (Exception ex) {
                log.error("Failed to start NAT server", ex);
            }
        }
    }

    @PreDestroy
    public void stop() {
        log.info("Stopping NAT server...");
        try {
            if (clientServerChannel != null) {
                clientServerChannel.close().sync();
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while closing server channels", e);
            Thread.currentThread().interrupt();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            log.info("NAT server stopped");
        }
    }

    public boolean isRunning() {
        return (clientServerChannel != null && clientServerChannel.isActive());
    }
}