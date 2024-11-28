package top.aixmax.penetrate.server.handler;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.json.JSONParser;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.util.CollectionUtils;
import top.aixmax.penetrate.client.config.PortMapping;
import top.aixmax.penetrate.core.handler.AbstractMessageHandler;
import top.aixmax.penetrate.core.protocol.Message;
import top.aixmax.penetrate.core.protocol.MessageFactory;
import top.aixmax.penetrate.server.manager.ClientManager;
import top.aixmax.penetrate.server.manager.ServerManager;
import top.aixmax.penetrate.server.model.ClientInfo;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * @author wangxu
 * @version 1.0 2024/11/16 21:39
 * @description
 */
@Slf4j
@ChannelHandler.Sharable
public class ServerChannelHandler extends AbstractMessageHandler {

    private final ClientManager clientManager;

    private final ServerManager serverManager;

    private static final Set<Integer> livePort = new ConcurrentSkipListSet<>();

    private final Map<ChannelHandlerContext, Boolean> authenticatedMap = new ConcurrentHashMap<>();

    public ServerChannelHandler(ClientManager clientManager) {
        this.clientManager = clientManager;
        this.serverManager = new ServerManager(clientManager);
    }

    @Override
    protected void handleRegister(ChannelHandlerContext ctx, Message msg) {
        boolean authenticated = authenticatedMap.computeIfAbsent(ctx, p -> false);
        if (authenticated) {
            log.warn("Duplicate register message received from {}", ctx.channel().remoteAddress());
            return;
        }

        String json = new String(msg.getData(), StandardCharsets.UTF_8);
        ClientInfo info = JSON.parseObject(json, ClientInfo.class);
        info.setChannel(ctx.channel());
        if (CollectionUtils.isEmpty(info.getPortMappings())) {
            log.warn("Client {} no port mapping", info.getClientId());
            return;
        }

        // 注册客户端
        if (clientManager.registerClient(info, ctx.channel())) {
            authenticatedMap.put(ctx, true);

            info.getPortMappings().forEach((portInfo) -> {
                if (!livePort.contains(portInfo.getRemotePort())) {
                    livePort.add(portInfo.getRemotePort());
                    new Thread(() -> serverManager.startExternalServer(portInfo.getRemotePort())).start();
                }
            });
            ctx.channel().writeAndFlush(Unpooled.wrappedBuffer(MessageFactory.createRegisterAckMessage()));
            log.info("Client registered: {}", info.getClientId());
        } else {
            log.error("Failed to register client: {}", info.getClientId());
            ctx.channel().writeAndFlush(Unpooled.wrappedBuffer(MessageFactory.createErrorMessage("Registration failed")));
            ctx.close();
        }
    }

    @Override
    protected void handleHeartbeat(ChannelHandlerContext ctx) {
        boolean authenticated = authenticatedMap.computeIfAbsent(ctx, p -> false);
        if (!authenticated) {
            log.warn("Received heartbeat from unauthenticated client");
            ctx.close();
            return;
        }

        ClientInfo clientInfo = clientManager.getClientByChannel(ctx.channel());
        if (clientInfo != null) {
            clientInfo.updateHeartbeat();
            ctx.writeAndFlush(Unpooled.wrappedBuffer(MessageFactory.createHeartbeatAckMessage()));
            log.debug("Heartbeat received from client: {}", clientInfo.getClientId());
        }
    }

    @Override
    protected void handleData(ChannelHandlerContext ctx, Message msg) {
        boolean authenticated = authenticatedMap.computeIfAbsent(ctx, p -> false);
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
            ctx.writeAndFlush(Unpooled.wrappedBuffer(MessageFactory.createErrorMessage("Data forward failed")));
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
        // 更新统计信息
        clientInfo.incrementRequests();
        if (msg.getData() != null) {
            clientInfo.addBytes(msg.getData().length);
            // 获取目标通道
            Channel targetChannel = clientManager.getServerChannel(msg.getChannelId());
            if (targetChannel != null && targetChannel.isActive()) {
                targetChannel.writeAndFlush(Unpooled.wrappedBuffer(msg.getData()));
                log.debug("Data forwarded to Server channel Id {}, length: {}", msg.getChannelId(), msg.getData().length);
            } else {
                log.warn("No active channel found for id: {}", msg.getChannelId());
                ctx.writeAndFlush(Unpooled.wrappedBuffer(
                        MessageFactory.createErrorMessage("No active channel for id: " + msg.getChannelId())));
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        boolean authenticated = authenticatedMap.computeIfAbsent(ctx, p -> false);
        if (authenticated) {
            ClientInfo info = clientManager.unregisterClient(ctx.channel());
            if (info != null && !CollectionUtils.isEmpty(info.getPortMappings())) {
                info.getPortMappings().forEach(portMapping -> {
                    // 可以增加，如果所有客户端都不存在则删除当前监听
                });
            }
        } else {
            ctx.close();
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
