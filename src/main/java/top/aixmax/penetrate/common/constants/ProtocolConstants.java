package top.aixmax.penetrate.common.constants;

/**
 * @author wangxu
 * @version 1.0 2024/11/16 18:01
 * @description 协议相关常量
 */

public final class ProtocolConstants {
    /**
     * 协议版本
     */
    public static final byte VERSION = 1;

    /**
     * 魔数
     */
    public static final short MAGIC = (short) 0xffff;  // 可以自定义一个魔数值

    /**
     * 最大帧长度 (16MB)
     */
    public static final int MAX_FRAME_LENGTH = 16 * 1024 * 1024;

    /**
     * 长度字段长度
     */
    public static final int LENGTH_FIELD_LENGTH = 4;

    /**
     * 头部长度 (version + type + length)
     */
    public static final int HEADER_LENGTH = 1 + 1 + LENGTH_FIELD_LENGTH;

    /**
     * 最小消息长度
     */
    public static final int MIN_MESSAGE_LENGTH = HEADER_LENGTH;

    /**
     * 默认心跳间隔（秒）
     */
    public static final int DEFAULT_HEARTBEAT_INTERVAL = 30;

    /**
     * 默认重试间隔（秒）
     */
    public static final int DEFAULT_RETRY_INTERVAL = 5;

    /**
     * 默认最大重试次数
     */
    public static final int DEFAULT_MAX_RETRY_TIMES = 3;

    /**
     * 默认连接超时时间（毫秒）
     */
    public static final int DEFAULT_CONNECT_TIMEOUT = 5000;

    public static final int minLength = 15;

    public static final byte start = 0x02;

    public static final byte end = 0x03;

    public static final int waitTime = 5000;

    private ProtocolConstants() {
        // 防止实例化
    }
}
