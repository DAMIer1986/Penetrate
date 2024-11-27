package top.aixmax.penetrate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author wangxu
 * @version 1.0 2024/11/16 18:17
 * @description
 */
@Data
@ConfigurationProperties(prefix = "nat")
public class CommonConfig {
    /**
     * 运行模式: client/server/both
     */
    private RunMode mode = RunMode.BOTH;

    public enum RunMode {
        CLIENT,
        SERVER,
        BOTH
    }
}
