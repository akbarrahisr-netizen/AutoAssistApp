package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.*
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.view.accessibility.*
import android.widget.TextView
import android.graphics.Color
import java.util.*

class MyAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var floatingStatus: TextView? = null

    // --- STATE CONTROL ---
    private var step = 0
    private var pIdx = 1
    private var avlFound = false
    private var sniperActive = false

    private var lastActionTime = System.currentTimeMillis()
    private var lastClickTime = 0L
    private var retryCount = 0
    private var lastTriggerTime = ""
    private var sniperStartTime = 0L

    // 🛡️ WATCHDOG (AUTO RECOVERY)
    private val watchdog = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (sniperActive && (now - lastActionTime > 6000)) {
                resetAll("Auto Recover 🔄")
            }
            handler.postDelayed(this, 2000)
        }
    }

    // 🔥 SNIPER LOOP
    private val sniperTask = object : Runnable {
        override fun run() {
            if (!sniperActive) return

            // ⛔ Safety Timeout (40 sec)
            if (System.currentTimeMillis() - sniperStartTime > 40000) {
                resetAll("Timeout Stop ⛔")
                return
            }

            val root = rootInActiveWindow
            if (root != null && root.packageName == "cris.org.in.prs.ima") {
                if (!avlFound && step <= 1) {
                    smartClick(root, "Refresh")
                    smartClick(root, "Updated")
                    updateAction()
                }
            }
            handler.postDelayed(this, 350)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        showFloating()
        handler.post(watchdog)

        handler.postDelayed(object : Runnable {
            override fun run() {
                val prefs = getSharedPreferences("AutoData", MODE_PRIVATE)
                val target = prefs.getString("t_time", "11:00:00") ?: "11:00:00"
                val now = currentTime()

                if (now.startsWith(target.substring(0,5)) && lastTriggerTime != target) {
                    lastTriggerTime = target
                    sniperActive = true
                    avlFound = false
                    step = 0
                    sniperStartTime = System.currentTimeMillis()
                    handler.post(sniperTask)
                    showStatus("🔥 SNIPER ACTIVE")
                }
                handler.postDelayed(this, 200)
            }
        }, 200)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val root = rootInActiveWindow ?: return

        // 🔴 PACKAGE CHECK
        if (root.packageName != "cris.org.in.prs.ima") return

        // 🔴 LOADING GUARD
        if (isScreenLoading(root)) return

        val prefs = getSharedPreferences("AutoData", MODE_PRIVATE)
        val delay = (prefs.getString("c_delay", "300")?.toLong() ?: 300L) + (40..100).random()

        when (step) {
            0 -> selectTrain(root, prefs, delay)
            1 -> handleAvailability(root)
            2 -> fillForm(root, prefs, delay)
        }
    }

    private fun selectTrain(root: AccessibilityNodeInfo, prefs: SharedPreferences, delay: Long) {
        val train = prefs.getString("t_num", "") ?: ""
        val cls = prefs.getString("sel_cls", "SL") ?: "SL"

        if (root.findAccessibilityNodeInfosByText(train).isNotEmpty()) {
            if (smartClick(root, cls)) {
                updateAction()
                handler.postDelayed({ step = 1 }, delay)
            }
        } else {
            smartScroll(root)
        }
    }

    private fun handleAvailability(root: AccessibilityNodeInfo) {
        if (!avlFound && isAvailableSmart(root)) {
            avlFound = true
            handler.removeCallbacks(sniperTask)

            retryAction {
                val r = rootInActiveWindow ?: return@retryAction false
                if (smartClick(r, "PASSENGER DETAILS")) {
                    step = 2
                    showStatus("🎯 TARGET LOCKED")
                    true
                } else false
            }
        }
    }

    private fun fillForm(root: AccessibilityNodeInfo, prefs: SharedPreferences, delay: Long) {

        smartClick(root, "OK")

        val edits = getEdits(root)

        if (edits.size < 2) {
            handler.postDelayed({
                fillForm(rootInActiveWindow ?: root, prefs, delay)
            }, 250)
            return
        }

        val total = (1..6).count {
            !(prefs.getString("n$it", "") ?: "").isEmpty()
        }

        if (edits[0].text.isNullOrEmpty()) {

            val name = prefs.getString("n$pIdx", "") ?: ""
            val age = prefs.getString("a$pIdx", "") ?: ""
            val gender = if (prefs.getString("g$pIdx", "M") == "F") "Female" else "Male"

            if (name.isEmpty()) return

            inputSafe(edits[0], name)
            inputSafe(edits[1], age)

            handler.postDelayed({
                val r = rootInActiveWindow ?: return@postDelayed

                if (smartClick(r, gender)) {
                    handler.postDelayed({
                        val now = System.currentTimeMillis()

                        if (now - lastClickTime > 800) {
                            if (smartClick(rootInActiveWindow, "Add Passenger")) {
                                lastClickTime = now
                                pIdx++
                                updateAction()
                            }
                        }
                    }, delay)
                }
            }, delay)

        } else if (pIdx > total && total > 0) {

            if (smartClick(root, "REVIEW JOURNEY DETAILS")) {

                // 🎯 CAPTCHA AUTO FOCUS
                val captchaNodes = root.findAccessibilityNodeInfosByText("Captcha")
                if (captchaNodes.isNotEmpty()) {
                    val edit = getEdits(root).lastOrNull()
                    edit?.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                }

                handler.postDelayed({
                    resetAll("Ready for Payment ✅")
                }, 2000)
            }

        } else {
            if (edits.size < 3) {
                smartClick(root, "Add New")
                updateAction()
            }
        }
    }

    // --- TOOLS ---

    private fun isScreenLoading(root: AccessibilityNodeInfo): Boolean {
        val list = listOf("Loading", "Please wait")
        for (t in list) {
            if (root.findAccessibilityNodeInfosByText(t).isNotEmpty()) return true
        }
        return false
    }

    private fun isAvailableSmart(root: AccessibilityNodeInfo): Boolean {
        val good = listOf("AVL", "AVAILABLE")
        val bad = listOf("WL", "RAC", "NOT AVAILABLE")

        for (b in bad) {
            if (root.findAccessibilityNodeInfosByText(b).isNotEmpty()) return false
        }
        for (g in good) {
            if (root.findAccessibilityNodeInfosByText(g).isNotEmpty()) return true
        }
        return false
    }

    private fun smartScroll(root: AccessibilityNodeInfo) {
        if (root.isScrollable) {
            root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }
    }

    private fun retryAction(action: () -> Boolean) {
        if (action()) {
            retryCount = 0
            updateAction()
        } else if (retryCount < 5) {
            retryCount++
            handler.postDelayed({ retryAction(action) }, 250)
        }
    }

    private fun smartClick(root: AccessibilityNodeInfo?, text: String): Boolean {
        val nodes = root?.findAccessibilityNodeInfosByText(text) ?: return false
        for (n in nodes) {
            var p: AccessibilityNodeInfo? = n
            repeat(6) {
                if (p?.isClickable == true && p.isEnabled) {
                    p.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    updateAction()
                    return true
                }
                p = p?.parent
            }
        }
        return false
    }

    private fun inputSafe(node: AccessibilityNodeInfo, text: String) {
        val b = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }

        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)

        handler.postDelayed({
            if (node.text?.toString() != text) {
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
            }
        }, 120)
    }

    private fun getEdits(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()
        fun scan(n: AccessibilityNodeInfo?) {
            if (n == null) return
            if (n.className == "android.widget.EditText") list.add(n)
            for (i in 0 until n.childCount) scan(n.getChild(i))
        }
        scan(root)
        return list
    }

    private fun updateAction() {
        lastActionTime = System.currentTimeMillis()
    }

    private fun resetAll(msg: String) {
        handler.removeCallbacks(sniperTask)
        step = 0
        pIdx = 1
        avlFound = false
        sniperActive = false
        retryCount = 0
        showStatus(msg)
    }

    private fun currentTime(): String {
        val c = Calendar.getInstance()
        return String.format("%02d:%02d:%02d",
            c.get(Calendar.HOUR_OF_DAY),
            c.get(Calendar.MINUTE),
            c.get(Calendar.SECOND)
        )
    }

    private fun showFloating() {
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            floatingStatus = TextView(this).apply {
                setBackgroundColor(Color.parseColor("#CC000000"))
                setTextColor(Color.WHITE)
                textSize = 14f
                setPadding(20, 10, 20, 10)
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.y = 80
            wm.addView(floatingStatus, params)
        } catch (e: Exception) {}
    }

    private fun showStatus(msg: String) {
        floatingStatus?.text = msg
    }

    override fun onInterrupt() {}
}
