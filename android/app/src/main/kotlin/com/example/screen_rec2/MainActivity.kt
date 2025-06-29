//package com.example.screen_rec2
//
//import android.app.Activity
//import android.content.Intent
//import android.media.projection.MediaProjectionManager
//import io.flutter.embedding.android.FlutterActivity
//import io.flutter.embedding.engine.FlutterEngine
//import io.flutter.plugin.common.MethodChannel
//
//class MainActivity : FlutterActivity() {
//    private val CHANNEL = "screen_recorder"
//    private val REQUEST_CODE = 1234
//    private var resultCallback: MethodChannel.Result? = null
//
//    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
//        super.configureFlutterEngine(flutterEngine)
//
//        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
//            if (call.method == "startRecording") {
//                val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
//                val intent = pm.createScreenCaptureIntent()
//                resultCallback = result
//                startActivityForResult(intent, REQUEST_CODE)
//            } else {
//                result.notImplemented()
//            }
//        }
//    }
//
//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
//            val serviceIntent = Intent(this, ScreenRecorderService::class.java).apply {
//                putExtra("resultCode", resultCode)
//                putExtra("data", data)
//            }
//            startForegroundService(serviceIntent)
//            resultCallback?.success("started")
//        } else {
//            resultCallback?.error("DENIED", "User cancelled screen recording", null)
//        }
//    }
//}

package com.example.screen_rec2  // ⚠️ Update this to match your package

//import android.app.Activity
//import android.content.Intent
//import android.media.projection.MediaProjectionManager
//import android.os.Bundle
//import io.flutter.embedding.android.FlutterActivity
//import io.flutter.embedding.engine.FlutterEngine
//import io.flutter.plugin.common.MethodChannel
//
//class MainActivity : FlutterActivity() {
//    private val CHANNEL = "screen_recorder"
//    private val REQUEST_CODE = 2024
//    private var methodResult: MethodChannel.Result? = null
//
//    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
//        super.configureFlutterEngine(flutterEngine)
//
//        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
//            if (call.method == "startRecording") {
//                val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
//                val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
//                methodResult = result
//                startActivityForResult(captureIntent, REQUEST_CODE)
//            } else {
//                result.notImplemented()
//            }
//        }
//    }
//
//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
//            val serviceIntent = Intent(this, MediaCodecScreenRecorderService::class.java).apply {
//                putExtra("resultCode", resultCode)
//                putExtra("data", data)
//            }
//            startForegroundService(serviceIntent)
//            methodResult?.success("started")
//        } else {
//            methodResult?.error("PERMISSION_DENIED", "User denied screen recording permission", null)
//        }
//    }
//}



import android.content.Intent
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val CHANNEL = "screen_recording"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler {
                call, result ->
            when (call.method) {
                "startScreenRecording" -> {
                    val intent = Intent(this, MediaCodecScreenRecorderService::class.java)
                    startForegroundService(intent)
                    result.success(null)
                }
                "stopScreenRecording" -> {
                    val intent = Intent(this, MediaCodecScreenRecorderService::class.java)
                    stopService(intent)
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
    }
}
