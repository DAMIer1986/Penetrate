package top.aixmax.penetrate.client.manager;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.aixmax.penetrate.client.config.PortMapping;
import top.aixmax.penetrate.common.utils.ByteUtils;
import top.aixmax.penetrate.config.ClientConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author wangxu
 * @version 1.0 2024/11/16 19:59
 * @description
 */
@Slf4j
@Component
public class PortMappingManager {
    private final ClientConfig config;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;
    private final Map<Integer, Channel> localServers = new ConcurrentHashMap<>();
    private final Map<Integer, List<Channel>> localConnections = new ConcurrentHashMap<>();
    private Channel clientChannel;

    public PortMappingManager(ClientConfig config) {
        this.config = config;
        this.bossGroup = new NioEventLoopGroup(1);
        this.workerGroup = new NioEventLoopGroup();
    }

    @PostConstruct
    public void init() {
        if (!config.isEnabled()) {
            return;
        }
        startAll();
    }

    public void setClientChannel(Channel clientChannel) {
        this.clientChannel = clientChannel;
    }

    /**
     * 获取所有端口映射
     */
    public List<PortMapping> getMappings() {
        return config.getPortMappings();
    }

    /**
     * 检查是否可以接受新的连接
     */
    public boolean canAcceptNewConnection(int localPort) {
        PortMapping mapping = getMapping(localPort);
        if (mapping == null) {
            return false;
        }

        List<Channel> connections = localConnections.get(localPort);
        if (connections == null) {
            return true;
        }

        // 清理已关闭的连接
        connections.removeIf(channel -> !channel.isActive());

        // 检查是否超过最大连接数
        return connections.size() < mapping.getMaxConnections();
    }

    /**
     * 获取指定本地端口的映射
     */
    public PortMapping getMapping(int localPort) {
        return config.getPortMappings().stream()
                .filter(mapping -> mapping.getLocalPort() == localPort)
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取指定端口的当前连接数
     */
    public int getCurrentConnections(int localPort) {
        List<Channel> connections = localConnections.get(localPort);
        if (connections == null) {
            return 0;
        }
        // 清理已关闭的连接
        connections.removeIf(channel -> !channel.isActive());
        return connections.size();
    }

    /**
     * 添加新的连接
     */
    public boolean addConnection(int localPort, Channel channel) {
        if (!canAcceptNewConnection(localPort)) {
            return false;
        }

        List<Channel> connections = localConnections.computeIfAbsent(
                localPort, k -> new ArrayList<>());
        connections.add(channel);

        log.debug("Added new connection for port {}, current connections: {}",
                localPort, connections.size());
        return true;
    }

    /**
     * 移除连接
     */
    public void removeConnection(int localPort, Channel channel) {
        List<Channel> connections = localConnections.get(localPort);
        if (connections != null) {
            connections.remove(channel);
            log.debug("Removed connection for port {}, remaining connections: {}",
                    localPort, connections.size());
        }
    }

    /**
     * 启动所有端口映射
     */
    public void startAll() {
        for (PortMapping mapping : config.getPortMappings()) {
            if (mapping.isEnabled()) {
                startMapping(mapping);
            }
        }
    }

    /**
     * 注册端口映射
     */
    public void registerMapping(PortMapping mapping) {
        if (mapping == null || !mapping.isEnabled()) {
            return;
        }

        // 检查端口是否已经被使用
        if (localServers.containsKey(mapping.getLocalPort())) {
            log.warn("Port {} is already mapped", mapping.getLocalPort());
            return;
        }

        // 启动映射
        startMapping(mapping);
    }

    /**
     * 取消注册端口映射
     */
    public void unregisterMapping(int localPort) {
        Channel serverChannel = localServers.remove(localPort);
        if (serverChannel != null) {
            serverChannel.close();
        }

        List<Channel> connections = localConnections.remove(localPort);
        if (connections != null) {
            connections.forEach(Channel::close);
        }

        log.info("Unregistered port mapping for local port: {}", localPort);
    }

    /**
     * 启动单个端口映射
     */
    private void startMapping(PortMapping mapping) {
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.AUTO_READ, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new LocalHandler(mapping));
                        }
                    });

            Channel serverChannel = bootstrap.bind(mapping.getLocalPort()).sync().channel();
            localServers.put(mapping.getLocalPort(), serverChannel);
            localConnections.putIfAbsent(mapping.getLocalPort(), new ArrayList<>());

            log.info("Started port mapping: {} -> {}",
                    mapping.getLocalPort(), mapping.getRemotePort());
        } catch (Exception e) {
            log.error("Failed to start port mapping: {} -> {}",
                    mapping.getLocalPort(), mapping.getRemotePort(), e);
        }
    }

    /**
     * 检查端口映射是否已注册
     */
    public boolean isMappingRegistered(int localPort) {
        return localServers.containsKey(localPort);
    }

    /**
     * 获取已注册的端口映射数量
     */
    public int getRegisteredMappingCount() {
        return localServers.size();
    }

    /**
     * 获取指定端口的连接数
     */
    public int getConnectionCount(int localPort) {
        List<Channel> connections = localConnections.get(localPort);
        return connections != null ? connections.size() : 0;
    }



    /**
     * 处理来自服务器的数据
     */
    public void handleIncomingData(byte[] data) {
        // 解析数据包头部，获取目标端口和数据
        if (data.length < 8) {
            log.warn("Invalid data packet: too short");
            return;
        }

        try {
            // 解析端口和数据
            int localPort = ByteUtils.bytesToInt(data, 0);
            int dataLength = ByteUtils.bytesToInt(data, 4);

            if (data.length < 8 + dataLength) {
                log.warn("Invalid data packet: incomplete data");
                return;
            }

            byte[] payload = new byte[dataLength];
            System.arraycopy(data, 8, payload, 0, dataLength);

            // 获取对应的本地连接并转发数据
            List<Channel> channels = localConnections.get(localPort);
            if (channels != null && !channels.isEmpty()) {
                // 这里可以根据需要选择转发策略，当前简单地发送给第一个连接
                Channel localChannel = channels.get(0);
                if (localChannel.isActive()) {
                    localChannel.writeAndFlush(payload);
                } else {
                    channels.remove(localChannel);
                }
            }
        } catch (Exception e) {
            log.error("Error handling incoming data", e);
        }
    }

    /**
     * 处理连接断开事件
     */
    public void handleDisconnect() {
        // 清理所有本地连接
        localConnections.values().forEach(channels ->
                channels.forEach(Channel::close));
        localConnections.clear();

        // 标记客户端通道为null
        clientChannel = null;
    }

    @PreDestroy
    public void destroy() {
        // 关闭所有本地服务器
        localServers.values().forEach(Channel::close);
        localServers.clear();

        // 关闭所有本地连接
        localConnections.values().forEach(channels ->
                channels.forEach(Channel::close));
        localConnections.clear();

        // 关闭线程组
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }

    /**
     * 本地连接处理器
     */
    @ChannelHandler.Sharable
    private class LocalHandler extends ChannelInboundHandlerAdapter {
        private final PortMapping mapping;

        public LocalHandler(PortMapping mapping) {
            this.mapping = mapping;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            if (addConnection(mapping.getLocalPort(), ctx.channel())) {
                log.debug("New connection accepted for port {}", mapping.getLocalPort());
            } else {
                log.warn("Connection rejected for port {}: max connections reached",
                        mapping.getLocalPort());
                ctx.close();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            removeConnection(mapping.getLocalPort(), ctx.channel());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("Error in local connection", cause);
            ctx.close();
        }
    }

}