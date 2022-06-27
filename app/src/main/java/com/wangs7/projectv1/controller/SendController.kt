package com.wangs7.projectv1.controller

import android.util.Log
import com.wangs7.projectv1.codec.BitRateControl
import com.wangs7.projectv1.network.UdpSocket
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.*

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

    private var bitRateControl:BitRateControl? = null

    private var lossRate = 0.0

    private val receiveThread:Runnable = Runnable {
        Log.e(TAG, "receiveThread ++++++++++++++++++++++++++++")
        while (isRunning) {
            if (socket == null) {
                Log.e(TAG, "receiveThread socket == null")
                break
            }
            var data = socket!!.receivePacket()
            /** 解JOSN信息 **/
            if (data != null){
                var str = String(Arrays.copyOfRange(data, 8, data.size), charset("GB2312"))

                var json = JSONObject(str)
                var inver = json.getInt("inver_res")
                var bandwidth = json.getInt("bandwidth")
                var loss = json.getInt("loss")
                var lossBase = json.getInt("loss_base")
                lossRate = loss * 1.0 / lossBase
                //TODO 控制信息处理
                Log.e(TAG,"=========== inverRes = ${inver}  bandwidth = ${bandwidth} data.size = ${data.size}===========")

            }

        }

    }
    /** 处理反馈信息 给出调整意见 **/
    private fun informationHandle(inver_:Int, bandwidth_:Int, lossRate_:Double) {

    }

    fun setBitRateController(it:BitRateControl) {
        bitRateControl = it
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


/** 原解析方案 **/
//            if (false && data !== null) {
//                var buffer = ByteBuffer.allocate(data.size)
//                buffer.put(data)
//                //var sn = buffer.getShort(2)
//                var seqNum: ByteBuffer = ByteBuffer.allocate(4)
//                seqNum.put(data.copyOfRange(2, 4)) // 包序
//                var sn = seqNum.getShort(0).toUShort().toInt()
//
//                var ts = buffer.getInt(4)
////                var timeStamp: ByteBuffer = ByteBuffer.allocate(4)
////                timeStamp.put(data.copyOfRange(4, 8)) // 时间戳
////                var ts = timeStamp.getInt( 0)
//
//                var dt = buffer.getInt(8)
////                var deltaTs :ByteBuffer = ByteBuffer.allocate(4)
////                deltaTs.put(data.copyOfRange(8, 12)) // 时间戳
////                var dt = deltaTs.getInt( 0)
//                var loss_n = buffer.getInt(12)
//                var loss_b = buffer.getInt(16)
//                var srcSq = buffer.getShort(20)
//                Log.e(TAG,"=========== seq = ${sn}  TimeStamp = ${ts}  DeltaT = ${dt}  loss = ${loss_n*1.0/loss_b}  srcSq = ${srcSq}===========")
//            }