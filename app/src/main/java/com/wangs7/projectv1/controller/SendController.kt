package com.wangs7.projectv1.controller

import android.util.Log
import com.wangs7.projectv1.network.UdpSocket
import java.nio.ByteBuffer

/**
 * @version:
 * @author: wangs7__
 * @className:
 * @packageName:
 * @description:
 * @date: 2022/5/18 14:24
 **/
class SendController {
    private var socket : UdpSocket? = null

    private var isRunning = false

    private val receiveThread:Runnable = Runnable {
        Log.e(TAG, "receiveThread ++++++++++++++++++++++++++++")
        while (isRunning) {
            if (socket == null) {
                Log.e(TAG, "receiveThread socket == null")
                break
            }
            var data = socket!!.receivePacket()
            if (data !== null) {
                var buffer = ByteBuffer.allocate(data.size)
                buffer.put(data)
                //var sn = buffer.getShort(2)
                var seqNum: ByteBuffer = ByteBuffer.allocate(4)
                seqNum.put(data.copyOfRange(2, 4)) // 包序
                var sn = seqNum.getShort(0).toUShort().toInt()

                var ts = buffer.getInt(4)
//                var timeStamp: ByteBuffer = ByteBuffer.allocate(4)
//                timeStamp.put(data.copyOfRange(4, 8)) // 时间戳
//                var ts = timeStamp.getInt( 0)

                var dt = buffer.getInt(8)
//                var deltaTs :ByteBuffer = ByteBuffer.allocate(4)
//                deltaTs.put(data.copyOfRange(8, 12)) // 时间戳
//                var dt = deltaTs.getInt( 0)
                var loss_n = buffer.getInt(12)
                var loss_b = buffer.getInt(16)
                var srcSq = buffer.getShort(20)
                Log.e(TAG,"=========== seq = ${sn}  TimeStamp = ${ts}  DeltaT = ${dt}  loss = ${loss_n*1.0/loss_b}  srcSq = ${srcSq}===========")
            }
        }
    }

    fun start() {
        Log.e(TAG,"===========START===========")
        isRunning = true
        var t = Thread(receiveThread)
        //t.priority = 9
        t.start()
    }
    fun stop() {
        isRunning = false

    }

    init {
        socket = UdpSocket(UdpSocket.DEFAULT_PORT+1, false)

    }

    companion object {
        val TAG: String = SendController::class.java.simpleName
    }
}