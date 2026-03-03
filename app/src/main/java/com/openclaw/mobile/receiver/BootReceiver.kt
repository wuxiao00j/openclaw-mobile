package com.openclaw.mobile.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.openclaw.mobile.config.SecureStorage
import com.openclaw.mobile.service.OpenClawService
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var secureStorage: SecureStorage

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.i("Boot completed, checking if should start OpenClaw")
            
            // TODO: 检查用户是否设置了开机自启动
            // if (secureStorage.isAutoStartEnabled()) {
            //     val serviceIntent = Intent(context, OpenClawService::class.java).apply {
            //         action = OpenClawService.ACTION_START
            //     }
            //     context.startForegroundService(serviceIntent)
            // }
        }
    }
}
