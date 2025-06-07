package top.aixmax.penetrate.client.manager;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;
import top.aixmax.penetrate.client.config.PortMapping;
import top.aixmax.penetrate.client.handler.LocalChannelHandler;
import top.aixmax.penetrate.config.ClientConfig;
import top.aixmax.penetrate.core.protocol.Message;

import javax.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * @author wangxu
 * @version 1.0 2024/11/16 19:59
 * @description
 */
@Slf4j
public class PortMappingManager {

    private final ClientConfig config;

    private final EventLoopGroup group;

    private final Map<String, Channel> localConnections = new ConcurrentHashMap<>();

    private final Set<Integer> localPorts = new ConcurrentSkipListSet<>();

    private final Set<Integer> externalPorts = new ConcurrentSkipListSet<>();

    private final Map<Integer, PortMapping> portMappingMap = new ConcurrentHashMap<>();

    private Channel serverChannel;

    public PortMappingManager(ClientConfig config) {
        int processors = Runtime.getRuntime().availableProcessors();
        this.config = config;
        this.group = new NioEventLoopGroup(processors * 2);
        // 初始化端口映射
        initializePortMappings();
    }

    public void setServerChannel(Channel serverChannel) {
        this.serverChannel = serverChannel;
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
    public boolean canAcceptNewConnection(String localKey) {
        Channel connections = localConnections.get(localKey);
        return connections == null;
    }

    /**
     * 添加新的连接
     */
    public void addConnection(String localKey, Channel channel) {
        if (!canAcceptNewConnection(localKey)) {
            return;
        }
        localConnections.put(localKey, channel);

        log.debug("Added new connection for port {}", localKey);
    }

    /**
     * 移除连接
     */
    public void removeConnection(String localKey) {
        Channel channel = localConnections.remove(localKey);
        if (channel != null) {
            channel.close();
            log.debug("Removed connection for port {}", localKey);
        }
    }

    /**
     * 启动单个端口映射
     */
    private Channel startMapping(PortMapping mapping,int serverChannelId) {
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .option(ChannelOption.SO_RCVBUF, 1048576) // 1M
                    .option(ChannelOption.SO_SNDBUF, 1048576)
                    .handler(new LocalChannelHandler(mapping, serverChannel, this, serverChannelId));

            ChannelFuture future = bootstrap.connect(mapping.getLocalHost(), mapping.getLocalPort()).sync();
            Channel channel = future.channel();

            log.info("Started port mapping: {} -> {}",
                    mapping.getLocalPort(), mapping.getRemotePort());
            return channel;
        } catch (Exception e) {
            log.error("Failed to start port mapping: {} -> {}",
                    mapping.getLocalPort(), mapping.getRemotePort(), e);
            throw new RuntimeException("Failed to start port mapping " +
                    mapping.getLocalPort() + " ->" + mapping.getRemotePort());
        }
    }


    /**
     * 初始化本地端口
     */
    private void initializePortMappings() {
        if (config.getPortMappings() == null) {
            return;
        }
        for (PortMapping mapping : config.getPortMappings()) {
            if (!mapping.getEnabled()) {
                continue;
            }
            validatePortMapping(mapping);
            portMappingMap.put(mapping.getRemotePort(), mapping);
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
        if (localPorts.contains(mapping.getLocalPort()) || externalPorts.contains(mapping.getRemotePort())) {
            throw new IllegalArgumentException("Port repeat :" + mapping.getLocalPort());
        }
        localPorts.add(mapping.getLocalPort());
        externalPorts.add(mapping.getRemotePort());
    }

    public int mappingPort(int externalPort) {
        PortMapping mapping = portMappingMap.get(externalPort);
        if (mapping == null) {
            log.warn("None Register External Port {}", externalPort);
            throw new RuntimeException("None Register External Port " + externalPort);
        }
        return mapping.getLocalPort();
    }


    /**
     * 处理来自服务器的数据
     */
    public void handleIncomingData(Message msg) {
        // 解析数据包头部，获取目标端口和数据
        try {
            // 解析端口和数据
            int localPort = mappingPort(msg.getExternalPort());
            int serverChannelId = msg.getChannelId();

            log.debug("Write Data : {}--{}", serverChannelId, msg.getData().length);

            // 获取对应的本地连接并转发数据
            Channel localChannel = localConnections.get(localPort + "+" + serverChannelId);
            if (localChannel != null) {
                if (localChannel.isActive()) {
                    localChannel.writeAndFlush(Unpooled.wrappedBuffer(msg.getData()));
                    return;
                } else {
                    localChannel.close();
                }
            }

            localChannel = startMapping(portMappingMap.get(msg.getExternalPort()), serverChannelId);
            localChannel.writeAndFlush(Unpooled.wrappedBuffer(msg.getData()));

            localConnections.put(localPort + "+" + serverChannelId, localChannel);
        } catch (Exception e) {
            log.error("Error handling incoming data", e);
        }
    }

    /**
     * 处理连接断开事件
     */
    public void handleDisconnect() {
        // 清理所有本地连接
        localConnections.values().forEach(Channel::close);
        localConnections.clear();
    }

    @PreDestroy
    public void destroy() {
        // 关闭所有本地连接
        localConnections.values().forEach(Channel::close);
        localConnections.clear();

        // 关闭线程组
        group.shutdownGracefully();
    }

}