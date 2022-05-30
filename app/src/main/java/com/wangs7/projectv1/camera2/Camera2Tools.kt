package com.wangs7.projectv1.camera2

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.SurfaceView
import com.wangs7.projectv1.camera2.ImageUtil.rotateYUVDegree90
import java.lang.RuntimeException
import java.util.concurrent.Semaphore

/**
 * @version:
 * @author: wangs7__
 * @className:
 * @packageName:
 * @description:
 * @date: 2022/2/16 16:44
 **/
class Camera2Tools (private val mContext: Context, var surfaceView: AutoFitSurfaceView) {

    private var mCameraDevice: CameraDevice? = null
    private var mCaptureSession: CameraCaptureSession? = null
    private var mPreviewRequestBuilder: CaptureRequest.Builder? = null

    private var mImageReader: ImageReader? = null
    private var mImageReaderThread: HandlerThread? = null
    private var mImageReaderHandler: Handler? = null
    private var mImageDataListener: ImageDataListener? = null

    private val mCameraOpenCloseLock = Semaphore(1)

    private var mCameraId = "0"

    private val mStateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.i(TAG, "CameraDevice.StateCallback onOpened()")
            mCameraOpenCloseLock.release()
            mCameraDevice = camera

            createCameraPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.i(TAG, "CameraDevice.StateCallback onDisconnected()")
            mCameraOpenCloseLock.release()
            camera.close()
            mCameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.i(TAG, "CameraDevice.StateCallback onError()")
            mCameraOpenCloseLock.release()
            camera.close()
            mCameraDevice = null
        }

    }

    private fun createCameraPreviewSession() {
        try {
            val imageSurface = mImageReader!!.surface
            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            mPreviewRequestBuilder!!.addTarget(imageSurface)
            mPreviewRequestBuilder!!.addTarget(surfaceView.holder.surface)
            val list = listOf(imageSurface, surfaceView.holder.surface)
            mCameraDevice!!.createCaptureSession( list, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    Log.i(TAG, "onConfigured")
                    // The camera is already closed
                    if (null == mCameraDevice) {
                        return
                    }
                    mCaptureSession = session
                    mPreviewRequestBuilder!!.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

                    try {
                        mCaptureSession!!.setRepeatingRequest(
                            mPreviewRequestBuilder!!.build(),
                            object : CameraCaptureSession.CaptureCallback() {
                                override fun onCaptureStarted(
                                    session: CameraCaptureSession,
                                    request: CaptureRequest,
                                    timestamp: Long,
                                    frameNumber: Long
                                ) {
                                    super.onCaptureStarted(session, request, timestamp, frameNumber)
                                    }
                                }, mImageReaderHandler)
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.i(TAG, "onConfigureFailed")
                } }, null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    fun startCamera2(width: Int, height: Int) {
        Log.i(TAG, "startCamera2()")
        startImageReaderThread()
        setUpCameraOutputs(width, height)
        val cameraManager = mContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            cameraManager.openCamera(mCameraId, mStateCallback, mImageReaderHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "startCamera2 error: " + e.message)
        }
    }


    fun playCamera() {
        Log.i(TAG, "playCamera")
        try {
            mCaptureSession!!.setRepeatingRequest(
                mPreviewRequestBuilder!!.build(),
                null,
                mImageReaderHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    fun pauseCamera() {
        Log.i(TAG, "pauseCamera")
        try {
            mCaptureSession!!.stopRepeating()
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    fun closeCamera() {
        Log.i(TAG, "closeCamera")
        try {
            mCameraOpenCloseLock.acquire()
            if (mCaptureSession != null) {
                mCaptureSession!!.close()
                mCaptureSession = null
            }
            if (mCameraDevice != null) {
                mCameraDevice!!.close()
                mCameraDevice = null
            }
            if (mImageReader != null) {
                mImageReader!!.close()
                mImageReader = null
            }
            clearSurface(surfaceView)

        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            mCameraOpenCloseLock.release()
        }
    }

    private fun setUpCameraOutputs(width: Int, height: Int) {
        Log.i(TAG, "setUpCameraOutputs start")
        mImageReader =
            ImageReader.newInstance(width, height, ImageFormat.YUV_420_888,  /*maxImages*/2)
        mImageReader!!.setOnImageAvailableListener(
            RTPOnImageAvailableListener(),
            mImageReaderHandler
        )
        return
    }
    private fun clearSurface(surface: SurfaceView) {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(display, version, 0, version, 1)
        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE, 0,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(display, attribList, 0, configs, 0, configs.size, numConfigs, 0)
        val config = configs[0]
        val context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            ), 0
        )
        val eglSurface = EGL14.eglCreateWindowSurface(
            display, config, surface, intArrayOf(
                EGL14.EGL_NONE
            ), 0
        )
        EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        EGL14.eglSwapBuffers(display, eglSurface)
        EGL14.eglDestroySurface(display, eglSurface)
        EGL14.eglMakeCurrent(
            display,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_CONTEXT
        )
        EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)
    }

    private fun startImageReaderThread() {
        mImageReaderThread = HandlerThread("CameraBackground")
        mImageReaderThread!!.start()
        mImageReaderHandler = Handler(mImageReaderThread!!.looper)
    }

    fun setCameraId(id:String) {
        mCameraId = id
    }
    private inner class RTPOnImageAvailableListener : ImageReader.OnImageAvailableListener {
        override fun onImageAvailable(reader: ImageReader) {
            //Log.i(TAG, "onImageAvailable")
            val readImage = reader.acquireNextImage()
            // 图片数据处理
            
            val data: ByteArray? = ImageUtil.getBytesFromImageAsType(readImage, ImageUtil.YUV420SP)
            val data2: ByteArray? = data?.let { rotateYUVDegree90(it, readImage.width, readImage.height) }
            readImage.close()

            mImageDataListener!!.onImageDataListener(data2)
        }
    }


    fun setImageDataListener(listener: ImageDataListener?) {
        mImageDataListener = listener
    }

    interface ImageDataListener {
        fun onImageDataListener(reader: ByteArray?)
    }

    companion object {
        private val TAG = Camera2Tools::class.java.simpleName
    }
}