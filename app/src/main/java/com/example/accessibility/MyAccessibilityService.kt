package com.example.accessibility.safe

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.*
import android.view.*
import android.graphics.*
import android.widget.TextView
import java.util.*

class SafeAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    // ---------------- STATE ----------------
    private var step = 0
    private var running = false
    private var lastActionTs = 0L

    // ---------------- WATCHDOG ----------------
    private val watchdog = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()

            if (running && now - lastActionTs > 8000) {
                reset("Auto Recovery Triggered")
            }

            mainHandler.postDelayed(this, 2000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
        mainHandler.post(watchdog)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return

        if (!isValidEvent(event)) return

        when (step) {
            0 -> observe(root)
            1 -> process(root)
        }
    }

    // ---------------- SAFE OBSERVE ----------------
    private fun observe(root: AccessibilityNodeInfo) {
        val node = findNode(root, "Refresh") ?: return
        safeClick(node)
        step = 1
    }

    // ---------------- SAFE PROCESS ----------------
    private fun process(root: AccessibilityNodeInfo) {
        val node = findNode(root, "OK") ?: return
        safeClick(node)
    }

    // ---------------- SAFE NODE SEARCH (ITERATIVE) ----------------
    private fun findNode(root: AccessibilityNodeInfo, keyword: String): AccessibilityNodeInfo? {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)

        while (stack.isNotEmpty()) {
            val n = stack.removeLast()

            val text = n.text?.toString() ?: ""
            val desc = n.contentDescription?.toString() ?: ""

            if (text.contains(keyword, true) || desc.contains(keyword, true)) {
                return n
            }

            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { stack.add(it) }
            }
        }
        return null
    }

    // ---------------- SAFE CLICK ----------------
    private fun safeClick(node: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        if (now - lastActionTs < 500) return

        try {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            lastActionTs = now
        } catch (_: Exception) {}
    }

    // ---------------- EVENT FILTER ----------------
    private fun isValidEvent(event: AccessibilityEvent?): Boolean {
        return event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
    }

    // ---------------- RESET ----------------
    private fun reset(msg: String) {
        step = 0
        running = false
        lastActionTs = 0L
    }

    override fun onInterrupt() {
        reset("Interrupted")
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
