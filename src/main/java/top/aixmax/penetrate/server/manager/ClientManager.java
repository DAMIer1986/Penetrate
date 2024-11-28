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
 * @author wangxu
 * @version 1.0 2024/11/16 20:40
 * @description
 */
@Slf4j
public class ClientManager {

    private final ServerConfig config;

    // 客户端ID与客户端映射
    private final Map<String, ClientInfo> clients = new ConcurrentHashMap<>();

    // 远程端口 -> 客户端信息
    private final Map<Integer, List<ClientInfo>> portClientMappings = new ConcurrentHashMap<>();
    // 外部Channel ID -> 端口映射信息
    private final Map<String, PortMappingInfo> externalChannels = new ConcurrentHashMap<>();

    private final Map<Channel, ClientInfo> channelMap = new ConcurrentHashMap<>();

    // 服务端外网管道映射
    private final Map<String, Channel> serverChannelMap = new ConcurrentHashMap<>();

    public ClientManager(ServerConfig config) {
        this.config = config;
    }

    private final Map<Integer, String> idMap = new ConcurrentHashMap<>();

    private final AtomicInteger channelIntId = new AtomicInteger(1000);

    /**
     * 处理外部请求数据
     */
    public void handleExternalData(Channel externalChannel, ByteBuf data, int port) {
        String channelId = externalChannel.id().asLongText();
        int tempId = -1;
        if (!idMap.containsValue(channelId)) {
            tempId = this.channelIntId.addAndGet(1);
            idMap.put(tempId, channelId);
            serverChannelMap.put(channelId, externalChannel);
        } else {
            for (Map.Entry<Integer, String> entry : idMap.entrySet()) {
                if (channelId.equals(entry.getValue())) {
                    tempId = entry.getKey();
                }
            }
        }
        if (tempId == -1) {
            return;
        }
        List<ClientInfo> clientInfos = portClientMappings.get(port);

        if (CollectionUtils.isEmpty(clientInfos)) {
            log.warn("No Client is active");
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
            log.warn("Non Client Active");
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

        log.debug("Forwarded {} bytes to client {} for port {} to {}",
                payload.length, clientInfo.getClientId(), 1, port);
    }

    /**
     * 处理外部连接断开
     */
    public void handleExternalDisconnect(Channel externalChannel) {
        String channelId = externalChannel.id().asLongText();
        PortMappingInfo mappingInfo = externalChannels.remove(channelId);

        if (mappingInfo != null) {
            List<ClientInfo> clientInfos = portClientMappings.get(mappingInfo.getRemotePort());
            if (!CollectionUtils.isEmpty(clientInfos)) {
                for (ClientInfo clientInfo : clientInfos) {
                    if (clientInfo != null && clientInfo.isActive()) {
                        // 发送连接断开通知到客户端
                        ByteBuffer buffer = ByteBuffer.allocate(16);
                        buffer.putInt(mappingInfo.getRemotePort());
                        buffer.putLong(Long.parseLong(channelId));
                        buffer.putInt(0); // 数据长度为0表示断开连接

                        clientInfo.getChannel().writeAndFlush(
                                Unpooled.wrappedBuffer(buffer.array())
                        );

                        log.debug("Notified client {} about disconnection for port {}",
                                clientInfo.getClientId(), mappingInfo.getRemotePort());
                    }
                }
            }
        }
    }

    /**
     * 获取外部服务管道
     *
     * @param channelIntId 管道ID
     * @return 外部服务管道
     */
    public Channel getServerChannel(Integer channelIntId) {
        return serverChannelMap.get(idMap.get(channelIntId));
    }

    // 内部类：端口映射信息
    @Data
    private static class PortMappingInfo {
        private final int remotePort;
        private final long createTime;

        public PortMappingInfo(int remotePort) {
            this.remotePort = remotePort;
            this.createTime = System.currentTimeMillis();
        }

    }

    /**
     * 注册客户端
     *
     * @param info    客户端信息
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
     *
     * @param channel 通道
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
     *
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
