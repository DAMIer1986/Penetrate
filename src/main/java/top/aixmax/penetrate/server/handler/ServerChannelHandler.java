package top.aixmax.penetrate.server.handler;

import com.alibaba.fastjson.JSON;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import top.aixmax.penetrate.core.handler.AbstractMessageHandler;
import top.aixmax.penetrate.core.protocol.Message;
import top.aixmax.penetrate.core.protocol.MessageFactory;
import top.aixmax.penetrate.server.manager.ClientManager;
import top.aixmax.penetrate.server.manager.ServerManager;
import top.aixmax.penetrate.server.model.ClientInfo;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * @author wangxu
 * @version 1.0 2024/11/16 21:39
 * @description
 */
@Slf4j
public class ServerChannelHandler extends AbstractMessageHandler {

    private final ClientManager clientManager;

    private final ServerManager serverManager;

    private boolean authenticated = false;

    public ServerChannelHandler(ClientManager clientManager) {
        this.clientManager = clientManager;
        this.serverManager = new ServerManager(clientManager);
    }

    @Override
    protected void handleRegister(ChannelHandlerContext ctx, Message msg) {
        if (authenticated) {
            log.warn("Duplicate register message received from {}", ctx.channel().remoteAddress());
            return;
        }

        String json = new String(msg.getData(), StandardCharsets.UTF_8);
        ClientInfo info = JSON.parseObject(json, ClientInfo.class);

        if (CollectionUtils.isEmpty(info.getPortMappings())) {
            log.warn("Client {} no port mapping", info.getClientId());
            return;
        }

        // 注册客户端
        if (clientManager.registerClient(info, ctx.channel())) {
            authenticated = true;

            info.getPortMappings().forEach((port, portInfo) ->
                    new Thread(() -> serverManager.startExternalServer(portInfo.getRemotePort())).start());

            ctx.writeAndFlush(MessageFactory.createRegisterAckMessage());
            log.info("Client registered: {}", info.getClientId());
        } else {
            log.error("Failed to register client: {}", info.getClientId());
            ctx.writeAndFlush(MessageFactory.createErrorMessage("Registration failed"));
            ctx.close();
        }
    }

    @Override
    protected void handleHeartbeat(ChannelHandlerContext ctx) {
        if (!authenticated) {
            log.warn("Received heartbeat from unauthenticated client");
            ctx.close();
            return;
        }

        ClientInfo clientInfo = clientManager.getClientByChannel(ctx.channel());
        if (clientInfo != null) {
            clientInfo.updateHeartbeat();
            ctx.writeAndFlush(MessageFactory.createHeartbeatAckMessage());
            log.debug("Heartbeat received from client: {}", clientInfo.getClientId());
        }
    }

    @Override
    protected void handleData(ChannelHandlerContext ctx, Message msg) {
        if (!authenticated) {
            log.warn("Received data from unauthenticated client");
            ctx.close();
            return;
        }

        ClientInfo clientInfo = clientManager.getClientByChannel(ctx.channel());
        if (clientInfo == null) {
            log.error("Client info not found for channel");
            ctx.close();
            return;
        }

        try {
            handleDataForward(ctx, msg, clientInfo);
        } catch (Exception e) {
            log.error("Error handling data forward for client: {}", clientInfo.getClientId(), e);
            ctx.writeAndFlush(MessageFactory.createErrorMessage("Data forward failed"));
        }
    }

    /**
     * 获取客户端返回数据并发送至对应的服务管道
     *
     * @param ctx        客户端连接
     * @param msg        客户端数据
     * @param clientInfo 客户端信息
     */
    private void handleDataForward(ChannelHandlerContext ctx, Message msg, ClientInfo clientInfo) {
        ByteBuffer buffer = ByteBuffer.wrap(msg.getData());
        int externalChannelId = msg.getChannelId();
        int dataLength = buffer.getInt();
        byte[] data = new byte[dataLength];
        buffer.get(data);

        // 更新统计信息
        clientInfo.incrementRequests();
        clientInfo.addBytes(dataLength);

        // 获取目标通道
        Channel targetChannel = clientManager.getServerChannel(externalChannelId);
        if (targetChannel != null && targetChannel.isActive()) {
            targetChannel.writeAndFlush(data);
            log.debug("Data forwarded to Server channel Id {}, length: {}", externalChannelId, dataLength);
        } else {
            log.warn("No active channel found for id: {}", externalChannelId);
            ctx.writeAndFlush(MessageFactory.createErrorMessage(
                    "No active channel for id: " + externalChannelId));
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (authenticated) {
            clientManager.unregisterClient(ctx.channel());
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent event) {
            if (event.state() == IdleState.READER_IDLE) {
                log.warn("Channel idle timeout, closing connection");
                ctx.close();
            }
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ClientInfo clientInfo = clientManager.getClientByChannel(ctx.channel());
        log.error("Channel exception caught for client: {}",
                clientInfo != null ? clientInfo.getClientId() : "unknown", cause);
        ctx.close();
    }
}
