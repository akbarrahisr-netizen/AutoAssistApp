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
    private var currentStep = 0
    private var pIndex = 1
    private var lastTrigger = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        showFloating()

        handler.postDelayed(object : Runnable {
            override fun run() {
                val prefs = getSharedPreferences("AutoData", MODE_PRIVATE)
                // !! की जगह ?: (Default value) का इस्तेमाल किया है ताकि क्रैश न हो
                val target = prefs.getString("t_time", "11:00:00") ?: "11:00:00"
                val now = currentTime()

                floatingStatus?.text = "Time: $now | Step: $currentStep"

                if (now == target && lastTrigger != now) {
                    rootInActiveWindow?.let {
                        clickAction(it, "Refresh")
                        clickAction(it, "Updated")
                        currentStep = 0
                        pIndex = 1
                        lastTrigger = now
                    }
                }
                handler.postDelayed(this, 200) // रफ़्तार थोड़ी स्थिर रखी है
            }
        }, 200)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val root = rootInActiveWindow ?: return
        if (!root.packageName.toString().contains("irctc")) return

        val prefs = getSharedPreferences("AutoData", MODE_PRIVATE)
        
        // खाली बॉक्स होने पर भी ऐप क्रैश नहीं होगा (Safe conversion)
        val delayStr = prefs.getString("c_delay", "200") ?: "200"
        val userDelay = try { delayStr.toLong() } catch (e: Exception) { 200L }

        when (currentStep) {
            0 -> findTrainStep(root, prefs, userDelay)
            1 -> checkAvlStep(root, userDelay)
            2 -> fillFormStep(root, prefs, userDelay)
        }
    }

    private fun findTrainStep(root: AccessibilityNodeInfo, prefs: SharedPreferences, delay: Long) {
        val tNum = prefs.getString("t_num", "") ?: ""
        val cls = prefs.getString("sel_cls", "SL") ?: "SL"
        if (tNum.isEmpty()) return

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

    private fun checkAvlStep(root: AccessibilityNodeInfo, delay: Long) {
        val allNodes = root.toString()
        if (allNodes.contains("AVL") || allNodes.contains("AVAILABLE")) {
            handler.postDelayed({
                if (clickAction(rootInActiveWindow ?: root, "PASSENGER DETAILS")) {
                    currentStep = 2
                }
            }, delay)
        }
    }

    private fun fillFormStep(root: AccessibilityNodeInfo, prefs: SharedPreferences, delay: Long) {
        clickAction(root, "OK") 

        val edits = getEdits(root)
        val total = (1..6).count { !(prefs.getString("n$it", "") ?: "").isNullOrEmpty() }

        if (edits.size >= 2 && (edits[0].text == null || edits[0].text.isEmpty())) {
            val name = prefs.getString("n$pIndex", "") ?: ""
            val age = prefs.getString("a$pIndex", "") ?: ""
            val gender = if (prefs.getString("g$pIndex", "M") == "F") "Female" else "Male"

            if (name.isNotEmpty()) {
                input(edits[0], name)
                input(edits[1], age)

                handler.postDelayed({
                    val r = rootInActiveWindow ?: return@postDelayed
                    if (clickAction(r, gender)) {
                        handler.postDelayed({
                            val r2 = rootInActiveWindow ?: return@postDelayed
                            if (clickAction(r2, "Add Passenger") || clickAction(r2, "ADD PASSENGER")) {
                                pIndex++
                            }
                        }, delay)
                    }
                }, delay)
            }
        } else if (pIndex > total && total > 0) {
            if (clickAction(root, "REVIEW JOURNEY DETAILS")) {
                handler.postDelayed({ currentStep = 0; pIndex = 1 }, 2000)
            }
        } else {
            clickAction(root, "Add New")
            clickAction(root, "ADD NEW")
        }
    }

    // --- स्मार्ट क्लिक (Crash Safe) ---
    private fun clickAction(root: AccessibilityNodeInfo?, text: String): Boolean {
        val nodes = root?.findAccessibilityNodeInfosByText(text) ?: return false
        for (n in nodes) {
            var p: AccessibilityNodeInfo? = n
            while (p != null) {
                if (p.isClickable) {
                    try { p.performAction(AccessibilityNodeInfo.ACTION_CLICK) } catch (e: Exception) { return false }
                    return true
                }
                p = p.parent
            }
        }
        return false
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
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            floatingStatus = TextView(this).apply {
                setBackgroundColor(Color.parseColor("#CC000000"))
                setTextColor(Color.WHITE)
                setPadding(20, 10, 20, 10)
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
            wm.addView(floatingStatus, params)
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onInterrupt() {}
}
