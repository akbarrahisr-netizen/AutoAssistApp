package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import android.graphics.Color
import java.util.Calendar

class MyAccessibilityService : AccessibilityService() {

    private var floatingStatus: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastT = ""
    private var pIdx = 1

    override fun onServiceConnected() {
        super.onServiceConnected()
        showFloatingStatus()

        handler.postDelayed(object : Runnable {
            override fun run() {
                val prefs = getSharedPreferences("AutoData", Context.MODE_PRIVATE)
                val target = prefs.getString("t_time", "10:59:59") ?: "10:59:59"
                val now = String.format("%02d:%02d:%02d", Calendar.getInstance().get(Calendar.HOUR_OF_DAY), Calendar.getInstance().get(Calendar.MINUTE), Calendar.getInstance().get(Calendar.SECOND))

                floatingStatus?.text = "Time: $now | Status: Ready"

                if (now == target && lastT != now) {
                    val root = rootInActiveWindow
                    if (root?.packageName?.contains("irctc") == true) {
                        clickAction(root, "Refresh")
                        lastT = now
                    }
                }
                // यह चेक करने की रफ़्तार है, एक्शन की नहीं
                handler.postDelayed(this, 100) 
            }
        }, 100)
    }

    private fun showFloatingStatus() {
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
        params.y = 100
        try { wm.addView(floatingStatus, params) } catch (e: Exception) {}
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        if (!root.packageName.toString().contains("irctc")) return

        val prefs = getSharedPreferences("AutoData", Context.MODE_PRIVATE)
        val tNum = prefs.getString("t_num", "") ?: ""
        val userDelay = prefs.getString("c_delay", "0")?.toLong() ?: 0L

        // ट्रेन ढूँढना और क्लिक
        val train = root.findAccessibilityNodeInfosByText(tNum).firstOrNull()
        if (train != null) {
            handler.postDelayed({
                clickAction(root, prefs.getString("sel_cls", "SL") ?: "SL")
                clickAction(root, "PASSENGER DETAILS")
            }, userDelay)
        } else {
            root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }

        clickAction(root, "OK")
        fillFormFast(root, prefs, userDelay)
    }

    private fun fillFormFast(root: AccessibilityNodeInfo, prefs: android.content.SharedPreferences, delay: Long) {
        val edits = mutableListOf<AccessibilityNodeInfo>()
        findEdits(root, edits)
        val total = (1..6).count { !prefs.getString("n$it", "").isNullOrEmpty() }

        if (edits.size >= 2 && edits[0].text.isNullOrEmpty()) {
            val n = prefs.getString("n$pIdx", "") ?: ""
            val a = prefs.getString("a$pIdx", "") ?: ""
            val g = if (prefs.getString("g$pIdx", "M")?.uppercase() == "F") "Female" else "Male"
            
            if (n.isNotEmpty()) {
                val b1 = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, n) }
                edits[0].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b1)
                val b2 = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, a) }
                edits[1].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b2)
                clickAction(root, g)
                
                // यहाँ कोडिंग में कोई टाइम नहीं है, आपके बॉक्स वाला 'delay' इस्तेमाल हो रहा है
                handler.postDelayed({ 
                    clickAction(root, "Add Passenger")
                    pIdx++ 
                }, delay)
            }
        } else if (pIdx > total && total > 0) {
            clickAction(root, "REVIEW JOURNEY DETAILS")
        } else {
            clickAction(root, "+ Add New")
        }
    }

    private fun findEdits(n: AccessibilityNodeInfo?, l: MutableList<AccessibilityNodeInfo>) {
        if (n == null) return
        if (n.className == "android.widget.EditText") l.add(n)
        for (i in 0 until n.childCount) findEdits(n.getChild(i), l)
    }

    private fun clickAction(r: AccessibilityNodeInfo?, t: String) {
        val ns = r?.findAccessibilityNodeInfosByText(t) ?: return
        ns.forEach { it.performAction(AccessibilityNodeInfo.ACTION_CLICK); it.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
    }

    override fun onInterrupt() {}
}
