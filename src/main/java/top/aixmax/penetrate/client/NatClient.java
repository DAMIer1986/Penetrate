package top.aixmax.penetrate.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import top.aixmax.penetrate.client.config.PortMapping;
import top.aixmax.penetrate.client.handler.ClientHandler;
import top.aixmax.penetrate.client.manager.PortMappingManager;
import top.aixmax.penetrate.common.constants.ProtocolConstants;
import top.aixmax.penetrate.config.ClientConfig;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;

/**
 * @author wangxu
 * @version 1.0 2024/11/16 18:11
 * @description
 */
@Slf4j
public class NatClient {

    @Getter
    private final ClientConfig config;

    private final EventLoopGroup group;

    private final ClientHandler clientHandler;

    private volatile boolean running = true;

    public NatClient(ClientConfig config) {
        this.config = config;
        this.group = new NioEventLoopGroup(config.getWorkerThreads());
        // 创建一个共享的handler实例
        this.clientHandler = new ClientHandler(new PortMappingManager(config), config.getClientId());
        // 日志输出配置信息
        logConfiguration();
    }

    /**
     * 日志输出
     */
    private void logConfiguration() {
        log.info("Initializing NAT client with configuration:");
        log.info("Server: {}:{}", config.getServerHost(), config.getServerPort());
        log.info("Client ID: {}", config.getClientId());
        log.info("Heartbeat interval: {}s", config.getHeartbeatInterval());
        log.info("Port mappings:");
        if (config.getPortMappings() != null) {
            config.getPortMappings().stream().filter(PortMapping::getEnabled)
                    .forEach(mapping -> log.info("  {} -> {}",
                            mapping.getLocalPort(), mapping.getRemotePort()));
        }
    }

    @PostConstruct
    public void start() {
        if (!config.isEnabled()) {
            log.info("NAT client is disabled, skipping startup");
            return;
        }
        log.info("Starting NAT client...");
        // 连接至服务器
        connectToServer();
    }

    /**
     * 连接服务器
     */
    private void connectToServer() {
        if (!running) {
            return;
        }

        new Thread(() -> {
            while (true) {
                try {
                    Bootstrap bootstrap = new Bootstrap();
                    bootstrap.group(group)
                            .channel(NioSocketChannel.class)
                            .handler(clientHandler);

                    // Connect to the server
                    ChannelFuture future = bootstrap.connect(config.getServerHost(), config.getServerPort()).sync();
                    // Wait until the connection is closed
                    future.channel().closeFuture().sync();
                    Thread.sleep(ProtocolConstants.waitTime);
                } catch (Exception ex) {
                    group.shutdownGracefully();
                }
            }
        }).start();
    }

    /**
     * 重连服务器
     */
    public void scheduleReconnect() {
        if (!running) {
            return;
        }
        group.schedule(() -> {
            log.info("Attempting to reconnect...");
            connectToServer();
        }, config.getRetryInterval(), TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() {
        running = false;
        group.shutdownGracefully();
    }

}
