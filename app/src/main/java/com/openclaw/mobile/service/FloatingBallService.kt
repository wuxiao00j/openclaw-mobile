package com.openclaw.mobile.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.openclaw.mobile.databinding.ViewFloatingBallBinding
import timber.log.Timber

class FloatingBallService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var _binding: ViewFloatingBallBinding? = null
    private val binding get() = _binding!!

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupFloatingView()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setupFloatingView() {
        _binding = ViewFloatingBallBinding.inflate(LayoutInflater.from(this))
        floatingView = binding.root

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }

        floatingView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }

        binding.fabMain.setOnClickListener {
            toggleExpandedMenu()
        }

        windowManager.addView(floatingView, params)
    }

    private fun toggleExpandedMenu() {
        // TODO: 展开菜单显示更多选项
        Timber.d("Floating ball clicked")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingView != null) {
            windowManager.removeView(floatingView)
            floatingView = null
        }
        _binding = null
    }
}
