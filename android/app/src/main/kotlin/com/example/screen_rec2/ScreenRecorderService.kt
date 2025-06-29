package com.example.screen_rec2

import android.app.*
import android.content.*
import android.media.*
import android.media.projection.*
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import io.flutter.plugin.common.MethodChannel
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor
import java.io.File
import java.util.*
import android.hardware.display.VirtualDisplay
import android.hardware.display.DisplayManager

class ScreenRecorderService : Service() {
    private lateinit var mediaProjection: MediaProjection
    private lateinit var projectionManager: MediaProjectionManager
    private var timer: Timer? = null
    private lateinit var mediaRecorder: MediaRecorder
    private var engine: FlutterEngine? = null
    private var channel: MethodChannel? = null
    private var virtualDisplay: VirtualDisplay? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "screen_recording_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Recording", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Recording Interview")
            .setContentText("Recording screen in progress...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)

        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_OK) ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY



        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        mediaProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                stopSelf() // clean up the service
            }
        }, Handler(Looper.getMainLooper()))

        engine = FlutterEngine(this)
        engine!!.dartExecutor.executeDartEntrypoint(
            DartExecutor.DartEntrypoint.createDefault()
        )
        channel = MethodChannel(engine!!.dartExecutor.binaryMessenger, "screen_recorder")

        startChunk()

        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                stopChunk()
                startChunk()
            }
        }, 100000, 100000)

        return START_STICKY
    }

    private fun startChunk() {
        val file = File(getExternalFilesDir(null), "chunk_${System.currentTimeMillis()}.mp4")

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.DEFAULT)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(file.absolutePath)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoEncodingBitRate(1024 * 1024)
            setVideoFrameRate(30)
            setVideoSize(720, 1280)
            prepare()
            start()
        }

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenRecorder",
            720,
            1280,
            resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder.surface,
            null,
            null
        )

        Handler(Looper.getMainLooper()).post {
            channel?.invokeMethod("chunkReady", file.absolutePath)
        }
    }

    private fun stopChunk() {
        try {
            mediaRecorder.stop()
        } catch (e: Exception) {
            Log.e("ScreenRecorderService", "Stop error: ${e.message}")
            return
        }

        mediaRecorder.reset()
        mediaRecorder.release()

        virtualDisplay?.release()
        virtualDisplay = null

        // Delay to ensure file system has flushed write buffers
        Handler(Looper.getMainLooper()).postDelayed({
            val lastFile = File(getExternalFilesDir(null), "chunk_${System.currentTimeMillis()}.mp4")
            if (lastFile.exists() && lastFile.length() > 1024 * 1024) {
                channel?.invokeMethod("chunkReady", lastFile.absolutePath)
            } else {
                Log.w("ScreenRecorderService", "Skipped small/corrupt file: ${lastFile.length()} bytes")
            }
        }, 15) // Wait 1.5 seconds before uploading
    }

    override fun onDestroy() {
        super.onDestroy()
        stopChunk()
        mediaProjection.stop()
        timer?.cancel()
        engine?.destroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}