package top.aixmax.penetrate.client.handler;

import com.alibaba.fastjson.JSON;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import top.aixmax.penetrate.client.manager.PortMappingManager;
import top.aixmax.penetrate.common.constants.ProtocolConstants;
import top.aixmax.penetrate.config.ClientConfig;
import top.aixmax.penetrate.core.handler.AbstractMessageHandler;
import top.aixmax.penetrate.core.protocol.Message;
import top.aixmax.penetrate.core.protocol.MessageFactory;
import top.aixmax.penetrate.server.model.ClientInfo;


/**
 * @author wangxu
 * @version 1.0 2024/11/16 18:12
 * @description
 */
@Slf4j
@ChannelHandler.Sharable
public class ClientHandler extends AbstractMessageHandler {

    private final PortMappingManager portMappingManager;

    private final ClientConfig config;

    private boolean authenticated = false;

    public ClientHandler(PortMappingManager portMappingManager, ClientConfig config) {
        this.portMappingManager = portMappingManager;
        this.config = config;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        // 发送注册消息
        ClientInfo ci = new ClientInfo(config.getClientId(), null);
        ci.setSort(config.getSort());
        if (CollectionUtils.isEmpty(portMappingManager.getMappings())) {
            throw new RuntimeException("Client No Mapping!");
        }

        ci.setPortMappings(portMappingManager.getMappings());

        ByteBuf byteBuf = Unpooled.wrappedBuffer(
                MessageFactory.createRegisterMessage(JSON.toJSONString(ci)));
        ctx.writeAndFlush(byteBuf);
        log.info("Sending register message with clientId: {}", config.getClientId());
    }

    @Override
    protected void handleRegisterAck(ChannelHandlerContext ctx, Message msg) {
        authenticated = true;
        portMappingManager.setServerChannel(ctx.channel());

        // 开启心跳线程
        new Thread(() -> {
            try {
                while (ctx.channel().isActive()) {
                    ByteBuf heartByte = Unpooled.wrappedBuffer(MessageFactory.createHeartbeatMessage());
                    ctx.channel().writeAndFlush(heartByte);
                    Thread.sleep(ProtocolConstants.waitTime);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();
        log.info("Client registered successfully");
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
            portMappingManager.handleIncomingData(msg);
        } catch (Exception e) {
            log.error("Error handling incoming data", e);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("Connection to server lost");
        authenticated = false;
        // 通知端口映射管理器连接断开
        portMappingManager.handleDisconnect();
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Channel exception", cause);
        ctx.close();
    }

    @Override
    public void handleConnect(ChannelHandlerContext ctx, Message msg) {
        if (!authenticated) {
            log.warn("Received data before authentication");
            return;
        }

        try {
            // 注册连接
            portMappingManager.handleConnect(msg);
        } catch (Exception e) {
            log.error("Error handling incoming data", e);
        }
    }
}
