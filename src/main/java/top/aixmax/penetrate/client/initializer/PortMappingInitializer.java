package top.aixmax.penetrate.client.initializer;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import top.aixmax.penetrate.client.config.PortMapping;
import top.aixmax.penetrate.client.handler.DataForwardHandler;
import top.aixmax.penetrate.client.handler.LocalChannelHandler;
import top.aixmax.penetrate.client.manager.PortMappingManager;

/**
 * @author wangxu
 * @version 1.0 2024/11/16 19:59
 * @description
 */
@Slf4j
public class PortMappingInitializer extends ChannelInitializer<SocketChannel> {
    private final PortMapping portMapping;
    private final Channel serverChannel;
    private final PortMappingManager portMappingManager;

    public PortMappingInitializer(PortMapping portMapping,
                                  Channel serverChannel,
                                  PortMappingManager portMappingManager) {
        this.portMapping = portMapping;
        this.serverChannel = serverChannel;
        this.portMappingManager = portMappingManager;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        // 检查是否可以接受新连接
        if (!portMappingManager.canAcceptNewConnection(portMapping.getLocalPort())) {
            log.warn("Reached maximum connections for port {}", portMapping.getLocalPort());
            ch.close();
            return;
        }

        ch.pipeline()
                .addLast(new IdleStateHandler(0, 0, portMapping.getIdleTimeout()))
                .addLast(new LocalChannelHandler(portMapping, serverChannel, portMappingManager));
    }
}
