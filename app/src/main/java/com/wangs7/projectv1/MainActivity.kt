package com.wangs7.projectv1

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.wangs7.projectv1.camera2.Camera2Tools
import com.wangs7.projectv1.camera2.getPreviewOutputSize
import com.wangs7.projectv1.codec.VideoEncoder
import com.wangs7.projectv1.controller.SendController
import com.wangs7.projectv1.databinding.ActivityMainBinding
import com.wangs7.projectv1.network.RtpSender


private const val PERMISSIONS_REQUEST_CODE = 10
private val PERMISSIONS_REQUIRED = arrayOf(Manifest.permission.CAMERA)

class MainActivity : AppCompatActivity() {


    /** 这是test01 **/
    private lateinit var binding:ActivityMainBinding
    private lateinit var characteristics: CameraCharacteristics
    private lateinit var camera2Tools: Camera2Tools
    private var cameraIndex = 0

    private var frameRate = 30
    private var bitrate = 5_000_000

    private var IP: String? = null
    private val port = 5004
    private var vWidth = mWidth
    private var vHeight = mHeight

    private var videoEncoder: VideoEncoder? = null
    private var rtpSender = RtpSender()
    //private var videoH264Data = ByteArray(vWidth * vHeight * 3)

    private var cameraState = STOP_STATE
    private var isRun: Boolean = false

    private var sendController = SendController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "========onCreate========")
        /** 请求相机权限 **/
        if (hasPermissions(this)) {
            // If permissions have already been granted, proceed
            Log.d(TAG, "Permissions have already been granted.")
        } else {
            // Request camera-related permissions
            requestPermissions(PERMISSIONS_REQUIRED, PERMISSIONS_REQUEST_CODE)
        }
        /** 绑定布局 **/
        binding = ActivityMainBinding.inflate(layoutInflater)
        /** 初始化摄像头工具 **/
        camera2Tools = Camera2Tools(this, binding.autoSurfaceView)
        var cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // 设置摄像头id
        camera2Tools.setCameraId(cameraManager.cameraIdList[cameraIndex])
        // 获取摄像头参数
        characteristics = cameraManager.getCameraCharacteristics(cameraManager.cameraIdList[cameraIndex])

        /** 初始化编码器 **/
        videoEncoder = VideoEncoder()
        videoEncoder!!.setSize(vHeight, vWidth)
        videoEncoder!!.setBitRate(bitrate)
        videoEncoder!!.setFrameRate(frameRate)
        videoEncoder!!.start()


        /** 设置摄像头数据监听回调 **/
        camera2Tools.setImageDataListener(object : Camera2Tools.ImageDataListener {
            override fun onImageDataListener(data: ByteArray?) {
                if (data == null) {
                    Log.e(TAG, "OnImageDataListener: data is null!")
                } else if (videoEncoder == null) {
                    Log.e(TAG, "OnImageDataListener: avcEncoder is null!")
                } else {
                    /** 数据处理 **/
                    videoEncoder!!.inputFrameToEncoder(data)
                }
            }
        })

        /** 设置edit **/
        editSettings(binding)

        /** 设置预览回调 **/
        binding.autoSurfaceView.holder.addCallback(object : SurfaceHolder.Callback{
            override fun surfaceCreated(holder: SurfaceHolder) {
                val previewSize = getPreviewOutputSize(
                    binding.autoSurfaceView.display,
                    characteristics,
                    SurfaceHolder::class.java
                )
                Log.d(TAG, "View finder size: ${binding.autoSurfaceView.width} x ${binding.autoSurfaceView.height}")
                Log.d(TAG, "Selected preview size: $previewSize")
                binding.autoSurfaceView.setAspectRatio(
                    previewSize.width,
                    previewSize.height
                )
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) = Unit

            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
        })


        /** 设置按钮响应 **/
        binding.button.setOnClickListener {
            /** 开始 **/
            if (binding.button.text == this.getString(R.string.start)) {
                binding.button.text = this.getString(R.string.stop)

                if (cameraState == START_STATE) return@setOnClickListener
                binding.ipInput.isEnabled = false
                Log.d(TAG, "Button.Click start... ${binding.ipInput.text}")
                IP = binding.ipInput.text.toString()
                isRun = true
                videoEncoder!!.setSendInfo(rtpSender)
                camera2Tools.startCamera2(mWidth, mHeight)
                rtpSender.setAddress(IP, port)
                rtpSender.start()
                sendController.start()

                /** 循环调节码率 **/
                Thread {
                    var i = 0
                    var n = intArrayOf(5_000, 50_000, 500_000, 5_000_000)
                    while (isRun) {
                        i = (i+1) % 4
                        videoEncoder!!.changeBitRate(n[i])

                        Thread.sleep(20_000)
                        Log.e(TAG, "changeBitRate n = ${n[i]}")
                    }
                }//.start()

                cameraState = START_STATE

            } else { /** 停止 **/
                binding.button.text = this.getString(R.string.start)
                if (cameraState == STOP_STATE) return@setOnClickListener
                binding.ipInput.isEnabled = true
                camera2Tools.closeCamera()
                sendController.stop()
                rtpSender.close()
                videoEncoder!!.clear()
                cameraState = STOP_STATE
                isRun = false
                Log.d(TAG, "Button.Click stop...")

            }
        }

        setContentView(binding.root)
    }


    override fun onStart() {
        super.onStart()
        Log.d(TAG, "========onStart========")
    }


    override fun onResume() {
        super.onResume()
        Log.d(TAG, "========onResume========")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "========onPause========")

        if (cameraState != STOP_STATE) {
            camera2Tools.closeCamera()
            cameraState = STOP_STATE

        }
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "========onStop========")

        if (videoEncoder != null){
            videoEncoder!!.close()
            //videoEncoder = null
        }
        /** RTP **/
        if (isRun) {
            binding.button.text = this.getString(R.string.start)
            rtpSender.close()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "========onDestroy========")

        if (videoEncoder != null){
            videoEncoder!!.close()
            videoEncoder = null
        }
        /** RTP **/
        if (isRun) {
            rtpSender.close()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun editSettings(binding: ActivityMainBinding) {
        // 限制输入类型
        binding.ipInput.inputType = InputType.TYPE_CLASS_NUMBER
        val digits = "0123456789."
        binding.ipInput.keyListener = DigitsKeyListener.getInstance(digits)
        // 监听点击edit的事件
        binding.ipInput.setOnTouchListener { _, event
            -> if (MotionEvent.ACTION_DOWN == event.action) {
            binding.ipInput.isCursorVisible = true // 再次点击显示光标
        }
            false }
        // 监听键盘回车
        binding.ipInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                binding.ipInput.isCursorVisible = false
            }
            false
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null) {
            if (ev.action == MotionEvent.ACTION_DOWN) {
                // 获得当前得到焦点的View，一般情况下就是EditText（特殊情况就是轨迹求或者实体案件会移动焦点）
                val v = currentFocus;
                if (isShouldHideInput(v, ev)) {
                    if (v != null) {
                        hideSoftInput(v)
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
    // 隐藏软键盘
    private fun hideSoftInput(v: View?) {
        if (v != null && (v is EditText)) {
            if (v.windowToken != null) run {
                v.isCursorVisible = false
                val im: InputMethodManager =
                    getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                im.hideSoftInputFromWindow(v.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
            }
        }
    }
    // 判断点击位置
    private fun isShouldHideInput(v: View?, ev: MotionEvent): Boolean {
        if (v != null && (v is EditText)) {
            val l = IntArray(2){ 0 }
            v.getLocationInWindow(l)
            val left = l[0]
            val top = l[1]
            val bottom = top + v.height
            val right = left + v.width
            return !(ev.x > left && ev.x < right
                    && ev.y > top && ev.y < bottom)
        }
        return false //不是
    }



    companion object {
        private var TAG = MainActivity::class.java.simpleName
        private const val mWidth = 720
        private const val mHeight = 480//540
//        private const val mWidth = 1440
//        private const val mHeight = 1080//540
        private const val START_STATE = 0
        private const val STOP_STATE = 1
        private const val PAUSE_STATE = 2
        private const val PLAY_STATE = 3

        /** Convenience method used to check if all permissions required by this app are granted */
        fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    }

}