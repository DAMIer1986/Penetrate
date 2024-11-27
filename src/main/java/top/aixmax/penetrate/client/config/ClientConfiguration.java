package top.aixmax.penetrate.client.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.aixmax.penetrate.client.NatClient;
import top.aixmax.penetrate.config.ClientConfig;
import top.aixmax.penetrate.config.CommonConfig;

/**
 * @author wangxu
 * @version 1.0 2024/11/16 18:14
 * @description
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({ClientConfig.class, CommonConfig.class})
public class ClientConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "nat.client", name = "enabled", havingValue = "true")
    public NatClient natClient(ClientConfig clientConfig, CommonConfig commonConfig) {
        if (commonConfig.getMode() == CommonConfig.RunMode.SERVER) {
            log.info("Skipping client initialization as running in SERVER mode");
            return null;
        }

        log.info("Initializing NAT client in {} mode", commonConfig.getMode());
        validateClientConfig(clientConfig);
        return new NatClient(clientConfig);
    }

    private void validateClientConfig(ClientConfig config) {
        if (config.getClientId() == null || config.getClientId().trim().isEmpty()) {
            config.setClientId(generateDefaultClientId());
            log.warn("No client ID configured, using generated ID: {}", config.getClientId());
        }

        if (config.getPortMappings() == null || config.getPortMappings().isEmpty()) {
            log.warn("No port mappings configured for client");
        }
    }

    private String generateDefaultClientId() {
        return "client-" + System.currentTimeMillis();
    }
}
