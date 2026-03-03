package com.openclaw.mobile.a11y

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

object NodeInfoConverter {

    fun toJson(node: AccessibilityNodeInfo): String {
        val json = nodeToJson(node)
        return json.toString()
    }

    private fun nodeToJson(node: AccessibilityNodeInfo): JSONObject {
        return JSONObject().apply {
            put("className", node.className?.toString() ?: "")
            put("packageName", node.packageName?.toString() ?: "")
            put("text", node.text?.toString() ?: "")
            put("contentDescription", node.contentDescription?.toString() ?: "")
            put("viewIdResourceName", node.viewIdResourceName ?: "")
            
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            put("bounds", JSONObject().apply {
                put("left", bounds.left)
                put("top", bounds.top)
                put("right", bounds.right)
                put("bottom", bounds.bottom)
            })
            
            put("clickable", node.isClickable)
            put("longClickable", node.isLongClickable)
            put("focusable", node.isFocusable)
            put("focused", node.isFocused)
            put("enabled", node.isEnabled)
            put("scrollable", node.isScrollable)
            put("checkable", node.isCheckable)
            put("checked", node.isChecked)
            put("editable", node.isEditable)
            put("selected", node.isSelected)
            put("visible", node.isVisibleToUser)
            put("password", node.isPassword)
            
            // 子节点
            val children = JSONArray()
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    children.put(nodeToJson(child))
                }
            }
            put("children", children)
        }
    }

    fun toSimplifiedJson(node: AccessibilityNodeInfo): String {
        val json = simplifiedNodeToJson(node)
        return json.toString()
    }

    private fun simplifiedNodeToJson(node: AccessibilityNodeInfo): JSONObject {
        return JSONObject().apply {
            // 只包含有用的信息
            put("text", node.text?.toString() ?: "")
            put("desc", node.contentDescription?.toString() ?: "")
            put("id", node.viewIdResourceName ?: "")
            
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            put("rect", JSONArray().apply {
                put(bounds.left)
                put(bounds.top)
                put(bounds.right)
                put(bounds.bottom)
            })
            
            put("clickable", node.isClickable)
            put("editable", node.isEditable)
            
            // 只包含可见且有信息的子节点
            val children = JSONArray()
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    if (child.isVisibleToUser && hasMeaningfulInfo(child)) {
                        children.put(simplifiedNodeToJson(child))
                    }
                }
            }
            if (children.length() > 0) {
                put("children", children)
            }
        }
    }

    private fun hasMeaningfulInfo(node: AccessibilityNodeInfo): Boolean {
        return node.text?.isNotBlank() == true ||
                node.contentDescription?.isNotBlank() == true ||
                node.isClickable ||
                node.isEditable ||
                node.childCount > 0
    }
}
