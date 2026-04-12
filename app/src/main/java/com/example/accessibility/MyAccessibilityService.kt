package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.*
import android.content.Context
import android.graphics.Rect
import android.os.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * ================================
 * 🧠 SIMPLE AUTONOMOUS UI ENGINE (EDUCATIONAL)
 * ================================
 * Purpose: Learn Accessibility + UI tracking safely
 */

data class SemanticTarget(
    val id: String,
    val keyword: String,
    val targetClass: String
)

/* -------------------------------
   UI STATE HASH (STABILITY CHECK)
--------------------------------*/
object UIStateHasher {

    fun hash(root: AccessibilityNodeInfo?): String {
        if (root == null) return "NULL"

        val builder = StringBuilder()
        traverse(root, builder, 0)

        return builder.toString().hashCode().toString()
    }

    private fun traverse(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (!node.isVisibleToUser || depth > 8) return

        sb.append(node.className)
        sb.append(node.text ?: "")
        sb.append(node.isClickable)

        val rect = Rect()
        node.getBoundsInScreen(rect)
        sb.append(rect.centerX()).append(rect.centerY())

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let {
                traverse(it, sb, depth + 1)
            }
        }
    }
}

/* -------------------------------
   MEMORY (LEARNING LAYER)
--------------------------------*/
class SimpleMemory {
    private val map = ConcurrentHashMap<String, Float>()

    fun get(id: String): Float = map[id] ?: 0.5f

    fun success(id: String) {
        map[id] = (get(id) + 0.05f).coerceAtMost(0.95f)
    }

    fun failure(id: String) {
        map[id] = (get(id) - 0.1f).coerceAtLeast(0.1f)
    }
}

/* -------------------------------
   NODE RESOLVER (SEMANTIC SEARCH)
--------------------------------*/
class NodeResolver {

    fun find(root: AccessibilityNodeInfo, target: SemanticTarget): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByText(target.keyword)

        val match = nodes.firstOrNull {
            it.isVisibleToUser && it.className == target.targetClass
        }

        return match
    }
}

/* -------------------------------
   FOREGROUND WATCHDOG (ANDROID SAFE)
--------------------------------*/
class Watchdog(private val service: Service) {

    fun start() {
        val channelId = "ENGINE_CHANNEL"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Engine Service",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager = service.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = Notification.Builder(service, channelId)
            .setContentTitle("Accessibility Engine Running")
            .setContentText("Monitoring UI State...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        service.startForeground(1001, notification)
    }
}

/* -------------------------------
   MAIN ACCESSIBILITY SERVICE
--------------------------------*/
class MyAccessibilityService : AccessibilityService() {

    private val memory = SimpleMemory()
    private val resolver = NodeResolver()

    private var lastHash = ""
    private var stableFrames = 0

    override fun onServiceConnected() {
        Watchdog(this).start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        val root = rootInActiveWindow ?: return

        // 1. UI STABILITY CHECK
        val currentHash = UIStateHasher.hash(root)

        if (currentHash != lastHash) {
            lastHash = currentHash
            stableFrames = 0
            return
        }

        stableFrames++

        // wait for stable UI
        if (stableFrames < 2) return

        // 2. GENERIC TARGET (FOR LEARNING ONLY)
        val target = SemanticTarget(
            id = "BTN_1",
            keyword = "Login",
            targetClass = "android.widget.Button"
        )

        // 3. FIND NODE
        val node = resolver.find(root, target)

        if (node != null && node.isClickable) {

            val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

            if (success) {
                memory.success(target.id)
            } else {
                memory.failure(target.id)
            }

            // cooldown to prevent repeated clicks
            stableFrames = -3
        }
    }

    override fun onInterrupt() {}
}
