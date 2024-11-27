package top.aixmax.penetrate.core.protocol;

import lombok.Data;
import lombok.experimental.Accessors;
import top.aixmax.penetrate.common.constants.ProtocolConstants;
import top.aixmax.penetrate.common.enums.MessageType;
import top.aixmax.penetrate.common.utils.ByteUtils;

/**
 * @author wangxu
 * @version 1.0 2024/11/16 18:04
 * @description 消息实体
 */
@Data
@Accessors(chain = true)
public class Message {
    private byte head = ProtocolConstants.start;
    private MessageType type;
    private int channelId = 0;  // 外部连接的管道ID
    private int externalPort = 0; // 外部连接端口号，传递给客户端用于映射
    private int dataLength = 0; // 数据长度
    private byte[] data;
    private byte end = ProtocolConstants.end;

    public static Message create() {
        return new Message();
    }

    /**
     * 获取消息字节
     *
     * @return 消息字节
     */
    public byte[] getBytes() {
        byte[] res = new byte[data.length + ProtocolConstants.minLength];
        res[0] = ProtocolConstants.start;
        res[1] = type.getValue();

        byte[] channelIdByte = ByteUtils.intToByteArray(channelId);
        res[2] = channelIdByte[0];
        res[3] = channelIdByte[1];
        res[4] = channelIdByte[2];
        res[5] = channelIdByte[3];

        byte[] portByte = ByteUtils.intToByteArray(externalPort);
        res[6] = portByte[0];
        res[7] = portByte[1];
        res[8] = portByte[2];
        res[9] = portByte[3];

        byte[] dataLengthByte = ByteUtils.intToByteArray(dataLength);
        res[6] = dataLengthByte[0];
        res[7] = dataLengthByte[1];
        res[8] = dataLengthByte[2];
        res[9] = dataLengthByte[3];

        System.arraycopy(res, 10, data, 0, this.data.length);

        res[res.length - 1] = ProtocolConstants.end;
        return res;
    }
}