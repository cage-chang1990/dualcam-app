package com.swapna.camera2sample

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.FileDescriptor
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DualCamActivity"
    }

    // Camera devices
    private var mainCameraDevice: CameraDevice? = null
    private var pipCameraDevice: CameraDevice? = null

    // Camera IDs
    private lateinit var mainCameraId: String
    private lateinit var pipCameraId: String

    // All available rear cameras
    private val rearCameraIds = mutableListOf<CameraInfo>()
    private var currentRearCameraIndex = 0

    data class CameraInfo(
        val id: String,
        val focalLength: Float,
        val displayName: String
    )

    // Capture request builders
    private lateinit var mainCaptureRequestBuilder: CaptureRequest.Builder
    private lateinit var pipCaptureRequestBuilder: CaptureRequest.Builder

    // Preview sizes
    private lateinit var mainPreviewSize: Size
    private lateinit var pipPreviewSize: Size

    // Preview surfaces
    private var mainPreviewSurface: Surface? = null
    private var pipPreviewSurface: Surface? = null

    // TextureViews
    private lateinit var mainTextureView: TextureView
    private lateinit var pipTextureView: TextureView

    // Background threads and handlers
    private var backgroundHandlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var backgroundHandlerThreadPip: HandlerThread? = null
    private var backgroundHandlerPip: Handler? = null

    // Camera managers
    private val cameraManager: CameraManager by lazy {
        getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    // MediaRecorders
    private var mediaRecorderMain: MediaRecorder? = null
    private var mediaRecorderPip: MediaRecorder? = null

    // Recording state
    private var isRecording = false
    private var recordingStartTime = 0L

    // Timer
    private var timerHandler: Handler? = null
    private var timerRunnable: Runnable? = null
    private var seconds = 0

    // UI elements
    private lateinit var btnRecord: ImageButton
    private lateinit var tvTimer: TextView
    private lateinit var recordingIndicator: LinearLayout
    private lateinit var recordingDot: View
    private lateinit var waveformView: WaveformView
    private lateinit var tvCameraLabel: TextView
    private var recordingPulseRunnable: Runnable? = null

    // Camera facing
    private var isMainFront = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        try {
            initViews()
            initListeners()

            if (!CameraPermissionHelper.hasAllPermissions(this)) {
                CameraPermissionHelper.requestAllPermissions(this)
                return
            }

            startBackgroundThread()
            initializeTextureViews()
            detectRearCameras()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "初始化错误: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun initViews() {
        mainTextureView = findViewById(R.id.textureViewMain)
        pipTextureView = findViewById(R.id.textureViewPip)
        btnRecord = findViewById(R.id.btnRecord)
        tvTimer = findViewById(R.id.tvTimer)
        recordingIndicator = findViewById(R.id.recordingIndicator)
        recordingDot = findViewById(R.id.recordingDot)
        waveformView = findViewById(R.id.waveformView)
        tvCameraLabel = findViewById(R.id.tvCameraLabel)
    }

    private fun initListeners() {
        btnRecord.setOnClickListener {
            toggleRecording()
        }

        findViewById<ImageButton>(R.id.btnSwitchCamera).setOnClickListener {
            switchToNextRearCamera()
        }
    }

    private fun initializeTextureViews() {
        mainTextureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                openMainCamera()
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }

        pipTextureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                openPipCamera()
            }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }

    private fun detectRearCameras() {
        rearCameraIds.clear()
        val allCameraIds = cameraManager.cameraIdList

        Toast.makeText(this, "检测到${allCameraIds.size}个摄像头", Toast.LENGTH_LONG).show()

        Log.d(TAG, "=== Camera Detection ===")
        Log.d(TAG, "Total camera IDs: ${allCameraIds.size}")

        for (cameraId in allCameraIds) {
            try {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)

                val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)

                // Try to get physical camera IDs if this is a logical multi-camera
                val physicalIds = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    characteristics.physicalCameraIds
                } else {
                    emptySet()
                }

                Log.d(TAG, "Camera $cameraId: facing=$facing, focalLengths=${focalLengths?.toList()}, physicalCameras=${physicalIds.size}")

                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    if (physicalIds.isNotEmpty()) {
                        // Logical multi-camera - add all physical cameras
                        for (physicalId in physicalIds) {
                            try {
                                val physicalChars = cameraManager.getCameraCharacteristics(physicalId)
                                val physicalFacing = physicalChars.get(CameraCharacteristics.LENS_FACING)
                                // Only add rear-facing physical cameras
                                if (physicalFacing != CameraCharacteristics.LENS_FACING_BACK) {
                                    Log.d(TAG, "  Skipping physical camera $physicalId - not rear facing")
                                    continue
                                }
                                val physicalFocals = physicalChars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                                val physicalFocal = physicalFocals?.firstOrNull() ?: 0f

                                val displayName = when {
                                    physicalFocal < 2f -> "广角"
                                    physicalFocal < 5f -> "中焦"
                                    else -> "长焦"
                                }

                                rearCameraIds.add(CameraInfo(physicalId, physicalFocal, displayName))
                                Log.d(TAG, "  Physical camera: $physicalId, focal=$physicalFocal, name=$displayName")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error getting physical camera $physicalId: $e")
                            }
                        }
                    } else {
                        // Single camera
                        val focalLength = focalLengths?.firstOrNull() ?: 0f
                        val displayName = when {
                            focalLength < 2f -> "广角"
                            focalLength < 5f -> "中焦"
                            else -> "长焦"
                        }
                        rearCameraIds.add(CameraInfo(cameraId, focalLength, displayName))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting camera $cameraId info: $e")
            }
        }

        rearCameraIds.sortBy { it.focalLength }

        Log.d(TAG, "Total rear cameras found: ${rearCameraIds.size}")
        for (cam in rearCameraIds) {
            Log.d(TAG, "  - ${cam.id}: ${cam.displayName} (focal: ${cam.focalLength})")
        }
        Log.d(TAG, "=====================")

        if (rearCameraIds.size > 1) {
            // Default to middle camera (main camera) - sorted by focal length
            currentRearCameraIndex = rearCameraIds.size / 2
            updateCameraLabel()
        } else if (rearCameraIds.size == 1) {
            Toast.makeText(this, "单摄像头模式", Toast.LENGTH_SHORT).show()
            updateCameraLabel()
        }
    }

    private fun updateCameraLabel() {
        if (rearCameraIds.isNotEmpty() && currentRearCameraIndex < rearCameraIds.size) {
            val cam = rearCameraIds[currentRearCameraIndex]
            val prefix = if (isMainFront) "前置/" else "后置/"
            tvCameraLabel.text = prefix + cam.displayName
            tvCameraLabel.visibility = View.VISIBLE
        } else {
            val prefix = if (isMainFront) "前置" else "后置"
            tvCameraLabel.text = prefix
            tvCameraLabel.visibility = View.VISIBLE
        }
    }

    fun switchToNextRearCamera() {
        if (isRecording) {
            Toast.makeText(this, "Recording in progress", Toast.LENGTH_SHORT).show()
            return
        }

        if (rearCameraIds.size <= 1) {
            // Try switching front/back instead
            isMainFront = !isMainFront
            val camType = if (isMainFront) "前置摄像头" else "后置摄像头"
            Toast.makeText(this, "切换到 $camType", Toast.LENGTH_SHORT).show()

            mainCameraDevice?.close()
            pipCameraDevice?.close()

            openMainCamera()
            openPipCamera()
            updateCameraLabel()
            return
        }

        currentRearCameraIndex = (currentRearCameraIndex + 1) % rearCameraIds.size
        val cam = rearCameraIds[currentRearCameraIndex]
        Toast.makeText(this, "切换到 ${cam.displayName}", Toast.LENGTH_SHORT).show()

        mainCameraDevice?.close()
        pipCameraDevice?.close()

        openMainCamera()
        openPipCamera()
        updateCameraLabel()
    }

    private fun openMainCamera() {
        val frontCameraId = if (isMainFront) findFrontCameraId() else null
        val rearCamera = rearCameraIds.getOrNull(currentRearCameraIndex)

        // Alternate between front and rear for main camera
        mainCameraId = if (isMainFront && frontCameraId != null) {
            frontCameraId
        } else {
            rearCamera?.id ?: findBackCameraId()
        }

        val characteristics = cameraManager.getCameraCharacteristics(mainCameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        mainPreviewSize = map?.getOutputSizes(SurfaceTexture::class.java)?.maxByOrNull {
            it.width * it.height
        } ?: Size(1920, 1080)

        val stateCallback = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                mainCameraDevice = camera
                createMainPreviewSession()
            }
            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                mainCameraDevice = null
            }
            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                mainCameraDevice = null
                Log.e(TAG, "Main camera error: $error")
            }
        }

        try {
            cameraManager.openCamera(mainCameraId, stateCallback, backgroundHandler)
        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission not granted")
        }
    }

    private fun openPipCamera() {
        // PiP camera is always the opposite of main camera
        pipCameraId = if (isMainFront) findBackCameraId() else findFrontCameraId()

        val characteristics = cameraManager.getCameraCharacteristics(pipCameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        pipPreviewSize = map?.getOutputSizes(SurfaceTexture::class.java)?.maxByOrNull {
            it.width * it.height
        } ?: Size(1920, 1080)

        val stateCallback = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                pipCameraDevice = camera
                createPipPreviewSession()
            }
            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                pipCameraDevice = null
            }
            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                pipCameraDevice = null
                Log.e(TAG, "PiP camera error: $error")
            }
        }

        try {
            cameraManager.openCamera(pipCameraId, stateCallback, backgroundHandlerPip)
        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission not granted")
        }
    }

    private fun findFrontCameraId(): String {
        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                return id
            }
        }
        return cameraManager.cameraIdList[0]
    }

    private fun findBackCameraId(): String {
        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                return id
            }
        }
        return cameraManager.cameraIdList[0]
    }

    private fun createMainPreviewSession() {
        val surfaceTexture = mainTextureView.surfaceTexture ?: return
        surfaceTexture.setDefaultBufferSize(mainPreviewSize.width, mainPreviewSize.height)
        mainPreviewSurface = Surface(surfaceTexture)

        mainCaptureRequestBuilder = mainCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        mainCaptureRequestBuilder.addTarget(mainPreviewSurface!!)

        val callback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e(TAG, "Main preview session configuration failed")
            }
            override fun onConfigured(session: CameraCaptureSession) {
                session.setRepeatingRequest(mainCaptureRequestBuilder.build(), null, backgroundHandler)
            }
        }

        mainCameraDevice?.createCaptureSession(
            listOf(mainPreviewSurface),
            callback,
            backgroundHandler
        )
    }

    private fun createPipPreviewSession() {
        val surfaceTexture = pipTextureView.surfaceTexture ?: return
        surfaceTexture.setDefaultBufferSize(pipPreviewSize.width, pipPreviewSize.height)
        pipPreviewSurface = Surface(surfaceTexture)

        pipCaptureRequestBuilder = pipCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        pipCaptureRequestBuilder.addTarget(pipPreviewSurface!!)

        val callback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e(TAG, "PiP preview session configuration failed")
            }
            override fun onConfigured(session: CameraCaptureSession) {
                session.setRepeatingRequest(pipCaptureRequestBuilder.build(), null, backgroundHandlerPip)
            }
        }

        pipCameraDevice?.createCaptureSession(
            listOf(pipPreviewSurface),
            callback,
            backgroundHandlerPip
        )
    }

    private fun toggleRecording() {
        if (isRecording) {
            stopDualRecording()
        } else {
            startDualRecording()
        }
    }

    private fun startDualRecording() {
        isRecording = true
        recordingStartTime = System.currentTimeMillis()
        seconds = 0

        btnRecord.setImageResource(R.drawable.ic_stop_new)
        tvTimer.visibility = View.VISIBLE
        waveformView.visibility = View.VISIBLE
        startTimer()

        recordingIndicator.visibility = View.VISIBLE
        startRecordingPulse()

        startAudioLevelMonitoring()

        setupAndStartRecorder(true)
        setupAndStartRecorder(false)

        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
    }

    private fun stopDualRecording() {
        isRecording = false
        stopTimer()
        stopRecordingPulse()
        stopAudioLevelMonitoring()

        try {
            mediaRecorderMain?.stop()
            mediaRecorderPip?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }

        mediaRecorderMain?.reset()
        mediaRecorderPip?.reset()

        btnRecord.setImageResource(R.drawable.ic_record_new)
        tvTimer.visibility = View.GONE
        tvTimer.text = "00:00"
        recordingIndicator.visibility = View.GONE
        waveformView.visibility = View.GONE
        waveformView.clear()

        mainCameraDevice?.let { createMainPreviewSession() }
        pipCameraDevice?.let { createPipPreviewSession() }

        Toast.makeText(this, "Recordings saved", Toast.LENGTH_SHORT).show()
    }

    private fun setupAndStartRecorder(isMain: Boolean) {
        val recorder = MediaRecorder()
        val cameraDevice = if (isMain) mainCameraDevice else pipCameraDevice
        val previewSurface = if (isMain) mainPreviewSurface else pipPreviewSurface
        val handler = if (isMain) backgroundHandler else backgroundHandlerPip

        recorder.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoSize(1920, 1080)
            setVideoFrameRate(30)
            setVideoEncodingBitRate(10_000_000)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44100)

            val isFrontCamera = if (isMain) isMainFront else !isMainFront
            setOrientationHint(if (isFrontCamera) 270 else 90)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                setOutputFile(getFilePath(this@MainActivity, isMain))
            } else {
                setOutputFile(getFileDescriptor(this@MainActivity, isMain))
            }

            prepare()
        }

        if (isMain) {
            mediaRecorderMain = recorder
        } else {
            mediaRecorderPip = recorder
        }

        val recordingSurface = recorder.surface
        val captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        captureRequestBuilder.addTarget(previewSurface!!)
        captureRequestBuilder.addTarget(recordingSurface)

        val callback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e(TAG, "Recording session configuration failed")
            }
            override fun onConfigured(session: CameraCaptureSession) {
                session.setRepeatingRequest(captureRequestBuilder.build(), null, handler)
                try {
                    recorder.start()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start recording", e)
                }
            }
        }

        cameraDevice.createCaptureSession(
            listOf(previewSurface, recordingSurface),
            callback,
            handler
        )
    }

    private fun startTimer() {
        timerHandler = Handler(Looper.getMainLooper())
        timerRunnable = object : Runnable {
            override fun run() {
                val hours = seconds / 3600
                val minutes = (seconds % 3600) / 60
                val secs = seconds % 60

                val time = String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
                tvTimer.text = if (hours > 0) {
                    String.format(Locale.getDefault(), "%d:%s", hours, time)
                } else {
                    time
                }

                seconds++
                timerHandler?.postDelayed(this, 1000)
            }
        }
        timerHandler?.post(timerRunnable!!)
    }

    private fun stopTimer() {
        timerRunnable?.let { timerHandler?.removeCallbacks(it) }
        timerHandler = null
        timerRunnable = null
        seconds = 0
    }

    private fun startRecordingPulse() {
        recordingPulseRunnable = object : Runnable {
            override fun run() {
                recordingDot.animate()
                    .alpha(0.3f)
                    .setDuration(500)
                    .withEndAction {
                        recordingDot.animate()
                            .alpha(1f)
                            .setDuration(500)
                            .withEndAction {
                                timerHandler?.postDelayed(this, 0)
                            }
                            .start()
                    }
                    .start()
            }
        }
        timerHandler?.post(recordingPulseRunnable!!)
    }

    private fun stopRecordingPulse() {
        recordingPulseRunnable?.let { timerHandler?.removeCallbacks(it) }
        recordingPulseRunnable = null
        recordingDot.alpha = 1f
    }

    private var audioLevelRunnable: Runnable? = null

    private fun startAudioLevelMonitoring() {
        audioLevelRunnable = object : Runnable {
            override fun run() {
                if (isRecording) {
                    try {
                        val amplitude = mediaRecorderMain?.maxAmplitude ?: 0
                        waveformView.updateAmplitude(amplitude)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting amplitude", e)
                    }
                    timerHandler?.postDelayed(this, 50)
                }
            }
        }
        timerHandler?.post(audioLevelRunnable!!)
    }

    private fun stopAudioLevelMonitoring() {
        audioLevelRunnable?.let { timerHandler?.removeCallbacks(it) }
        audioLevelRunnable = null
    }

    @SuppressLint("SimpleDateFormat")
    private fun getFileDescriptor(context: Context, isMain: Boolean): FileDescriptor {
        val resolver: ContentResolver = context.contentResolver
        val videoCollection: Uri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val date = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val fileName = if (isMain) "Camera2Video_Main_$date.mp4" else "Camera2Video_PiP_$date.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/DualCam")
        }

        val uri = resolver.insert(videoCollection, values)
        val parcelFileDescriptor = resolver.openFileDescriptor(uri!!, "wt")!!
        return parcelFileDescriptor.fileDescriptor
    }

    private fun getFilePath(context: Context, isMain: Boolean): String {
        val directory = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            "DualCam"
        )
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val date = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val fileName = if (isMain) "Camera2Video_Main_$date.mp4" else "Camera2Video_PiP_$date.mp4"
        return File(directory, fileName).absolutePath
    }

    private fun startBackgroundThread() {
        backgroundHandlerThread = HandlerThread("CameraVideoThreadMain").also { it.start() }
        backgroundHandler = Handler(backgroundHandlerThread!!.looper)

        backgroundHandlerThreadPip = HandlerThread("CameraVideoThreadPiP").also { it.start() }
        backgroundHandlerPip = Handler(backgroundHandlerThreadPip!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundHandlerThread?.quitSafely()
        backgroundHandlerThread?.join()
        backgroundHandlerThreadPip?.quitSafely()
        backgroundHandlerThreadPip?.join()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (!CameraPermissionHelper.isAllPermissionsGranted(grantResults)) {
            Toast.makeText(this, "Camera and audio permissions are required", Toast.LENGTH_LONG).show()
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                CameraPermissionHelper.launchPermissionSettings(this)
            }
            finish()
            return
        }

        startBackgroundThread()
        initializeTextureViews()
        detectRearCameras()
    }

    override fun onResume() {
        super.onResume()
        if (CameraPermissionHelper.hasAllPermissions(this)) {
            startBackgroundThread()
            detectRearCameras()
            if (mainTextureView.isAvailable) {
                openMainCamera()
            }
            if (pipTextureView.isAvailable) {
                openPipCamera()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (isRecording) {
            stopDualRecording()
        }
        mainCameraDevice?.close()
        pipCameraDevice?.close()
        stopBackgroundThread()
    }
}
