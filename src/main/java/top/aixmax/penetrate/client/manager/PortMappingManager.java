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
 * 端口映射管理器
 * 负责管理本地端口与远程端口的映射关系，处理数据的转发和连接的建立/断开
 *
 * @author wangxu
 * @version 1.0 2024/11/16 19:59
 */
@Slf4j
public class PortMappingManager {

    /** 客户端配置信息 */
    private final ClientConfig config;

    /** Netty事件循环组，用于处理异步IO操作 */
    private final EventLoopGroup group;

    /** 本地连接映射表，key为"本地端口+通道ID"，value为对应的Channel */
    private final Map<String, Channel> localConnections = new ConcurrentHashMap<>();

    /** 已使用的本地端口集合 */
    private final Set<Integer> localPorts = new ConcurrentSkipListSet<>();

    /** 已使用的外部端口集合 */
    private final Set<Integer> externalPorts = new ConcurrentSkipListSet<>();

    /** 端口映射配置表，key为远程端口，value为端口映射配置 */
    private final Map<Integer, PortMapping> portMappingMap = new ConcurrentHashMap<>();

    /** 与服务器的连接通道 */
    private Channel serverChannel;

    /**
     * 构造函数
     * @param config 客户端配置
     */
    public PortMappingManager(ClientConfig config) {
        int processors = Runtime.getRuntime().availableProcessors();
        this.config = config;
        this.group = new NioEventLoopGroup(processors * 2);
        // 初始化端口映射
        initializePortMappings();
    }

    /**
     * 设置服务器通道
     * @param serverChannel 服务器通道
     */
    public void setServerChannel(Channel serverChannel) {
        this.serverChannel = serverChannel;
    }

    /**
     * 获取所有端口映射配置
     * @return 端口映射配置列表
     */
    public List<PortMapping> getMappings() {
        return config.getPortMappings();
    }

    /**
     * 检查是否可以接受新的连接
     * @param localKey 本地连接键值（本地端口+通道ID）
     * @return 是否可以接受新连接
     */
    public boolean canAcceptNewConnection(String localKey) {
        Channel connections = localConnections.get(localKey);
        return connections == null;
    }

    /**
     * 添加新的连接
     * @param localKey 本地连接键值
     * @param channel 连接通道
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
     * @param localKey 本地连接键值
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
     * @param mapping 端口映射配置
     * @param serverChannelId 服务器通道ID
     * @return 创建的本地通道
     */
    private Channel startMapping(PortMapping mapping, int serverChannelId) {
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
     * 初始化本地端口映射
     * 从配置中读取端口映射信息并验证
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
     * 验证端口映射配置
     * @param mapping 端口映射配置
     * @throws IllegalArgumentException 如果配置无效
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

    /**
     * 根据外部端口获取对应的本地端口
     * @param externalPort 外部端口
     * @return 本地端口
     * @throws RuntimeException 如果外部端口未注册
     */
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
     * 根据消息中的外部端口和通道ID，将数据转发到对应的本地连接
     * @param msg 服务器消息
     */
    public void handleIncomingData(Message msg) {
        try {
            // 解析端口和数据
            int localPort = mappingPort(msg.getExternalPort());
            int serverChannelId = msg.getChannelId();
            String localKey = localPort + "+" + serverChannelId;

            log.debug("Received data for port {} with channel ID {}", localPort, serverChannelId);

            // 获取对应的本地连接
            Channel localChannel = localConnections.get(localKey);
            
            // 如果是新连接或连接已断开，创建新的本地连接
            if (localChannel == null || !localChannel.isActive()) {
                if (localChannel != null) {
                    localChannel.close();
                }
                localChannel = startMapping(portMappingMap.get(msg.getExternalPort()), serverChannelId);
                localConnections.put(localKey, localChannel);
                log.info("Created new local connection for port {} with channel ID {}", localPort, serverChannelId);
            }

            // 如果有数据，转发到本地连接
            if (msg.getData() != null && msg.getData().length > 0) {
                localChannel.writeAndFlush(Unpooled.wrappedBuffer(msg.getData()));
                log.debug("Forwarded {} bytes to local port {}", msg.getData().length, localPort);
            }
        } catch (Exception e) {
            log.error("Error handling incoming data", e);
        }
    }

    public void handleConnect(Message msg) {
        // 解析端口和数据
        int localPort = mappingPort(msg.getExternalPort());
        int serverChannelId = msg.getChannelId();

        log.debug("Write Data : {}--{}", serverChannelId, msg.getData().length);

        // 获取对应的本地连接并转发数据
        Channel localChannel = startMapping(portMappingMap.get(msg.getExternalPort()), serverChannelId);
        localConnections.put(localPort + "+" + serverChannelId, localChannel);
    }

    /**
     * 处理连接断开事件
     * 清理所有本地连接
     */
    public void handleDisconnect() {
        // 清理所有本地连接
        localConnections.forEach((key, channel) -> {
            try {
                channel.close();
                log.debug("Closed local connection for key: {}", key);
            } catch (Exception e) {
                log.error("Error closing local connection for key: {}", key, e);
            }
        });
        localConnections.clear();
    }

    /**
     * 销毁资源
     * 关闭所有本地连接和事件循环组
     */
    @PreDestroy
    public void destroy() {
        // 关闭所有本地连接
        localConnections.values().forEach(Channel::close);
        localConnections.clear();

        // 关闭线程组
        group.shutdownGracefully();
    }
}