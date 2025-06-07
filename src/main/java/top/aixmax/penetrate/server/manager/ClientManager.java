package top.aixmax.penetrate.server.manager;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.CollectionUtils;
import top.aixmax.penetrate.client.config.PortMapping;
import top.aixmax.penetrate.common.constants.ProtocolConstants;
import top.aixmax.penetrate.common.enums.MessageType;
import top.aixmax.penetrate.core.protocol.Message;
import top.aixmax.penetrate.server.config.ServerConfig;
import top.aixmax.penetrate.server.model.ClientInfo;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 客户端管理器
 * 负责管理客户端连接、端口映射和数据转发
 *
 * @author wangxu
 * @version 1.0 2024/11/16 20:40
 */
@Slf4j
public class ClientManager {

    /** 服务器配置信息 */
    private final ServerConfig config;

    /** 客户端ID与客户端信息映射表 */
    private final Map<String, ClientInfo> clients = new ConcurrentHashMap<>();

    /** 远程端口到客户端信息列表的映射表 */
    private final Map<Integer, List<ClientInfo>> portClientMappings = new ConcurrentHashMap<>();

    /** 外部通道ID到端口映射信息的映射表 */
    private final Map<String, PortMappingInfo> externalChannels = new ConcurrentHashMap<>();

    /** 通道到客户端信息的映射表 */
    private final Map<Channel, ClientInfo> channelMap = new ConcurrentHashMap<>();

    /** 服务端外网通道映射表 */
    private final Map<String, Channel> serverChannelMap = new ConcurrentHashMap<>();

    /** 通道ID映射表 */
    private final Map<Integer, String> idMap = new ConcurrentHashMap<>();

    /** 通道ID生成器 */
    private final AtomicInteger channelIntId = new AtomicInteger(1000);

    /**
     * 构造函数
     * @param config 服务器配置
     */
    public ClientManager(ServerConfig config) {
        this.config = config;
    }

    /**
     * 处理外部请求数据
     * 将外部客户端的数据转发给内部客户端
     * @param externalChannel 外部通道
     * @param data 数据
     * @param port 端口号
     */
    public void handleExternalData(Channel externalChannel, ByteBuf data, int port) {
        String channelId = externalChannel.id().asLongText();
        int tempId = -1;
        if (!idMap.containsValue(channelId)) {
            tempId = this.channelIntId.addAndGet(1);
            idMap.put(tempId, channelId);
            serverChannelMap.put(channelId, externalChannel);
            log.debug("Created new channel mapping: {} -> {}", tempId, channelId);
        } else {
            for (Map.Entry<Integer, String> entry : idMap.entrySet()) {
                if (channelId.equals(entry.getValue())) {
                    tempId = entry.getKey();
                    break;
                }
            }
        }
        if (tempId == -1) {
            log.error("Failed to get channel ID for external channel");
            return;
        }

        List<ClientInfo> clientInfos = portClientMappings.get(port);
        if (CollectionUtils.isEmpty(clientInfos)) {
            log.warn("No active client for port {}", port);
            externalChannel.close();
            return;
        }

        ClientInfo clientInfo = null;
        for (ClientInfo info : clientInfos) {
            if (info.isActive()) {
                clientInfo = info;
                break;
            }
        }

        if (clientInfo == null) {
            log.warn("No active client found for port {}", port);
            return;
        }

        // 构建数据包
        byte[] payload = new byte[data.readableBytes()];
        data.readBytes(payload);

        // 数据包格式：
        Message msg = new Message();
        msg.setType(MessageType.DATA);
        msg.setExternalPort(port);
        msg.setChannelId(tempId);
        msg.setData(payload);

        // 发送数据到客户端
        clientInfo.getChannel().writeAndFlush(
                Unpooled.wrappedBuffer(msg.getBytes())
        );

        log.debug("Forwarded {} bytes to client {} for port {} with channel ID {}",
                payload.length, clientInfo.getClientId(), port, tempId);
    }

    /**
     * 处理外部连接断开
     * 通知内部客户端关闭对应的连接
     * @param externalChannel 外部通道
     */
    public void handleExternalDisconnect(Channel externalChannel) {
        String channelId = externalChannel.id().asLongText();
        Integer tempId = null;
        
        // 查找对应的临时ID
        for (Map.Entry<Integer, String> entry : idMap.entrySet()) {
            if (channelId.equals(entry.getValue())) {
                tempId = entry.getKey();
                break;
            }
        }

        if (tempId != null) {
            // 查找对应的端口映射信息
            for (Map.Entry<Integer, List<ClientInfo>> entry : portClientMappings.entrySet()) {
                int port = entry.getKey();
                List<ClientInfo> clientInfos = entry.getValue();
                
                if (!CollectionUtils.isEmpty(clientInfos)) {
                    for (ClientInfo clientInfo : clientInfos) {
                        if (clientInfo != null && clientInfo.isActive()) {
                            // 发送连接断开通知到客户端
                            Message msg = new Message();
                            msg.setType(MessageType.DATA);
                            msg.setExternalPort(port);
                            msg.setChannelId(tempId);
                            msg.setData(new byte[0]); // 空数据表示断开连接

                            clientInfo.getChannel().writeAndFlush(
                                    Unpooled.wrappedBuffer(msg.getBytes())
                            );

                            log.info("Notified client {} about disconnection for port {} with channel ID {}",
                                    clientInfo.getClientId(), port, tempId);
                        }
                    }
                }
            }

            // 清理映射
            idMap.remove(tempId);
            serverChannelMap.remove(channelId);
        }
    }

    /**
     * 获取外部服务通道
     * @param channelIntId 通道ID
     * @return 外部服务通道
     */
    public Channel getServerChannel(Integer channelIntId) {
        return serverChannelMap.get(idMap.get(channelIntId));
    }

    /**
     * 端口映射信息内部类
     */
    @Data
    private static class PortMappingInfo {
        /** 远程端口 */
        private final int remotePort;
        /** 创建时间 */
        private final long createTime;

        /**
         * 构造函数
         * @param remotePort 远程端口
         */
        public PortMappingInfo(int remotePort) {
            this.remotePort = remotePort;
            this.createTime = System.currentTimeMillis();
        }
    }

    /**
     * 注册客户端
     * @param info 客户端信息
     * @param channel 通道
     * @return 是否注册成功
     */
    public boolean registerClient(ClientInfo info, Channel channel) {
        // 检查是否已存在
        ClientInfo existingClient = clients.get(info.getClientId());
        if (existingClient != null) {
            if (existingClient.getChannel().isActive()) {
                log.warn("Client {} already registered and active", info.getClientId());
                return false;
            }
            // 如果已存在但不活跃，先移除旧的
            unregisterClient(existingClient.getChannel());
        }

        clients.put(info.getClientId(), info);
        channelMap.put(channel, info);
        info.getPortMappings().forEach((portInfo) -> {
            List<ClientInfo> clientInfoList = portClientMappings
                    .computeIfAbsent(portInfo.getRemotePort(), p -> new CopyOnWriteArrayList<>());
            clientInfoList.add(info);
            clientInfoList.sort(Comparator.comparingInt(ClientInfo::getSort));
        });
        log.info("Client registered: {}", info.getClientId());
        return true;
    }

    /**
     * 注销客户端
     * @param channel 通道
     * @return 被注销的客户端信息
     */
    public ClientInfo unregisterClient(Channel channel) {
        ClientInfo info = channelMap.remove(channel);
        if (info != null) {
            clients.remove(info.getClientId());
            portClientMappings.forEach((port, clients) -> clients.remove(info));
            log.info("Client unregistered: {}", info.getClientId());
        }
        return info;
    }

    /**
     * 通过通道获取客户端信息
     * @param channel 通道
     * @return 客户端信息
     */
    public ClientInfo getClientByChannel(Channel channel) {
        return channelMap.get(channel);
    }

    /**
     * 定时清理不活跃的客户端
     */
    @Scheduled(fixedRate = 60000) // 每分钟执行一次
    public void cleanInactiveClients() {
        LocalDateTime now = LocalDateTime.now();
        clients.entrySet().removeIf(entry -> {
            ClientInfo client = entry.getValue();
            Channel channel = client.getChannel();

            // 检查通道是否活跃
            if (!channel.isActive()) {
                log.info("Removing inactive client: {}", entry.getKey());
                channelMap.remove(channel);
                return true;
            }

            // 检查最后心跳时间
            if (ChronoUnit.SECONDS.between(client.getLastHeartbeatTime(), now) > config.getReadIdleTime()) {
                log.info("Removing client due to heartbeat timeout: {}", entry.getKey());
                channel.close();
                channelMap.remove(channel);
                return true;
            }
            return false;
        });
    }

    /**
     * 客户端统计信息类
     */
    @Data
    public static class ClientStatistics {
        private final String clientId;
        private final LocalDateTime connectTime;
        private final LocalDateTime lastHeartbeatTime;
        private final long totalRequests;
        private final long totalBytes;
        private final int activePortMappings;
        private final boolean isActive;

        public ClientStatistics(ClientInfo clientInfo) {
            this.clientId = clientInfo.getClientId();
            this.connectTime = clientInfo.getConnectTime();
            this.lastHeartbeatTime = clientInfo.getLastHeartbeatTime();
            this.totalRequests = clientInfo.getTotalRequests().get();
            this.totalBytes = clientInfo.getTotalBytes().get();
            this.activePortMappings = clientInfo.getPortMappings().size();
            this.isActive = clientInfo.getChannel().isActive();
        }
    }

}
