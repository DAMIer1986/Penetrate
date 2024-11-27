package top.aixmax.penetrate.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author wangxu
 * @version 1.0 2024/11/16 20:04
 * @description
 */
@Data
@ConfigurationProperties(prefix = "nat.server")
public class ServerConfig {
    /**
     * 是否启用服务端
     */
    private boolean enabled = false;

    /**
     * 客户端连接端口
     */
    private int clientPort = 61927;

    /**
     * 最大连接数
     */
    private int maxConnections = 1000;

    /**
     * 是否启用SSL
     */
    private boolean enableSsl = false;

    /**
     * 读空闲超时（秒）
     */
    private int readIdleTime = 60;

    /**
     * 写空闲超时（秒）
     */
    private int writeIdleTime = 30;

    /**
     * Boss线程数
     */
    private int bossThreads = 1;

    /**
     * Worker线程数
     */
    private int workerThreads = Runtime.getRuntime().availableProcessors() * 2;

    /**
     * SSL证书路径
     */
    private String certPath;

    /**
     * SSL私钥路径
     */
    private String keyPath;

    /**
     * 最大帧长度
     */
    private int maxFrameLength = 16777216; // 16MB

    /**
     * 连接认证超时时间(秒)
     */
    private int authTimeout = 30;

    /**
     * 是否启用访问控制
     */
    private boolean enableAccessControl = false;

    /**
     * 允许的客户端ID列表
     */
    private String[] allowedClientIds = new String[0];

    /**
     * 是否启用流量控制
     */
    private boolean enableTrafficControl = false;

    /**
     * 每个客户端的最大带宽（字节/秒）
     */
    private long maxBytesPerSecond = 1024 * 1024; // 1MB/s
}
