package top.aixmax.penetrate.client.handler;

import com.alibaba.fastjson.JSON;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import top.aixmax.penetrate.client.config.PortMapping;
import top.aixmax.penetrate.client.manager.PortMappingManager;
import top.aixmax.penetrate.core.handler.AbstractMessageHandler;
import top.aixmax.penetrate.core.protocol.Message;
import top.aixmax.penetrate.core.protocol.MessageFactory;
import top.aixmax.penetrate.server.model.ClientInfo;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author wangxu
 * @version 1.0 2024/11/16 18:12
 * @description
 */
@Slf4j
@ChannelHandler.Sharable
public class ClientHandler extends AbstractMessageHandler {
    private final PortMappingManager portMappingManager;
    private final String clientId;
    private boolean authenticated = false;

    public ClientHandler(PortMappingManager portMappingManager, String clientId) {
        this.portMappingManager = portMappingManager;
        this.clientId = clientId;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // 发送注册消息
        ClientInfo ci = new ClientInfo(clientId, null);
        if (CollectionUtils.isEmpty(portMappingManager.getMappings())) {
            throw new RuntimeException("Client No Mapping!");
        }

        ci.setPortMappings(
                portMappingManager.getMappings().stream().collect(Collectors.toMap(PortMapping::getLocalPort, p -> p))
        );
        ctx.writeAndFlush(MessageFactory.createRegisterMessage(JSON.toJSONString(ci)));
        log.info("Sending register message with clientId: {}", clientId);
    }

    @Override
    protected void handleRegisterAck(ChannelHandlerContext ctx, Message msg) {
        authenticated = true;
        log.info("Client registered successfully");

        // 发送端口映射请求
        List<PortMapping> mappings = portMappingManager.getMappings();
        for (PortMapping mapping : mappings) {
            if (mapping.isEnabled()) {
                ctx.writeAndFlush(MessageFactory.createPortMappingMessage(
                        mapping.getLocalPort(),
                        mapping.getRemotePort()
                ));
                log.debug("Sent port mapping request: {} -> {}", mapping.getLocalPort(), mapping.getRemotePort());
            }
        }
    }

    @Override
    protected void handleHeartbeatAck(ChannelHandlerContext ctx) {
        log.debug("Received heartbeat ack");
    }

    @Override
    protected void handleData(ChannelHandlerContext ctx, Message msg) {
        if (!authenticated) {
            log.warn("Received data before authentication");
            return;
        }

        try {
            // 转发数据到本地端口
            portMappingManager.handleIncomingData(msg.getData());
        } catch (Exception e) {
            log.error("Error handling incoming data", e);
        }
    }

    protected void handlePortMappingAck(ChannelHandlerContext ctx, Message msg) {
        log.info("Port mapping acknowledged by server");
        // 可以在这里添加端口映射成功的处理逻辑
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent event) {
            if (event.state() == IdleState.WRITER_IDLE) {
                // 发送心跳
                ctx.writeAndFlush(MessageFactory.createHeartbeatMessage());
                log.debug("Sent heartbeat message");
            } else if (event.state() == IdleState.READER_IDLE) {
                // 读超时，关闭连接
                log.warn("Read idle timeout, closing connection");
                ctx.close();
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("Connection to server lost");
        authenticated = false;
        // 通知端口映射管理器连接断开
        portMappingManager.handleDisconnect();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Channel exception", cause);
        ctx.close();
    }
}
