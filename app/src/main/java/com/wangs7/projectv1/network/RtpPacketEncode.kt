package com.wangs7.projectv1.network

import com.wangs7.projectv1.network.CalculateUtil.memset
import com.wangs7.projectv1.network.CalculateUtil.intToByte
import kotlin.Throws
import java.lang.Exception
import java.nio.ByteBuffer
import java.util.*
import kotlin.experimental.and
import kotlin.experimental.or

/**
 * @version:
 * @author: wangs7__
 * @className:
 * @packageName:
 * @description:
 * @date: 2022/4/6 8:46
 */
class RtpPacketEncode     // -------视频END--------
    (private val h264ToRtpListener: H264ToRtpListener?) {
    //------------视频转换数据监听-----------
    interface H264ToRtpListener {
        fun h264ToRtpResponse(out: ByteArray?, len: Int)
    }

    //执行回调
    private fun executeH264ToRtpListener(out: ByteArray, len: Int) {
        h264ToRtpListener?.h264ToRtpResponse(out, len)
    }

    // -------视频--------
    private var frameRate: Int = DEFAULT_FRAME_RATE
    private val packageSize = DEFAULT_PACKAGE_SIZE
    private var timestampIncrease = (RTP_SAMPLING_RATE/ frameRate).toInt() //framerate是帧率
    private val sendBuffer = ByteArray(DEFAULT_BUFFER_SIZE)

    private var sequenceNumber = 0
    private var tsCurrent = 0
    private var bytes = 0

    fun setFrameRate(newFrameRate: Int){
        frameRate = newFrameRate
        timestampIncrease = (RTP_SAMPLING_RATE/ frameRate).toInt()
    }

    fun clearSN(){
        sequenceNumber = 0
    }
    /**
     * 一帧一帧的RTP封包
     *
     * @param r
     * @return
     */
    @Throws(Exception::class)
    fun h264ToRtp(r: ByteArray, h264len: Int) {
        /**
         * RTP packet header
         * Bit offset[b]	0-1	    2	    3	    4-7	    8	9-15	    16-31
         * 0			Version	    P	    X	    CC	    M	PT	    Sequence Number     31
         * 32			Timestamp									                        63
         * 64			SSRC identifier								                        95
         */
        memset(sendBuffer, 0, 1500)
        sendBuffer[1] = (sendBuffer[1] or 96)  // 负载类型号96,其值为：01100000
        sendBuffer[0] = (sendBuffer[0] or 0x80.toByte())  // 版本号,此版本固定为2
        sendBuffer[1] = (sendBuffer[1] and 254.toByte())  //标志位，由具体协议规定其值，其值为：01100000
        sendBuffer[11] = 10 //随机指定10，并在本RTP回话中全局唯一,java默认采用网络字节序号 不用转换（同源标识符的最后一个字节）
        if (h264len <= packageSize) {
            sendBuffer[1] =
                (sendBuffer[1] or 0x80.toByte())  // 设置rtp M位为1，其值为：11100000，分包的最后一片，M位（第一位）为0，后7位是十进制的96，表示负载类型
            //sendBuffer[3] = sequenceNumber++.toByte()
            sequenceNumber = (sequenceNumber +1) % SHORT_MAX
            System.arraycopy(intToByte(sequenceNumber), 0, sendBuffer, 2, 2) //send[2]和send[3]为序列号，共两位

            run {

                // java默认的网络字节序是大端字节序（无论在什么平台上），因为windows为小字节序，所以必须倒序
                /**参考：
                 * http://blog.csdn.net/u011068702/article/details/51857557
                 * http://cpjsjxy.iteye.com/blog/1591261
                 */
                var temp: Byte = sendBuffer[3]
                sendBuffer[3] = sendBuffer[2]
                sendBuffer[2] = temp
            }
            // FU-A HEADER, 并将这个HEADER填入sendBuffer[12]
            sendBuffer[12] = (sendBuffer[12].toInt() or (r[0].toInt() and 0x80)  shl 7).toByte()
            sendBuffer[12] = (sendBuffer[12].toInt() or (r[0].toInt() and 0x60 shr 5) shl 5).toByte()
            sendBuffer[12] = (sendBuffer[12].toInt() or (r[0].toInt() and 0x1f)).toByte()
            // 同理将sendBuffer[13]赋给nalu_payload
            //NALU头已经写到sendBuffer[12]中，接下来则存放的是NAL的第一个字节之后的数据。所以从r的第二个字节开始复制
            System.arraycopy(r, 1, sendBuffer, 13, h264len - 1)
            tsCurrent += timestampIncrease

            bytes = h264len + 12 //获sendBuffer的长度,为nalu的长度(包含nalu头但取出起始前缀,加上rtp_header固定长度12个字节)

            executeH264ToRtpListener(sendBuffer, bytes)
        } else if (h264len > packageSize) {

            var k = (h264len-1) / packageSize
            var l = (h264len-1) % packageSize
            var t = 0
            tsCurrent += timestampIncrease

            while (t <= k) {
                sequenceNumber = (sequenceNumber +1) % SHORT_MAX
                System.arraycopy(intToByte(sequenceNumber), 0, sendBuffer, 2, 2) //序列号，并且倒序
                run {
                    var temp: Byte = sendBuffer[3]
                    sendBuffer[3] = sendBuffer[2]
                    sendBuffer[2] = temp
                }
                if (t == 0) { //分包的第一片
                    sendBuffer[1] = (sendBuffer[1] and 0x7F) //其值为：01100000，不是最后一片，M位（第一位）设为0
                    //FU indicator，一个字节，紧接在RTP header之后，包括F,NRI，header
                    sendBuffer[12] = (sendBuffer[12].toInt() or (r[0].toInt() and 0x80) shl 7).toByte() //禁止位，为0
                    sendBuffer[12] =
                        (sendBuffer[12].toInt() or (r[0].toInt() and 0x60 shr 5) shl 5).toByte() //NRI，表示包的重要性
                    sendBuffer[12] = (sendBuffer[12] or 28.toByte()) as Byte //TYPE，表示此FU-A包为什么类型，一般此处为28
                    //FU header，一个字节，S,E，R，TYPE
                    sendBuffer[13] = (sendBuffer[13].toInt() and 0xBF).toByte() //E=0，表示是否为最后一个包，是则为1
                    sendBuffer[13] = (sendBuffer[13].toInt() and 0xDF).toByte() //R=0，保留位，必须设置为0
                    sendBuffer[13] = (sendBuffer[13].toInt() or 0x80).toByte() //S=1，表示是否为第一个包，是则为1
                    sendBuffer[13] = (sendBuffer[13] or (r[0] and 0x1f))  //TYPE，即NALU头对应的TYPE
                    //将除去NALU头剩下的NALU数据写入sendBuffer的第14个字节之后。前14个字节包括：12字节的RTP Header，FU indicator，FU header
                    System.arraycopy(r, 1, sendBuffer, 14, packageSize)
                    //client.send(new DatagramPacket(sendBuffer, packageSize + 14, addr, port/*9200*/));
                    executeH264ToRtpListener(sendBuffer, packageSize + 14)
                    t++
                } else if (t == k) { //分片的最后一片
                    sendBuffer[1] = (sendBuffer[1].toInt() or 0x80).toByte()
                    sendBuffer[12] = (sendBuffer[12].toInt() or (r[0].toInt() and 0x80) shl 7).toByte()
                    sendBuffer[12] = (sendBuffer[12].toInt() or (r[0].toInt() and 0x60 shr 5) shl 5).toByte()
                    sendBuffer[12] = (sendBuffer[12] or 28.toByte())
                    sendBuffer[13] = (sendBuffer[13].toInt() and 0xDF).toByte() //R=0，保留位必须设为0
                    sendBuffer[13] = (sendBuffer[13] and 0x7F) //S=0，不是第一个包
                    sendBuffer[13] = (sendBuffer[13] or 0x40) //E=1，是最后一个包
                    sendBuffer[13] = (sendBuffer[13] or (r[0] and 0x1f)) //NALU头对应的type
                    if (0 != l) { //如果不能整除，则有剩下的包，执行此代码。如果包大小恰好是1400的倍数，不执行此代码。
                        System.arraycopy(r, t * packageSize+1, sendBuffer, 14, l - 1) //l-1，不包含NALU头
                        bytes = l - 1 + 14 //bytes=l-1+14;
                        //client.send(new DatagramPacket(sendBuffer, bytes, addr, port/*9200*/));
                        //send(sendBuffer,bytes);
                        executeH264ToRtpListener(sendBuffer, bytes)
                    } //pl
                    t++
                } else if (t < k && 0 != t) { //既不是第一片，又不是最后一片的包
                    sendBuffer[1] = (sendBuffer[1] and 0x7F) //M=0，其值为：01100000，不是最后一片，M位（第一位）设为0.
                    sendBuffer[12] = (sendBuffer[12].toInt() or (r[0].toInt() and 0x80) shl 7).toByte()
                    sendBuffer[12] = (sendBuffer[12].toInt() or (r[0].toInt() and 0x60 shr 5) shl 5).toByte()
                    sendBuffer[12] = (sendBuffer[12] or 28.toByte())
                    sendBuffer[13] = (sendBuffer[13].toInt() and 0xDF).toByte() //R=0，保留位必须设为0
                    sendBuffer[13] = (sendBuffer[13] and 0x7F) //S=0，不是第一个包
                    sendBuffer[13] = (sendBuffer[13].toInt() and 0xBF).toByte() //E=0，不是最后一个包
                    sendBuffer[13] = (sendBuffer[13] or (r[0] and 0x1f)) //NALU头对应的type
                    System.arraycopy(r, t * packageSize+1 , sendBuffer, 14, packageSize) //不包含NALU头
                    //client.send(new DatagramPacket(sendbuf, packageSize + 14, addr, port/*9200*/));
                    //send(sendbuf,1414);
                    executeH264ToRtpListener(sendBuffer, packageSize + 14)
                    t++
                }
            }
        }
    }

    private fun addTimeStamp()
    {
        var timeStamp = ByteBuffer.allocate(4)
        timeStamp.putInt(System.currentTimeMillis().toInt())
        sendBuffer[4] = timeStamp[0]
        sendBuffer[5] = timeStamp[1]
        sendBuffer[6] = timeStamp[2]
        sendBuffer[7] = timeStamp[3]
    }

    companion object {
        private  val TAG = RtpPacketEncode::class.java.simpleName
        const val DEFAULT_FRAME_RATE = 30
        const val DEFAULT_BUFFER_SIZE = 1500
        const val DEFAULT_PACKAGE_SIZE = 1400
        const val RTP_SAMPLING_RATE = 90_000.0
        const val SHORT_MAX = 65536


    }
}