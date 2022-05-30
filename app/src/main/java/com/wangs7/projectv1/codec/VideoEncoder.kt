package com.wangs7.projectv1.codec

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.wangs7.projectv1.network.SendInfo
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue


/**
 * @version:
 * @author: wangs7__
 * @className:
 * @packageName:
 * @description:
 * @date: 2022/3/15 17:01
 **/
class VideoEncoder : BitRateControl{
    private var videoWidth: Int = DEFAULT_WIDTH
    private var videoHeight:Int = DEFAULT_HEIGHT
    private var frameRate: Int = DEFAULT_FRAME_RATE
    private var bitRate: Int = DEFAULT_BIT_RATE
    private var iFrequency: Int = DEFAULT_I_FREQUENCY

    private var mediaCodec: MediaCodec? = null
    private var mediaFormat: MediaFormat? = null

    private var videoEncoderHandler: Handler
    private var videoEncoderHandlerThread = HandlerThread("VideoEncoder")
    private var mInfo: ByteArray? = null
    private var info: ByteArray? = null
    private var isRun: Boolean = false

    private lateinit var sendInfo:SendInfo

    private val codecCallback : MediaCodec.Callback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            if (!isRun)
                return
            var inputBuffer = codec.getInputBuffer(index)
            inputBuffer!!.clear()
            var length = 0
            val  dataSource = inputDataQueue.poll()
            if (dataSource != null) {
                inputBuffer.put(dataSource)
                length = dataSource.size
            }

            codec.queueInputBuffer(index, 0, length, 0 ,0)
        }

        override fun onOutputBufferAvailable(
            codec: MediaCodec,
            index: Int,
            info: MediaCodec.BufferInfo
        ) {
            val outputBuffer = codec.getOutputBuffer(index)

            if (outputBuffer != null && info.size > 0) {
                val buffer = ByteArray(outputBuffer.remaining())
                outputBuffer[buffer]
                sendInfo.sendAvcPacket(buffer, buffer.size, 0);
                codec.releaseOutputBuffer(index, true)
            }
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            Log.e(TAG, "------> onError")
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            Log.i(TAG, "------> onOutputFormatChanged")

        }

    }


    fun setSendInfo(send:SendInfo) {sendInfo = send}

    fun setSize(width:Int, height:Int) {
        videoWidth = width
        videoHeight = height

    }

    fun setFrameRate(newFrameRate:Int) {
        frameRate = newFrameRate
    }
    fun setBitRate(newBitRate: Int) {
        bitRate = newBitRate
    }

    private fun setting() {

        mediaFormat!!.setInteger(MediaFormat.KEY_WIDTH, videoWidth)
        mediaFormat!!.setInteger(MediaFormat.KEY_HEIGHT, videoHeight)
        mediaFormat!!.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        mediaFormat!!.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
        mediaFormat!!.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
        )
        mediaFormat!!.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrequency)
        /**
         * CQ 表示完全不控制码率，尽最大可能保证图像质量, 质量要求高、不在乎带宽、
         * 解码器支持码率剧烈波动的情况下，可以选择这种策略；
         * CBR 表示编码器会尽量把输出码率控制为设定值，输出码率会在一定范围内波动，
         * 对于小幅晃动，方块效应会有所改善，但对剧烈晃动仍无能为力；连续调低码率则会导致码率急剧下降，
         * 如果无法接受这个问题，那 VBR 就不是好的选择；
         * VBR 表示编码器会根据图像内容的复杂度（实际上是帧间变化量的大小）来动态调整输出码率，
         * 图像复杂则码率高，图像简单则码率低，优点是稳定可控，这样对实时性的保证有帮助。
         */
        mediaFormat!!.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
    }

    fun inputFrameToEncoder(frameData: ByteArray) {
        inputDataQueue.offer(frameData)
    }

    override fun changeBitRate(newBitRate:Int) {
        var params = Bundle()
        bitRate = newBitRate
        params.clear()
        params.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, newBitRate)
        mediaCodec?.setParameters(params)
    }

    override fun getBitRate():Int { return bitRate }

    fun start() {
        Log.d(TAG, "VideoEncoder init.")
        try {
            mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        setting()
        mediaCodec!!.setCallback(codecCallback, videoEncoderHandler)
        mediaCodec!!.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mediaCodec!!.start()
        isRun = true
    }
    fun clear(){
        inputDataQueue.clear()
    }
    fun close() {
        try {
            mediaCodec!!.stop()
            mediaCodec!!.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }


    init {
        Log.d(TAG, "VideoEncoder init.")
        try {
            mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, videoWidth, videoHeight)
        videoEncoderHandlerThread.start()
        videoEncoderHandler = Handler(videoEncoderHandlerThread.looper)
    }

    companion object {
        private val TAG = VideoEncoder::class.java.simpleName
        private const val MIME_TYPE = "video/avc"
        const val DEFAULT_WIDTH = 480
        const val DEFAULT_HEIGHT = 640
        const val DEFAULT_FRAME_RATE = 25
        const val DEFAULT_BIT_RATE = 50_000
        const val DEFAULT_I_FREQUENCY = 2

        private const val CACHE_BUFFER_SIZE = 128
        private val inputDataQueue = ArrayBlockingQueue<ByteArray>(CACHE_BUFFER_SIZE)

    }
}