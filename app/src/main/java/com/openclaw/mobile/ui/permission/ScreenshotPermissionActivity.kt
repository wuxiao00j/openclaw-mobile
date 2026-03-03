package com.openclaw.mobile.ui.permission

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.openclaw.mobile.a11y.A11yBridgeService
import timber.log.Timber

/**
 * 用于获取截图权限（MediaProjection）的透明 Activity
 */
class ScreenshotPermissionActivity : Activity() {

    companion object {
        const val REQUEST_MEDIA_PROJECTION = 1001
        const val ACTION_MEDIA_PROJECTION_RESULT = "action_media_projection_result"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA = "extra_data"

        fun start(context: Context) {
            val intent = Intent(context, ScreenshotPermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(
            projectionManager.createScreenCaptureIntent(),
            REQUEST_MEDIA_PROJECTION
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                Timber.i("MediaProjection permission granted")
                
                // 创建 MediaProjection 并设置到 A11yBridgeService
                val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val mediaProjection = projectionManager.getMediaProjection(resultCode, data)
                
                // 设置到 A11yBridgeService
                A11yBridgeService.instance?.setMediaProjection(mediaProjection)
                
                // 发送广播通知权限已获取
                val broadcastIntent = Intent(ACTION_MEDIA_PROJECTION_RESULT).apply {
                    putExtra(EXTRA_RESULT_CODE, resultCode)
                    putExtra(EXTRA_DATA, data)
                }
                LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)
            } else {
                Timber.w("MediaProjection permission denied")
            }
            finish()
        }
    }
}
