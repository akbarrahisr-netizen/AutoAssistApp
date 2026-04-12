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

    private var step = 0
    private var pIdx = 1
    private var avlFound = false
    private var sniperActive = false

    private var lastActionTime = System.currentTimeMillis()
    private var lastTriggerMinute = ""
    private var lastClickTime = 0L
    private var scrollCount = 0

    // 🔥 SMART DELAY
    private fun getDelay(base: Long) = base + (40..120).random()

    // 🔥 WATCHDOG (Freeze Recovery)
    private val watchdog = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (sniperActive && (now - lastActionTime > 4000)) {
                resetAll("Auto Reset 🛡️")
            }
            handler.postDelayed(this, 2000)
        }
    }

    // 🔥 SNIPER LOOP
    private val sniperTask = object : Runnable {
        override fun run() {
            val root = rootInActiveWindow
            if (root != null && root.packageName.toString().contains("irctc")) {
                if (!avlFound && step <= 1) {
                    smartClick(root, "Refresh")
                    smartClick(root, "Updated")
                    lastActionTime = System.currentTimeMillis()
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

                val currentMinute = now.substring(0,5)

                if (currentMinute == target.substring(0,5) && lastTriggerMinute != currentMinute) {
                    lastTriggerMinute = currentMinute
                    sniperActive = true
                    avlFound = false
                    step = 0
                    scrollCount = 0

                    handler.post(sniperTask)
                    showFloatingStatus("🔥 SNIPER ACTIVE")
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
        if (!root.packageName.toString().contains("irctc")) return

        val prefs = getSharedPreferences("AutoData", MODE_PRIVATE)
        val delay = getDelay((prefs.getString("c_delay", "250") ?: "250").toLong())
        val trainNum = prefs.getString("t_num", "") ?: ""

        when (step) {
            0 -> findTrain(root, trainNum, prefs, delay)
            1 -> handleAVL(root, delay)
            2 -> fillForm(root, prefs, delay)
        }
    }

    private fun findTrain(root: AccessibilityNodeInfo, trainNum: String, prefs: SharedPreferences, delay: Long) {
        if (!isTrainPresent(root, trainNum)) {
            if (scrollCount < 8) {
                smartScroll(root)
                scrollCount++
            }
            return
        }

        val cls = prefs.getString("sel_cls", "SL") ?: "SL"
        if (smartClick(root, cls)) {
            step = 1
            scrollCount = 0
            lastActionTime = System.currentTimeMillis()
        }
    }

    private fun handleAVL(root: AccessibilityNodeInfo, delay: Long) {
        if (!avlFound && isAvailable(root)) {
            avlFound = true
            handler.removeCallbacks(sniperTask)

            retryInfinite {
                val r = rootInActiveWindow ?: return@retryInfinite false
                if (smartClick(r, "PASSENGER DETAILS")) {
                    step = 2
                    showFloatingStatus("🎯 SEAT LOCKED")
                    true
                } else false
            }
        }
    }

    private fun fillForm(root: AccessibilityNodeInfo, prefs: SharedPreferences, delay: Long) {
        smartClick(root, "OK")

        val edits = getEdits(root)
        if (edits.size < 2) return

        val total = (1..6).count { !(prefs.getString("n$it", "") ?: "").isEmpty() }

        if (edits[0].text.isNullOrEmpty()) {
            val name = prefs.getString("n$pIdx", "") ?: ""
            val age = prefs.getString("a$pIdx", "") ?: ""
            val gender = if (prefs.getString("g$pIdx", "M") == "F") "Female" else "Male"

            input(edits[0], name)
            input(edits[1], age)

            handler.postDelayed({
                val r = rootInActiveWindow ?: return@postDelayed
                if (smartClick(r, gender)) {
                    handler.postDelayed({
                        val r2 = rootInActiveWindow ?: return@postDelayed
                        if (System.currentTimeMillis() - lastClickTime > 500) {
                            if (smartClick(r2, "Add Passenger")) {
                                pIdx++
                                lastClickTime = System.currentTimeMillis()
                            }
                        }
                    }, delay)
                }
            }, delay)

        } else if (pIdx > total) {
            if (smartClick(root, "REVIEW JOURNEY DETAILS")) {
                resetAll("Done ✅")
            }
        }
    }

    // 🔧 TOOLS

    private fun retryInfinite(action: () -> Boolean) {
        if (!action()) {
            handler.postDelayed({ retryInfinite(action) }, 180)
        }
    }

    private fun isTrainPresent(root: AccessibilityNodeInfo, trainNum: String): Boolean {
        return root.findAccessibilityNodeInfosByText(trainNum).isNotEmpty()
    }

    private fun isAvailable(root: AccessibilityNodeInfo): Boolean {
        return root.findAccessibilityNodeInfosByText("AVL").isNotEmpty() ||
               root.findAccessibilityNodeInfosByText("AVAILABLE").isNotEmpty()
    }

    private fun smartClick(root: AccessibilityNodeInfo?, text: String): Boolean {
        val nodes = root?.findAccessibilityNodeInfosByText(text) ?: return false
        for (n in nodes) {
            var p: AccessibilityNodeInfo? = n
            repeat(5) {
                if (p?.isClickable == true) {
                    p.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
                p = p?.parent
            }
        }
        return false
    }

    private fun input(node: AccessibilityNodeInfo, text: String) {
        val b = Bundle()
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    private fun smartScroll(root: AccessibilityNodeInfo) {
        if (root.isScrollable) {
            root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }
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

    private fun resetAll(msg: String) {
        handler.removeCallbacks(sniperTask)
        step = 0
        pIdx = 1
        avlFound = false
        sniperActive = false
        scrollCount = 0
        showFloatingStatus(msg)
    }

    private fun currentTime(): String {
        val c = Calendar.getInstance()
        return String.format("%02d:%02d:%02d",
            c.get(Calendar.HOUR_OF_DAY),
            c.get(Calendar.MINUTE),
            c.get(Calendar.SECOND))
    }

    private fun showFloating() {
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            floatingStatus = TextView(this).apply {
                setBackgroundColor(Color.BLACK)
                setTextColor(Color.WHITE)
                setPadding(20,10,20,10)
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            wm.addView(floatingStatus, params)
        } catch (e: Exception) {}
    }

    private fun showFloatingStatus(text: String) {
        floatingStatus?.text = text
    }

    override fun onInterrupt() {}
}
