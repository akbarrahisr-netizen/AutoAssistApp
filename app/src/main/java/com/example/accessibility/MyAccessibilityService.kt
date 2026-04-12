package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/* =======================================================
   1. DATA MODEL (WHAT TO FIND ON SCREEN)
======================================================= */

data class SemanticAnchor(
    val id: String,
    val label: String
)

/* =======================================================
   2. UI STATE CHECKER (SCREEN CHANGE DETECTOR)
======================================================= */

object UIHasher {

    fun hash(root: AccessibilityNodeInfo?): String {
        if (root == null) return "NULL"

        val sb = StringBuilder()
        walk(root, sb, 0)
        return sb.toString().hashCode().toString()
    }

    private fun walk(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (!node.isVisibleToUser || depth > 10) return

        sb.append(node.className)
        sb.append(node.text ?: "")

        val r = Rect()
        node.getBoundsInScreen(r)
        sb.append(r.centerX()).append(r.centerY())

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { walk(it, sb, depth + 1) }
        }
    }
}

/* =======================================================
   3. MEMORY (LEARNING SYSTEM)
======================================================= */

class Memory {
    private val map = ConcurrentHashMap<String, Float>()

    fun get(id: String) = map[id] ?: 0.5f

    fun success(id: String) {
        map[id] = (get(id) + 0.05f).coerceAtMost(1f)
    }

    fun fail(id: String) {
        map[id] = (get(id) - 0.1f).coerceAtLeast(0f)
    }
}

/* =======================================================
   4. FIND BUTTON BY TEXT (SEMANTIC SEARCH)
======================================================= */

class Resolver {

    fun find(root: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        val list = root.findAccessibilityNodeInfosByText(label)
        return list.firstOrNull { it.isVisibleToUser }
    }
}

/* =======================================================
   5. MAIN ACCESSIBILITY SERVICE
======================================================= */

class MyAccessibilityService : AccessibilityService() {

    private val memory = Memory()
    private val resolver = Resolver()

    private var lastHash = ""
    private var stableFrames = 0

    override fun onServiceConnected() {
        startForegroundService()
    }

    /* ---------------------------------------------------
       FOREGROUND SERVICE (OPPO SAFE MODE)
    --------------------------------------------------- */

    private fun startForegroundService() {

        val channelId = "dcds_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "DCDS Engine",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Engine Running")
            .setContentText("Accessibility Active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(1001, notification)
    }

    /* ---------------------------------------------------
       MAIN LOGIC LOOP
    --------------------------------------------------- */

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        val root = rootInActiveWindow ?: return

        val currentHash = UIHasher.hash(root)

        /* ===================================================
           ENGINEERING TRUTH:
           UI change = instability
        =================================================== */

        if (currentHash != lastHash) {
            lastHash = currentHash
            stableFrames = 0
            return
        }

        stableFrames++

        // UI must be stable for 2 frames
        if (stableFrames < 2) return

        /* ===================================================
           TARGET: EXAMPLE (Login Button)
        =================================================== */

        val anchor = SemanticAnchor(
            id = "LOGIN",
            label = "Login"
        )

        val node = resolver.find(root, anchor.label)

        if (node != null && node.isClickable) {

            val clicked = node.performAction(
                AccessibilityNodeInfo.ACTION_CLICK
            )

            // =================================================
            // VERIFICATION (IMPORTANT PART)
            // =================================================

            val newRoot = rootInActiveWindow
            val newHash = UIHasher.hash(newRoot)

            val uiChanged = newHash != currentHash

            if (clicked && uiChanged) {
                memory.success(anchor.id)
            } else {
                memory.fail(anchor.id)
            }

            // cooldown to avoid double click
            stableFrames = -2
        }
    }

    override fun onInterrupt() {
        // required override
    }
}
