package top.aixmax.penetrate.server.model;

import io.netty.channel.Channel;
import lombok.Data;
import top.aixmax.penetrate.client.config.PortMapping;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author wangxu
 * @version 1.0 2024/11/16 21:30
 * @description
 */
@Data
public class ClientInfo {
    /**
     * 客户端ID
     */
    private String clientId;

    /**
     * 客户端通道
     */
    private Channel channel;

    /**
     * 连接时间
     */
    private LocalDateTime connectTime;

    /**
     * 最后心跳时间
     */
    private LocalDateTime lastHeartbeatTime;

    /**
     * 总请求数
     */
    private AtomicLong totalRequests;

    /**
     * 总传输字节数
     */
    private AtomicLong totalBytes;

    /**
     * 端口映射信息
     */
    private List<PortMapping> portMappings;

    /**
     * 客户端版本
     */
    private String version;

    /**
     * 客户端操作系统
     */
    private String osInfo;

    /**
     * 排序
     */
    private int sort = 0;

    /**
     * 额外属性
     */
    private final Map<String, String> attributes;

    public ClientInfo(String clientId, Channel channel) {
        this.clientId = clientId;
        this.channel = channel;
        this.connectTime = LocalDateTime.now();
        this.lastHeartbeatTime = LocalDateTime.now();
        this.totalRequests = new AtomicLong(0);
        this.totalBytes = new AtomicLong(0);
        this.portMappings = new CopyOnWriteArrayList<>();
        this.attributes = new ConcurrentHashMap<>();
    }

    /**
     * 更新心跳时间
     */
    public void updateHeartbeat() {
        this.lastHeartbeatTime = LocalDateTime.now();
    }

    /**
     * 增加请求计数
     */
    public void incrementRequests() {
        totalRequests.incrementAndGet();
    }

    /**
     * 添加传输字节数
     */
    public void addBytes(long bytes) {
        totalBytes.addAndGet(bytes);
    }

    /**
     * 设置客户端版本
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * 设置操作系统信息
     */
    public void setOsInfo(String osInfo) {
        this.osInfo = osInfo;
    }

    /**
     * 添加属性
     */
    public void setAttribute(String key, String value) {
        attributes.put(key, value);
    }

    /**
     * 获取属性
     */
    public String getAttribute(String key) {
        return attributes.get(key);
    }

    /**
     * 移除属性
     */
    public void removeAttribute(String key) {
        attributes.remove(key);
    }

    /**
     * 获取连接时长（秒）
     */
    public long getConnectionDuration() {
        return java.time.Duration.between(connectTime, LocalDateTime.now()).getSeconds();
    }

    /**
     * 获取最后心跳间隔（秒）
     */
    public long getLastHeartbeatInterval() {
        return java.time.Duration.between(lastHeartbeatTime, LocalDateTime.now()).getSeconds();
    }

    /**
     * 检查客户端是否活跃
     */
    public boolean isActive() {
        return channel != null && channel.isActive();
    }

    /**
     * 获取客户端地址
     */
    public String getRemoteAddress() {
        return channel != null ? channel.remoteAddress().toString() : "Unknown";
    }

    /**
     * 获取活跃端口映射数量
     */
    public int getActivePortMappingCount() {
        return (int) portMappings.stream()
                .filter(PortMapping::getEnabled)
                .count();
    }

    /**
     * 获取总传输速率（字节/秒）
     */
    public long getTransferRate() {
        long duration = getConnectionDuration();
        return duration > 0 ? totalBytes.get() / duration : 0;
    }

    @Override
    public String toString() {
        return String.format("ClientInfo{clientId='%s', version='%s', connected=%s, portMappings=%d, " +
                        "requests=%d, bytes=%d}",
                clientId, version, isActive(), portMappings.size(),
                totalRequests.get(), totalBytes.get());
    }
}
