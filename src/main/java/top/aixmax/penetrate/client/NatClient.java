package top.aixmax.penetrate.client;

import io.netty.bootstrap.Bootstrap;
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
import top.aixmax.penetrate.config.ClientConfig;

import java.util.concurrent.TimeUnit;

/**
 * @author wangxu
 * @version 1.0 2024/11/16 18:11
 * @description
 */
@Slf4j
public class NatClient {

    private PortMappingManager portMappingManager;

    @Getter
    private final ClientConfig config;

    private final EventLoopGroup group;

    private final ClientHandler clientHandler;

    @Getter
    private Channel channel;

    private volatile boolean running = true;

    public NatClient(ClientConfig config) {
        this.config = config;
        this.group = new NioEventLoopGroup(config.getWorkerThreads());
        this.portMappingManager = new PortMappingManager(config);
        // 创建一个共享的handler实例
        this.clientHandler = new ClientHandler(portMappingManager, config.getClientId());
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
            config.getPortMappings().stream().filter(PortMapping::isEnabled)
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

        // 初始化端口映射
        initializePortMappings();

        log.info("Starting NAT client...");
        connectToServer();
    }

    /**
     * 初始化本地端口
     */
    private void initializePortMappings() {
        if (config.getPortMappings() == null) {
            return;
        }

        for (PortMapping mapping : config.getPortMappings()) {
            if (!mapping.isEnabled()) {
                continue;
            }

            validatePortMapping(mapping);
            portMappingManager.registerMapping(mapping);
        }
    }

    /**
     * 检查端口映射
     *
     * @param mapping 端口映射配置
     */
    private void validatePortMapping(PortMapping mapping) {
        if (mapping.getLocalPort() <= 0 || mapping.getLocalPort() > 65535) {
            throw new IllegalArgumentException("Invalid local port: " + mapping.getLocalPort());
        }
        if (mapping.getRemotePort() <= 0 || mapping.getRemotePort() > 65535) {
            throw new IllegalArgumentException("Invalid remote port: " + mapping.getRemotePort());
        }
        if (!"tcp".equalsIgnoreCase(mapping.getProtocol()) && !"udp".equalsIgnoreCase(mapping.getProtocol())) {
            throw new IllegalArgumentException("Invalid protocol: " + mapping.getProtocol());
        }
    }

    /**
     * 连接服务器
     */
    private void connectToServer() {
        if (!running) {
            return;
        }

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeout())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        // 业务处理器
                        p.addLast(clientHandler);
                    }
                });

        bootstrap.connect(config.getServerHost(), config.getServerPort())
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        channel = future.channel();
                        log.info("Connected to server {}:{}", config.getServerHost(), config.getServerPort());
                    } else {
                        log.error("Failed to connect to server: {}", future.cause().getMessage());
                        scheduleReconnect();
                    }
                });
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
        if (channel != null) {
            channel.close();
        }
        group.shutdownGracefully();
    }

}
