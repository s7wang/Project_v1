package com.wangs7.projectv1.network

/**
 * @version:
 * @author: wangs7__
 * @className:
 * @packageName:
 * @description:
 * @date: 2022/5/10 20:09
 **/
interface SendInfo {
    fun sendAvcPacket(data: ByteArray?, dataSize: Int, timeUs: Long)
}