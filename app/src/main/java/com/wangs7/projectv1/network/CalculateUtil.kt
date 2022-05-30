package com.wangs7.projectv1.network

import kotlin.experimental.and
import kotlin.experimental.or

/**
 * @version:
 * @author: wangs7__
 * @className:
 * @packageName:
 * @description:
 * @date: 2022/4/6 8:49
 */
object CalculateUtil {
    /**
     * 注释：int到字节数组的转换！
     *
     * @param number
     * @return
     */
    @JvmStatic
    fun intToByte(number: Int): ByteArray {
        var temp = number
        val b = ByteArray(4)
        for (i in b.indices) {
            b[i] = (temp and 0xff.toByte().toInt()).toByte() // 将最低位保存在最低位
            temp = temp shr 8 // 向右移8位
        }
        return b
    }

    fun byteToInt(b: Byte): Int {
        //Java 总是把 byte 当做有符处理；我们可以通过将其和 0xFF 进行二进制与得到它的无符值
        return b.toInt() and 0xFF
    }

    //byte 数组与 int 的相互转换
    fun byteArrayToInt(b: ByteArray): Int {
        return (b[3] and 0xFF.toByte() or (
                (b[2].toInt() and 0xFF shl 8).toByte()) or (
                (b[1].toInt() and 0xFF shl 16).toByte()) or (
                (b[0].toInt() and 0xFF shl 24).toByte())).toInt()
    }

    fun intToByteArray(a: Int): ByteArray {
        return byteArrayOf(
            (a shr 24 and 0xFF).toByte(),
            (a shr 16 and 0xFF).toByte(),
            (a shr 8 and 0xFF).toByte(),
            (a and 0xFF).toByte()
        )
    }

    // 清空buf的值
    @JvmStatic
    fun memset(buf: ByteArray, value: Int, size: Int) {
        for (i in 0 until size) {
            buf[i] = value.toByte()
        }
    }

    //    public static void dump(NALU_t n) {
    //        System.out.println("len: " + n.len + " nal_unit_type:" + n.nal_unit_type);
    //
    //    }
    // 判断是否为0x000001,如果是返回1
    fun FindStartCode2(Buf: ByteArray, off: Int): Int {
        return if (Buf[0 + off] != 0.toByte() || Buf[1 + off] != 0.toByte() || Buf[2 + off] != 1.toByte()) 0 else 1
    }

    // 判断是否为0x00000001,如果是返回1
    fun FindStartCode3(Buf: ByteArray, off: Int): Int {
        return if (Buf[0 + off] != 0.toByte() || Buf[1 + off] != 0.toByte() || Buf[2 + off] != 0.toByte() || Buf[3 + off] != 1.toByte()
        ) 0 else 1
    }
}