package top.aixmax.penetrate.server.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.aixmax.penetrate.config.CommonConfig;
import top.aixmax.penetrate.server.NatServer;

/**
 * @author wangxu
 * @version 1.0 2024/11/16 20:07
 * @description
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({ServerConfig.class, CommonConfig.class})
public class ServerConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "nat.server", name = "enabled", havingValue = "true")
    public NatServer natServer(ServerConfig serverConfig, CommonConfig commonConfig) {
        if (commonConfig.getMode() == CommonConfig.RunMode.CLIENT) {
            log.info("Skipping server initialization as running in CLIENT mode");
            return null;
        }

        log.info("Initializing NAT server in {} mode", commonConfig.getMode());
        validateServerConfig(serverConfig);
        return new NatServer(serverConfig);
    }

    private void validateServerConfig(ServerConfig config) {
        if (config.getClientPort() <= 0 || config.getClientPort() > 65535) {
            throw new IllegalArgumentException("Invalid client port: " + config.getClientPort());
        }

        if (config.isEnableSsl()) {
            if (config.getCertPath() == null || config.getKeyPath() == null) {
                throw new IllegalArgumentException("SSL certificate and key paths must be provided when SSL is enabled");
            }
        }

        if (config.getMaxConnections() <= 0) {
            throw new IllegalArgumentException("Invalid max connections: " + config.getMaxConnections());
        }
    }
}
