//package com.example.screen_rec2
//
//
//import android.app.*
//import android.content.Context
//import android.content.Intent
//import android.graphics.PixelFormat
//import android.hardware.display.DisplayManager
//import android.hardware.display.VirtualDisplay
//import android.media.MediaCodec
//import android.media.MediaCodecInfo
//import android.media.MediaFormat
//import android.media.projection.MediaProjection
//import android.media.projection.MediaProjectionManager
//import android.os.*
//import android.util.Log
//import android.view.Surface
//import androidx.core.app.NotificationCompat
//import io.flutter.plugin.common.MethodChannel
//import io.flutter.embedding.engine.FlutterEngine
//import io.flutter.embedding.engine.dart.DartExecutor
//import java.io.ByteArrayOutputStream
//import java.nio.ByteBuffer
//import java.util.*
//
//class MediaCodecScreenRecorderService : Service() {
//    private lateinit var mediaProjection: MediaProjection
//    private lateinit var virtualDisplay: VirtualDisplay
//    private lateinit var codec: MediaCodec
//    private lateinit var surface: Surface
//    private lateinit var engine: FlutterEngine
//    private lateinit var channel: MethodChannel
//    private val TAG = "MediaCodecService"
//
//    private val VIDEO_WIDTH = 720
//    private val VIDEO_HEIGHT = 1280
//    private val VIDEO_BITRATE = 2_000_000
//    private val VIDEO_FRAME_RATE = 30
//
//    private val buffer = ByteArrayOutputStream()
//    private val CHUNK_SIZE = 5 * 1024 * 1024
//
//    override fun onBind(intent: Intent?): IBinder? = null
//
//    override fun onCreate() {
//        super.onCreate()
//        startForegroundService()
//    }
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        val resultCode = intent?.getIntExtra("resultCode", -1) ?: return START_NOT_STICKY
//        val resultData = intent.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY
//
//        val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
//        mediaProjection = pm.getMediaProjection(resultCode, resultData)
//        mediaProjection.registerCallback(object : MediaProjection.Callback() {
//            override fun onStop() {
//                super.onStop()
//                stopSelf()
//            }
//        }, Handler(Looper.getMainLooper()))
//        // Set up Flutter Engine
//        engine = FlutterEngine(this)
//        engine.dartExecutor.executeDartEntrypoint(DartExecutor.DartEntrypoint.createDefault())
//        channel = MethodChannel(engine.dartExecutor.binaryMessenger, "screen_recorder")
//
//        startCodec()
//        return START_STICKY
//    }
//
//    private fun startCodec() {
//        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
//            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
//            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
//            setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FRAME_RATE)
//            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
//        }
//
//        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
//        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
//        surface = codec.createInputSurface()
//        codec.start()
//
//        virtualDisplay = mediaProjection.createVirtualDisplay(
//            "CodecDisplay",
//            VIDEO_WIDTH,
//            VIDEO_HEIGHT,
//            resources.displayMetrics.densityDpi,
//            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
//            surface,
//            null,
//            null
//        )
//
//        Thread { encodeLoop() }.start()
//    }
//
//    private fun encodeLoop() {
//        val bufferInfo = MediaCodec.BufferInfo()
//
//        while (true) {
//            val index = codec.dequeueOutputBuffer(bufferInfo, 10000)
//            if (index >= 0) {
//                val encodedData = codec.getOutputBuffer(index) ?: continue
//                val chunk = ByteArray(bufferInfo.size)
//                encodedData.get(chunk)
//                encodedData.clear()
//
//                buffer.write(chunk)
//                codec.releaseOutputBuffer(index, false)
//
//                if (buffer.size() >= CHUNK_SIZE) {
//                    sendChunkToFlutter(buffer.toByteArray())
//                    buffer.reset()
//                }
//            }
//        }
//    }
//
//    private fun sendChunkToFlutter(chunk: ByteArray) {
//        Handler(Looper.getMainLooper()).post {
//            channel.invokeMethod("chunkReady", chunk)
//        }
//    }
//
//    private fun startForegroundService() {
//        val channelId = "recording_channel"
//        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val chan = NotificationChannel(channelId, "Screen Recording", NotificationManager.IMPORTANCE_LOW)
//            notificationManager.createNotificationChannel(chan)
//        }
//
//        val notification = NotificationCompat.Builder(this, channelId)
//            .setContentTitle("Recording Interview")
//            .setContentText("Screen recording is active")
//            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
//            .build()
//
//        startForeground(101, notification)
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        codec.stop()
//        codec.release()
//        virtualDisplay.release()
//        mediaProjection.stop()
//        engine.destroy()
//    }
//}



// File: MediaCodecScreenRecorderService.kt
package com.example.screen_rec2

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Log
import android.view.Surface
import androidx.core.app.NotificationCompat
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class MediaCodecScreenRecorderService : Service() {
    private lateinit var mediaProjection: MediaProjection
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var codec: MediaCodec
    private lateinit var surface: Surface
    private lateinit var engine: FlutterEngine
    private lateinit var channel: MethodChannel

    private val TAG = "MediaCodecService"
    private val VIDEO_WIDTH = 720
    private val VIDEO_HEIGHT = 1280
    private val VIDEO_BITRATE = 2_000_000
    private val VIDEO_FRAME_RATE = 30

    private val buffer = ByteArrayOutputStream()
    private val CHUNK_SIZE = 5 * 1024 * 1024

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", -1) ?: return START_NOT_STICKY
        val resultData = intent.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY

        val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = pm.getMediaProjection(resultCode, resultData)

        // ✅ Must register a callback before starting capture
        mediaProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, Handler(Looper.getMainLooper()))

        // Setup FlutterEngine
        engine = FlutterEngine(this)
        engine.dartExecutor.executeDartEntrypoint(DartExecutor.DartEntrypoint.createDefault())
        channel = MethodChannel(engine.dartExecutor.binaryMessenger, "screen_recorder")

        startCodec()
        return START_STICKY
    }

    private fun startCodec() {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = codec.createInputSurface()
        codec.start()

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "CodecDisplay",
            VIDEO_WIDTH,
            VIDEO_HEIGHT,
            resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null,
            null
        )

        Thread { encodeLoop() }.start()
    }

    private fun encodeLoop() {
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val index = codec.dequeueOutputBuffer(bufferInfo, 10000)
            if (index >= 0) {
                val encodedData = codec.getOutputBuffer(index) ?: continue
                val chunk = ByteArray(bufferInfo.size)
                encodedData.get(chunk)
                encodedData.clear()

                buffer.write(chunk)
                codec.releaseOutputBuffer(index, false)

                if (buffer.size() >= CHUNK_SIZE) {
                    val chunkBytes = buffer.toByteArray()
                    sendChunkToFlutter(chunkBytes)
                    buffer.reset()
                }
            }
        }
    }

    private fun sendChunkToFlutter(chunk: ByteArray) {
        Handler(Looper.getMainLooper()).post {
            channel.invokeMethod("chunkReady", chunk)
        }
    }

    private fun startForegroundService() {
        val channelId = "recording_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, "Screen Recording", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(chan)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Recording Interview")
            .setContentText("Screen recording is active")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()

        startForeground(101, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            codec.stop()
            codec.release()
            virtualDisplay.release()
            mediaProjection.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources", e)
        } finally {
            engine.destroy()
        }
    }
}
