package top.aixmax.penetrate.server.manager;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import lombok.extern.slf4j.Slf4j;
import top.aixmax.penetrate.common.constants.ProtocolConstants;
import top.aixmax.penetrate.server.handler.ExternalHandler;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务器管理器
 * 负责管理外部服务器的启动和运行，处理外部连接请求
 *
 * @author wangxu
 * @version 1.0 2024/11/26 10:32
 */
@Slf4j
public class ServerManager {

    /** 客户端管理器，用于管理客户端连接 */
    private final ClientManager clientManager;

    /** 外部服务器通道映射表，key为端口号，value为对应的Channel */
    private final Map<Integer, Channel> channelMap;

    /** Netty事件循环组，用于处理连接请求 */
    private final EventLoopGroup bossGroup;

    /** Netty事件循环组，用于处理IO操作 */
    private final EventLoopGroup workerGroup;

    /** Netty服务器启动器 */
    private final ServerBootstrap bootstrap;

    /**
     * 构造函数
     * @param clientManager 客户端管理器
     */
    public ServerManager(ClientManager clientManager) {
        this.clientManager = clientManager;
        this.channelMap = new ConcurrentHashMap<>();
        this.bossGroup = new EpollEventLoopGroup(1);
        this.workerGroup = new EpollEventLoopGroup(Runtime.getRuntime().availableProcessors() * 128);
        this.bootstrap = new ServerBootstrap();
    }

    /**
     * 启动外部端口监听
     * 在指定端口启动服务器，处理外部连接请求
     * @param externalPort 外部服务端口
     */
    public void startExternalServer(int externalPort) {
        bootstrap.group(bossGroup, workerGroup)
                .channel(EpollServerSocketChannel.class)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.SO_RCVBUF, 1048576) // 1MB 发送缓冲区
                .option(ChannelOption.SO_BACKLOG, 256)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.SO_SNDBUF, 1048576)
                .childHandler(new ExternalHandler(clientManager, externalPort));

        Channel sc = null;
        while (true) {
            try {
                if (sc == null || !sc.isActive()) {
                    if (sc != null) {
                        sc.close().sync();
                    }
                    sc = bootstrap.bind(externalPort).sync().channel();
                    channelMap.put(externalPort, sc);
                    log.info("External server listening on port {}", externalPort);
                }
                // 每5秒循环一次，确保接收线程存活
                Thread.sleep(ProtocolConstants.waitTime);
            } catch (Exception ex) {
                log.error("Failed to start external server on port {}", externalPort, ex);
                try {
                    Thread.sleep(ProtocolConstants.waitTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    @PreDestroy
    public void stop(int externalPort) {
        log.info("Stopping NAT server...");
        Channel sc = channelMap.remove(externalPort);
        try {
            sc.closeFuture().sync();
        } catch (InterruptedException e) {
            log.warn("Interrupted while closing server channels", e);
            Thread.currentThread().interrupt();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            log.info("NAT server stopped");
        }
    }

}
