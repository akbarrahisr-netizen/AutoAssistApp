package com.example.aare.v26

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Rect
import kotlinx.coroutines.*
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ln
import kotlin.random.Random

/* =========================================================
   🧬 GROUND SAFETY LAYER
========================================================= */

class SafetyGuard {
    private val cooldown = ConcurrentHashMap<String, Long>()
    private val WINDOW = 1500L

    fun allow(state: String, action: String): Boolean {
        val key = "$state#$action"
        val now = System.currentTimeMillis()
        val last = cooldown[key] ?: 0L
        if (now - last < WINDOW) return false
        cooldown[key] = now
        return true
    }
}

/* =========================================================
   🧠 COMPRESSED WORLD MODEL (NO GRAPH EXPLOSION)
========================================================= */

data class Edge(val action: String, val to: String, var weight: Float)

class WorldModel {

    private val memory = ConcurrentHashMap<String, MutableList<Edge>>()
    private val MAX_BUCKETS = 2000

    fun record(from: String, action: String, to: String) {
        val key = compress(from)

        if (memory.size > MAX_BUCKETS && !memory.containsKey(key)) {
            val oldest = memory.keys.firstOrNull()
            if (oldest != null) memory.remove(oldest)
        }

        val list = memory.getOrPut(key) { mutableListOf() }

        val existing = list.find { it.action == action && it.to == to }
        if (existing != null) existing.weight += 0.1f
        else list.add(Edge(action, to, 1f))
    }

    fun get(state: String): List<Edge> {
        return memory[compress(state)] ?: emptyList()
    }

    private fun compress(s: String): String = s.take(32)
}

/* =========================================================
   👁️ STABLE FINGERPRINT
========================================================= */

class StructuralFingerprint {

    fun hash(root: AccessibilityNodeInfo?): String {
        if (root == null) return "NULL"

        val sb = StringBuilder()
        walk(root, sb)

        return sha256(sb.toString())
    }

    private fun walk(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null || !node.isVisibleToUser) return

        val r = Rect()
        node.getBoundsInScreen(r)

        sb.append(node.className)
        sb.append(node.text ?: "")
        sb.append(node.isClickable)
        sb.append(r.flattenToString())

        for (i in 0 until node.childCount) {
            walk(node.getChild(i), sb)
        }
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

/* =========================================================
   🔮 ENTROPY ENGINE (REAL DYNAMIC UNCERTAINTY)
========================================================= */

class Predictor(private val world: WorldModel) {

    fun entropy(state: String): Float {
        val edges = world.get(state)
        if (edges.isEmpty()) return 0.9f

        val sum = edges.sumOf { it.weight.toDouble() }
        if (sum <= 0) return 0.9f

        var h = 0.0
        for (e in edges) {
            val p = e.weight / sum
            if (p > 0) h -= p * ln(p)
        }

        return h.toFloat().coerceIn(0f, 1f)
    }

    fun predict(state: String, action: String): String {
        return world.get(state)
            .filter { it.action == action }
            .maxByOrNull { it.weight }
            ?.to ?: "UNKNOWN"
    }
}

/* =========================================================
   🎯 ACTION SPACE
========================================================= */

class ActionScanner {

    fun scan(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()
        dfs(root, list)
        return list
    }

    private fun dfs(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>) {
        if (node == null || !node.isVisibleToUser) return

        if (node.isClickable && node.isEnabled) {
            list.add(node)
        }

        for (i in 0 until node.childCount) {
            dfs(node.getChild(i), list)
        }
    }
}

/* =========================================================
   🚀 AARE v26 CORE SERVICE (GROUND-BASED DECISION ENGINE)
========================================================= */

class AAREv26Service : AccessibilityService() {

    private val world = WorldModel()
    private val predictor = Predictor(world)
    private val fp = StructuralFingerprint()
    private val safety = SafetyGuard()
    private val scanner = ActionScanner()

    private lateinit var scope: CoroutineScope

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return

        scope.launch {

            val state = fp.hash(root)
            val candidates = scanner.scan(root)

            if (candidates.isEmpty()) return@launch

            val scored = candidates.map { node ->
                val key = node.text?.toString() ?: node.className.toString()
                val next = predictor.predict(state, key)
                val uncertainty = predictor.entropy(next)

                val exploration = Random.nextFloat() * 0.15f

                node to ((1f - uncertainty) + exploration)
            }

            val best = scored.maxByOrNull { it.second }?.first ?: return@launch

            val actionKey = best.text?.toString() ?: best.className.toString()

            if (!safety.allow(state, actionKey)) return@launch

            val success = withContext(Dispatchers.Main) {
                try {
                    best.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } catch (e: Exception) {
                    false
                }
            }

            if (success) {
                delay(650)

                val newRoot = rootInActiveWindow
                if (newRoot != null) {
                    val nextState = fp.hash(newRoot)
                    world.record(state, actionKey, nextState)
                }
            }
        }
    }

    override fun onInterrupt() {
        if (::scope.isInitialized) scope.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::scope.isInitialized) scope.cancel()
    }
}
