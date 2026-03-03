package com.openclaw.mobile.ui.permission

import android.app.Activity
import android.os.Bundle

/**
 * 权限申请辅助 Activity（透明）
 */
class PermissionHelperActivity : Activity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 透明 Activity，用于处理一些需要 Activity 上下文的权限申请
        finish()
    }
}
