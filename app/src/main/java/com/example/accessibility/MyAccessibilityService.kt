package com.example.aare.production.safecore

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap
import java.util.ArrayDeque

/* =========================================================
   🧠 STATE MACHINE CORE
========================================================= */

sealed class ServiceState {
    object Idle : ServiceState()
    object Observing : ServiceState()
    object Processing : ServiceState()
    object Executing : ServiceState()
    data class Error(val message: String) : ServiceState()
}

/* =========================================================
   🧩 SAFE NODE CACHE (ANTI RE-SCAN OPTIMIZATION)
========================================================= */

class NodeCache {
    private val cache = ConcurrentHashMap<String, AccessibilityNodeInfo>()

    fun put(key: String, node: AccessibilityNodeInfo) {
        cache[key] = node
    }

    fun get(key: String): AccessibilityNodeInfo? {
        return cache[key]
    }

    fun clear() {
        cache.clear()
    }
}

/* =========================================================
   🧭 ITERATIVE TREE TRAVERSAL ENGINE
========================================================= */

class TraversalEngine {

    fun findNodeByTextIterative(
        root: AccessibilityNodeInfo?,
        targetText: String
    ): AccessibilityNodeInfo? {

        if (root == null) return null

        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)

        while (stack.isNotEmpty()) {

            val node = stack.removeLast() ?: continue

            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""

            if (text.contains(targetText, true) ||
                desc.contains(targetText, true)
            ) {
                return node
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) stack.add(child)
            }
        }

        return null
    }

    fun collectClickables(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()

        val result = mutableListOf<AccessibilityNodeInfo>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)

        while (stack.isNotEmpty()) {
            val node = stack.removeLast()

            if (node.isClickable && node.isVisibleToUser) {
                result.add(node)
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.add(it) }
            }
        }

        return result
    }
}

/* =========================================================
   ⚖️ DECISION ENGINE (DETERMINISTIC POLICY)
========================================================= */

class DecisionEngine {

    fun pickBest(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        return nodes.firstOrNull { it.isEnabled && it.isClickable }
    }
}

/* =========================================================
   🛡️ SAFETY LAYER (ANTI CRASH + ANTI LOOP)
========================================================= */

class SafetyLayer {
    private val lastActionMap = ConcurrentHashMap<String, Long>()
    private val cooldown = 1200L

    fun allow(key: String): Boolean {
        val now = System.currentTimeMillis()
        val last = lastActionMap[key] ?: 0L

        if (now - last < cooldown) return false

        lastActionMap[key] = now
        return true
    }
}

/* =========================================================
   🚀 ORCHESTRATOR SERVICE (PRODUCTION SAFE SKELETON)
========================================================= */

class AARESafeService : AccessibilityService() {

    private var state: ServiceState = ServiceState.Idle

    private val handler = Handler(Looper.getMainLooper())

    private val traversalEngine = TraversalEngine()
    private val decisionEngine = DecisionEngine()
    private val safetyLayer = SafetyLayer()
    private val cache = NodeCache()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        val root = rootInActiveWindow ?: return

        state = ServiceState.Observing

        try {

            state = ServiceState.Processing

            val cachedKey = root.hashCode().toString()

            val cached = cache.get(cachedKey)
            val clickables = if (cached != null) {
                listOf(cached)
            } else {
                val nodes = traversalEngine.collectClickables(root)
                nodes.forEach { cache.put(it.hashCode().toString(), it) }
                nodes
            }

            val target = decisionEngine.pickBest(clickables) ?: return

            val actionKey = target.text?.toString() ?: target.className?.toString() ?: "unknown"

            if (!safetyLayer.allow(actionKey)) return

            state = ServiceState.Executing

            handler.post {
                try {
                    target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    state = ServiceState.Idle
                } catch (e: Exception) {
                    state = ServiceState.Error(e.message ?: "unknown")
                }
            }

        } catch (e: Exception) {
            state = ServiceState.Error(e.message ?: "fatal")
        }
    }

    override fun onInterrupt() {
        state = ServiceState.Idle
        cache.clear()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        cache.clear()
        super.onDestroy()
    }
}
