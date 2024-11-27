package top.aixmax.penetrate.common.utils;

/**
 * @author wangxu
 * @version 1.0 2024/11/26 12:08
 * @description
 */
public class ByteUtils {

    /**
     * 字节转整型
     *
     * @param bytes 字节
     * @return 整型
     */
    public static int byteArrayToInt(byte[] bytes) {
        if (bytes == null || bytes.length != 4) {
            throw new IllegalArgumentException("Byte array must be non-null and of length 4");
        }

        int result = 0;
        for (byte aByte : bytes) {
            result <<= 8; // 左移8位
            result |= (aByte & 0xFF); // 将当前字节与0xFF进行按位与操作以处理符号扩展
        }
        return result;
    }

    /**
     * byte转化int型
     *
     * @param bytes  byte数组
     * @param offset 偏移量
     * @return 整数
     */
    public static int bytesToInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) |
                ((bytes[offset + 1] & 0xFF) << 16) |
                ((bytes[offset + 2] & 0xFF) << 8) |
                (bytes[offset + 3] & 0xFF);
    }

    /**
     * 整型转字节
     *
     * @param value 整型
     * @return 字节
     */
    public static byte[] intToByteArray(int value) {
        byte[] bytes = new byte[4]; // int 占用 4 个字节
        bytes[0] = (byte) (value >> 24); // 取最高位
        bytes[1] = (byte) (value >> 16); // 取次高位
        bytes[2] = (byte) (value >> 8);  // 取次低位
        bytes[3] = (byte) value;          // 取最低位
        return bytes;
    }
}
