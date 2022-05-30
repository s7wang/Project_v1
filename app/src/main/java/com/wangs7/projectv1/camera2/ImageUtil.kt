package com.wangs7.projectv1.camera2

import androidx.annotation.RequiresApi
import android.os.Build
import android.media.Image.Plane
import android.graphics.ImageFormat
import android.media.Image
import android.util.Log
import java.lang.Exception



object ImageUtil {
    const val YUV420P = 0
    const val YUV420SP = 1
    const val NV21 = 2
    private  val TAG = ImageUtil::class.java.simpleName

    fun getBytesFromImageAsType(image: Image?, type: Int): ByteArray? {
        try {
            //Get the source data, if it is YUV format data planes.length = 3
            val planes = image!!.planes

            //Data effective width, in general, image width <= rowStride, which is also the reason for byte []. Length <= capacity
            // So we only take the width part
            val width = image.width
            val height = image.height
            Log.i(TAG, "image width = " + image.width + "; image height = " + image.height)

            //This is used to fill the final YUV data, which requires 1.5 times the picture size, because the YUV ratio is 4: 1: 1
            val yuvBytes =
                ByteArray(width * height * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8)
            //The position to which the target array is filled
            var dstIndex = 0

            //Temporary storage of uv data
            val uBytes = ByteArray(width * height / 4)
            val vBytes = ByteArray(width * height / 4)
            var uIndex = 0
            var vIndex = 0
            var pixelsStride: Int
            var rowStride: Int
            for (i in planes.indices) {
                pixelsStride = planes[i].pixelStride
                rowStride = planes[i].rowStride
                val buffer = planes[i].buffer

                //The index of the source data. The data of y is continuous in byte. The data of u is shifted to the left. It is assumed that both are even-numbered bits.
                val bytes = ByteArray(buffer.capacity())
                buffer[bytes]
                var srcIndex = 0
                if (i == 0) {
                    //Take out all the valid areas of Y directly, or store them as a temporary byte, and then copy it to the next step.
                    for (j in 0 until height) {
                        System.arraycopy(bytes, srcIndex, yuvBytes, dstIndex, width)
                        srcIndex += rowStride
                        dstIndex += width
                    }
                } else if (i == 1) {
                    //Take corresponding data according to pixelsStride
                    for (j in 0 until height / 2) {
                        for (k in 0 until width / 2) {
                            uBytes[uIndex++] = bytes[srcIndex]
                            srcIndex += pixelsStride
                        }
                        if (pixelsStride == 2) {
                            srcIndex += rowStride - width
                        } else if (pixelsStride == 1) {
                            srcIndex += rowStride - width / 2
                        }
                    }
                } else if (i == 2) {
                    //Take corresponding data according to pixelsStride
                    for (j in 0 until height / 2) {
                        for (k in 0 until width / 2) {
                            vBytes[vIndex++] = bytes[srcIndex]
                            srcIndex += pixelsStride
                        }
                        if (pixelsStride == 2) {
                            srcIndex += rowStride - width
                        } else if (pixelsStride == 1) {
                            srcIndex += rowStride - width / 2
                        }
                    }
                }
            }
            when (type) {
                YUV420P -> {
                    System.arraycopy(uBytes, 0, yuvBytes, dstIndex, uBytes.size)
                    System.arraycopy(vBytes, 0, yuvBytes, dstIndex + uBytes.size, vBytes.size)
                }
                YUV420SP -> {
                    var i = 0
                    while (i < vBytes.size) {
                        yuvBytes[dstIndex++] = uBytes[i]
                        yuvBytes[dstIndex++] = vBytes[i]
                        i++
                    }
                }
                NV21 -> {
                    var i = 0
                    while (i < vBytes.size) {
                        yuvBytes[dstIndex++] = vBytes[i]
                        yuvBytes[dstIndex++] = uBytes[i]
                        i++
                    }
                }
            }
            return yuvBytes
        } catch (e: Exception) {
            image?.close()
            Log.e(TAG, e.toString())
        }
        return null
    }



    fun rotateYUVDegree90(data: ByteArray, imageWidth: Int, imageHeight: Int): ByteArray {
        val yuv = ByteArray(imageWidth * imageHeight * 3 / 2)
        // Rotate the Y luma
        var i = 0
        for (x in 0 until imageWidth) {
            for (y in imageHeight - 1 downTo 0) {
                yuv[i] = data[y * imageWidth + x]
                i++
            }
        }
        // Rotate the U and V color components
        i = imageWidth * imageHeight * 3 / 2 - 1
        var x = imageWidth - 1
        while (x > 0) {
            for (y in 0 until imageHeight / 2) {
                yuv[i] = data[imageWidth * imageHeight + y * imageWidth + x]
                i--
                yuv[i] = data[imageWidth * imageHeight + y * imageWidth + (x - 1)]
                i--
            }
            x -= 2
        }
        return yuv
    }


}