package com.wangs7.projectv1.network
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*

/**
 * @version:
 * @author: wangs7__
 * @className:
 * @packageName:
 * @description:
 * @date: 2022/3/29 1:28
 **/
class RtpStream (
    private val payloadType: Int,
    private val sampleRate: Int,
    ) {
        private var sequenceNumber: Short = 0
        @Throws(IOException::class)
        fun makeRtpPacket(data: ByteArray?, offset: Int, dataSize: Int, timeUs: Long): ByteArray {
            return makePacket(null, data, offset,dataSize, timeUs)
        }
        fun makeRtpPacket(prefixData: ByteArray?, data: ByteArray?, offset: Int, dataSize: Int, timeUs: Long): ByteArray {
            return makePacket(prefixData, data, offset,dataSize, timeUs)
        }

        /**
         * RTP packet header
         * Bit offset[b]	0-1	    2	    3	    4-7	    8	9-15	    16-31
         * 0			Version	    P	    X	    CC	    M	PT	    Sequence Number     31
         * 32			Timestamp									                        63
         * 64			SSRC identifier								                        95
         */
        @Throws(IOException::class)
        private fun makePacket(prefixData: ByteArray?, data: ByteArray?, offset: Int, dataSize: Int, timeUs: Long): ByteArray {
            val buffer = ByteBuffer.allocate(500000)
            buffer.put((2 shl 6).toByte()) //rtp版本号 高两位
            buffer.put(payloadType.toByte()) //有效载荷类型
            buffer.putShort(sequenceNumber++) //序列号
            buffer.putInt(timeUs.toInt()) //时间戳
            buffer.putInt(12345678)
            buffer.putInt(dataSize)
            if (prefixData != null) {
                buffer.put(prefixData)
            }
            buffer.put(data, offset, dataSize)
            return Arrays.copyOf(buffer.array(), buffer.position())
        }

        companion object {
            private val TAG = RtpStream::class.java.simpleName
            const val RTP_PAYLOAD_TYPE_PCMU    = 0  // g711u
            const val RTP_PAYLOAD_TYPE_PCMA    = 8   // g711a
            const val RTP_PAYLOAD_TYPE_JPEG    = 26
            const val RTP_PAYLOAD_TYPE_H264    = 96
            const val RTP_PAYLOAD_TYPE_H265    = 97
            const val RTP_PAYLOAD_TYPE_OPUS    = 98
            const val RTP_PAYLOAD_TYPE_AAC     = 99
            const val RTP_PAYLOAD_TYPE_G726    = 100
            const val RTP_PAYLOAD_TYPE_G726_16 = 101
            const val RTP_PAYLOAD_TYPE_G726_24 = 102
            const val RTP_PAYLOAD_TYPE_G726_32 = 103
            const val RTP_PAYLOAD_TYPE_G726_40 = 104
            const val RTP_PAYLOAD_TYPE_SPEEX   = 105

        }
}