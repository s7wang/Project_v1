package com.wangs7.projectv1.network

import android.util.Log
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * @version:
 * @author: wangs7__
 * @className:
 * @packageName:
 * @description:
 * @date: 2022/3/29 1:29
 **/
class RtpSender: SendInfo, SendControl, RtpPacketEncode.H264ToRtpListener{


    private var socket: UdpSocket? = null
    private var rtpPacketEncode: RtpPacketEncode? = null

    private val sendQueue = ArrayBlockingQueue<ByteArray>(QUEUE_MAX_SIZE)
    val lock = ReentrantLock()
    private var queueByteSize = 0


    private var isRunning = false

    private val sendThread:Runnable


    fun setFrameRate(newFrameRate: Int){
        rtpPacketEncode?.setFrameRate(newFrameRate)
    }



    fun setAddress(ip: String?, port: Int) {
        if (socket == null){
            socket = UdpSocket(UdpSocket.DEFAULT_PORT, false)
        }
        socket!!.setAddress(ip, port)
    }

    override fun sendAvcPacket(data: ByteArray?, dataSize: Int, timeUs: Long) {

        if (data != null) {
            rtpPacketEncode?.h264ToRtp(data, data.size)
        }
    }

    fun start() {
        isRunning = true
        Thread(sendThread).start()
    }


    fun close() {
        isRunning = false

        rtpPacketEncode?.clearSN()
        sendQueue.clear()
        socket!!.close()
        socket = null
    }


    init {
        socket = UdpSocket(UdpSocket.DEFAULT_PORT, false)
        rtpPacketEncode = RtpPacketEncode(this)
        sendThread = Runnable {
            while (isRunning) {
                //Thread.sleep(0, 10)
                var data = sendQueue.poll(2, TimeUnit.MILLISECONDS)
                if (data != null) {
                    socket?.sendPacket(data, data.size)
                    lock.lock()
                    queueByteSize -= data.size
                    lock.unlock()
                    Log.i(TAG, "++++ h264ToRtpResponse send size ${data.size} ++++")
                }
            }
            Log.e(TAG, "sendThread over")
        }

    }

    companion object {
        private val TAG = RtpSender::class.java.simpleName
        private const val QUEUE_MAX_SIZE = 256
        private const val SPEED_LOW = 0
        private const val SPEED_MID = 0
        private const val SPEED_HEG = 0

    }

    override fun h264ToRtpResponse(out: ByteArray?, len: Int) {

        if (out != null){
            //socket?.sendPacket(out, len)
            //Log.i(TAG, "h264ToRtpResponse send size $len")
            /** 添加数据到缓冲队列 **/
            sendQueue.offer(Arrays.copyOfRange(out, 0, len))
            //统计队列现有容量
            lock.lock()
            queueByteSize += len
            lock.unlock()

        }
        else{
            Log.e(TAG, "h264ToRtpResponse out == null")
        }
    }

    /** 未实现 **/
    override fun updateSendControl() {
        var remainBuffer = sendQueue.remainingCapacity()
    }
}