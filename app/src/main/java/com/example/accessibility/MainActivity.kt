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

// 1. स्मार्ट एक्शन मॉडल
data class ActionStep(val name: String, val action: () -> Boolean, val retry: Int = 3)

class UltimateAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var floatingStatus: TextView? = null
    private var currentStep = 0
    private var pIndex = 1
    private var lastTrigger = ""
    
    // एक्शन क्यू (Queue) - यहाँ स्टेप्स स्टोर होंगे
    private var actionQueue = mutableListOf<ActionStep>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        showFloating()

        // रिफ्रेश वॉचडॉग (Watchdog)
        handler.postDelayed(object : Runnable {
            override fun run() {
                val prefs = getSharedPreferences("AutoData", MODE_PRIVATE)
                val target = prefs.getString("t_time", "11:00:00")!!
                val now = currentTime()

                floatingStatus?.text = "Time: $now | Step: $currentStep"

                if (now == target && lastTrigger != now) {
                    resetAndStart()
                    lastTrigger = now
                }
                handler.postDelayed(this, 100)
            }
        }, 100)
    }

    private fun resetAndStart() {
        val root = rootInActiveWindow ?: return
        clickAction(root, "Refresh")
        clickAction(root, "Updated")
        currentStep = 0
        pIndex = 1
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // ✅ EVENT FILTER: सिर्फ ज़रूरी इवेंट्स पकड़ो
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event?.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val root = rootInActiveWindow ?: return
        if (!root.packageName.toString().contains("irctc")) return

        val prefs = getSharedPreferences("AutoData", MODE_PRIVATE)
        val delay = prefs.getString("c_delay", "200")?.toLong() ?: 200L

        // --- मास्टर लॉजिक इंजन ---
        executeMasterFlow(root, prefs, delay)
    }

    private fun executeMasterFlow(root: AccessibilityNodeInfo, prefs: SharedPreferences, delay: Long) {
        val tNum = prefs.getString("t_num", "")!!
        val cls = prefs.getString("sel_cls", "SL")!!

        when (currentStep) {
            0 -> { // ट्रेन और क्लास हंटर
                val trainNode = root.findAccessibilityNodeInfosByText(tNum).firstOrNull()
                if (trainNode != null) {
                    val card = findParentCard(trainNode)
                    if (clickAction(card ?: root, cls)) {
                        handler.postDelayed({ currentStep = 1 }, delay)
                    }
                } else if (root.isScrollable) {
                    root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                }
            }

            1 -> { // AVL और पैसेंजर डिटेल्स (Smart Wait)
                if (isAvailable(root)) {
                    handler.postDelayed({
                        if (clickAction(rootInActiveWindow ?: root, "PASSENGER DETAILS")) {
                            currentStep = 2
                        }
                    }, delay)
                }
            }

            2 -> { // पॉप-अप क्लोजर और फॉर्म फिलिंग
                clickAction(root, "OK")
                fillFormUltimate(root, prefs, delay)
            }
        }
    }

    private fun fillFormUltimate(root: AccessibilityNodeInfo, prefs: SharedPreferences, delay: Long) {
        val edits = getEdits(root)
        val total = (1..6).count { !prefs.getString("n$it", "").isNullOrEmpty() }

        if (edits.size >= 2 && edits[0].text.isNullOrEmpty()) {
            val name = prefs.getString("n$pIndex", "")!!
            val age = prefs.getString("a$pIndex", "")!!
            val gender = if (prefs.getString("g$pIndex", "M") == "F") "Female" else "Male"

            if (name.isNotEmpty()) {
                input(edits[0], name)
                input(edits[1], age)

                handler.postDelayed({
                    val r = rootInActiveWindow ?: return@postDelayed
                    // जेंडर क्लिक (Smart Parent Logic)
                    if (clickAction(r, gender)) {
                        handler.postDelayed({
                            val r2 = rootInActiveWindow ?: return@postDelayed
                            if (clickAction(r2, "Add Passenger") || clickAction(r2, "ADD PASSENGER")) {
                                pIndex++
                                // यहाँ हम स्टेप 2 पर ही रहेंगे ताकि अगला पैसेंजर भरे
                            }
                        }, delay)
                    }
                }, delay)
            }
        } else if (pIndex > total && total > 0) {
            if (clickAction(root, "REVIEW JOURNEY DETAILS")) {
                // सफल होने पर अगले दिन के लिए रेडी
                handler.postDelayed({ currentStep = 3 }, 1000)
            }
        } else {
            clickAction(root, "Add New")
            clickAction(root, "ADD NEW")
        }
    }

    // --- स्मार्ट क्लिक इंजन ---
    private fun clickAction(root: AccessibilityNodeInfo?, text: String): Boolean {
        val nodes = root?.findAccessibilityNodeInfosByText(text) ?: return false
        for (n in nodes) {
            var p: AccessibilityNodeInfo? = n
            while (p != null) {
                if (p.isClickable) {
                    p.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
                p = p.parent
            }
        }
        return false
    }

    private fun isAvailable(root: AccessibilityNodeInfo): Boolean {
        val text = root.toString() // पूरी ट्री का टेक्स्ट चेक करें
        return text.contains("AVL") || text.contains("AVAILABLE")
    }

    private fun getEdits(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()
        fun tr(n: AccessibilityNodeInfo?) {
            if (n == null) return
            if (n.className == "android.widget.EditText") list.add(n)
            for (i in 0 until n.childCount) tr(n.getChild(i))
        }
        tr(root)
        return list
    }

    private fun input(node: AccessibilityNodeInfo, text: String) {
        val b = Bundle()
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    private fun currentTime(): String {
        val c = Calendar.getInstance()
        return String.format("%02d:%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND))
    }

    private fun findParentCard(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var p: AccessibilityNodeInfo? = node
        while (p?.parent != null) {
            p = p.parent
            if (p?.findAccessibilityNodeInfosByText("Refresh")?.isNotEmpty() == true) return p
        }
        return null
    }

    private fun showFloating() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingStatus = TextView(this).apply {
            setBackgroundColor(Color.parseColor("#EE000000"))
            setTextColor(Color.WHITE)
            setPadding(25, 12, 25, 12)
            textSize = 14f
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
        try { wm.addView(floatingStatus, params) } catch (_: Exception) {}
    }

    override fun onInterrupt() {}
}

