package top.aixmax.penetrate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import top.aixmax.penetrate.client.config.PortMapping;

import java.util.List;

/**
 * @author wangxu
 * @version 1.0 2024/11/16 18:10
 * @description
 */
@Data
@ConfigurationProperties(prefix = "nat.client")
public class ClientConfig {
    /**
     * 是否启用客户端
     */
    private boolean enabled = false;

    private String serverHost = "localhost";
    private int serverPort = 7000;
    private int heartbeatInterval = 30;
    private int retryInterval = 5;
    private int maxRetryTimes = 3;
    private int connectTimeout = 5000;
    private int workerThreads = 4;
    private boolean enableSsl = false;
    private String clientId;
    private String secretKey;

    // 端口映射配置
    private List<PortMapping> portMappings;
}
