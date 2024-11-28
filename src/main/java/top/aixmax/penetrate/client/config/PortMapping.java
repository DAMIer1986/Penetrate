package top.aixmax.penetrate.client.config;

import lombok.Data;

/**
 * @author wangxu
 * @version 1.0 2024/11/16 18:13
 * @description 客户端映射
 */
@Data
public class PortMapping {
    /**
     * 本地端口
     */
    private Integer localPort;

    /**
     * 远程端口
     */
    private Integer remotePort;

    /**
     * 协议类型：tcp/udp
     */
    private String protocol = "tcp";

    /**
     * 是否启用
     */
    private Boolean enabled = true;

    /**
     * 本地主机地址，默认localhost
     */
    private String localHost = "localhost";

    /**
     * 最大连接数
     */
    private Integer maxConnections = 100;

    /**
     * 连接超时时间（毫秒）
     */
    private Integer connectionTimeout = 5000;

    /**
     * 空闲超时时间（秒）
     */
    private Integer idleTimeout = 600;
}
