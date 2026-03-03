package com.openclaw.mobile.service

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.openclaw.mobile.a11y.A11yBridgeService
import timber.log.Timber

class KeepAliveJobService : JobService() {

    private var openClawService: OpenClawService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as OpenClawService.LocalBinder
            openClawService = binder.getService()
            serviceBound = true
            checkAndRestartServices()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            openClawService = null
            serviceBound = false
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        Timber.d("KeepAliveJobService started")
        
        // 绑定到 OpenClawService 检查状态
        val intent = Intent(this, OpenClawService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        
        return true // 异步执行
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Timber.d("KeepAliveJobService stopped")
        if (serviceBound) {
            unbindService(serviceConnection)
        }
        return false
    }

    private fun checkAndRestartServices() {
        // 检查 A11y Bridge 是否运行
        if (!A11yBridgeService.isRunning) {
            Timber.w("A11yBridgeService is not running, user needs to enable it")
        }

        // 检查 OpenClaw 是否运行
        openClawService?.let { service ->
            when (service.getState()) {
                OpenClawService.ServiceState.STOPPED -> {
                    Timber.w("OpenClawService is stopped, restarting...")
                    startService(Intent(this, OpenClawService::class.java).apply {
                        action = OpenClawService.ACTION_START
                    })
                }
                OpenClawService.ServiceState.ERROR -> {
                    Timber.w("OpenClawService is in error state, restarting...")
                    startService(Intent(this, OpenClawService::class.java).apply {
                        action = OpenClawService.ACTION_RESTART
                    })
                }
                else -> {
                    Timber.d("OpenClawService is healthy")
                }
            }
        }

        // 解绑
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        
        jobFinished(null, false)
    }
}
