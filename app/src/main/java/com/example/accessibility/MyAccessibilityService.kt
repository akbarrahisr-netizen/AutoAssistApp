package com.example.aare.v24

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ln

/* =========================================================
   🛡️ SAFETY GUARD
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
   🧬 WORLD MODEL
========================================================= */

class WorldModel {
    private val graph = ConcurrentHashMap<String, MutableList<Triple<String, String, Float>>>()

    fun record(from: String, action: String, to: String) {
        val list = graph.getOrPut(from) { mutableListOf() }
        val existing = list.find { it.first == action && it.second == to }

        if (existing != null) {
            list.remove(existing)
            list.add(Triple(action, to, existing.third + 0.1f))
        } else {
            list.add(Triple(action, to, 1f))
        }
    }

    fun get(from: String): List<Triple<String, String, Float>> {
        return graph[from] ?: emptyList()
    }
}

/* =========================================================
   👁️ FINGERPRINT
========================================================= */

class StructuralFingerprint {

    fun hash(root: AccessibilityNodeInfo?): String {
        if (root == null) return "NULL"

        val sb = StringBuilder()
        walk(root, sb)
        return sb.toString().hashCode().toString()
    }

    private fun walk(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null || !node.isVisibleToUser) return

        sb.append(node.className)
        sb.append(node.text ?: "")
        sb.append(node.isClickable)

        for (i in 0 until node.childCount) {
            walk(node.getChild(i), sb)
        }
    }
}

/* =========================================================
   🔮 PREDICTOR (SAFE ENTROPY)
========================================================= */

class Predictor(private val world: WorldModel) {

    fun entropy(state: String): Float {
        val transitions = world.get(state)
        if (transitions.isEmpty()) return 1f   // ✅ FIX: no crash

        val total = transitions.sumOf { it.third.toDouble() }
        if (total <= 0.0) return 1f            // ✅ FIX: zero safety

        var h = 0.0

        for (t in transitions) {
            val p = t.third / total
            if (p > 0) h -= p * ln(p)
        }

        return h.toFloat()
    }

    fun predict(state: String, action: String): String {
        return world.get(state)
            .filter { it.first == action }
            .maxByOrNull { it.third }
            ?.second ?: "UNKNOWN"
    }
}

/* =========================================================
   🎯 ACTION SCANNER
========================================================= */

class ActionScanner {

    fun scan(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()
        dfs(root, list)
        return list
    }

    private fun dfs(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>) {
        if (node == null || !node.isVisibleToUser) return

        if (node.isClickable) list.add(node)

        for (i in 0 until node.childCount) {
            dfs(node.getChild(i), list)
        }
    }
}

/* =========================================================
   🚀 SERVICE (FIXED TIMING + CORRECT MEMORY WRITE)
========================================================= */

class AAREv24Service : AccessibilityService() {

    private val world = WorldModel()
    private val predictor = Predictor(world)
    private val scanner = ActionScanner()
    private val safety = SafetyGuard()
    private val fp = StructuralFingerprint()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return

        scope.launch {

            val state = fp.hash(root)
            val candidates = scanner.scan(root)

            val target = candidates.firstOrNull() ?: return@launch
            val action = "CLICK"

            if (!safety.allow(state, action)) return@launch

            val success = withContext(Dispatchers.Main) {
                try {
                    target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } catch (e: Exception) {
                    false
                }
            }

            // ✅ FIX 1: WAIT FOR UI SETTLE (critical)
            delay(650)

            // ✅ FIX 2: CAPTURE NEW STATE AFTER TRANSITION
            val newRoot = rootInActiveWindow
            val newState = fp.hash(newRoot)

            // ✅ SAFE MEMORY UPDATE (correct timing)
            world.record(state, action, newState)
        }
    }

    override fun onInterrupt() {
        scope.cancel()
    }
}
