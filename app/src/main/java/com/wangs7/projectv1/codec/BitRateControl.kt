package com.wangs7.projectv1.codec

/**
 * @version:
 * @author: wangs7__
 * @className:
 * @packageName:
 * @description:
 * @date: 2022/5/10 19:06
 **/
interface BitRateControl {
    fun getBitRate():Int
    fun changeBitRate(newBitRate:Int)
}