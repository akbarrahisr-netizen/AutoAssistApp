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
                val target = prefs.getString("t_time", "11:00:00") ?: "11:00:00"
                val now = String.format("%02d:%02d:%02d", Calendar.getInstance().get(Calendar.HOUR_OF_DAY), Calendar.getInstance().get(Calendar.MINUTE), Calendar.getInstance().get(Calendar.SECOND))
                floatingStatus?.text = "Time: $now | Status: Hunting"
                if (now == target && lastT != now) {
                    val root = rootInActiveWindow
                    if (root?.packageName?.contains("irctc") == true) {
                        clickAction(root, "Refresh")
                        clickAction(root, "Updated")
                        lastT = now
                    }
                }
                handler.postDelayed(this, 100) 
            }
        }, 100)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        if (!root.packageName.toString().contains("irctc")) return

        val prefs = getSharedPreferences("AutoData", Context.MODE_PRIVATE)
        val tNum = prefs.getString("t_num", "") ?: ""
        val selCls = prefs.getString("sel_cls", "SL") ?: "SL"
        val userDelay = prefs.getString("c_delay", "0")?.toLong() ?: 0L

        // 1. सही ट्रेन ढूँढो
        val trainNode = root.findAccessibilityNodeInfosByText(tNum).firstOrNull()
        if (trainNode != null) {
            // उस ट्रेन के कार्ड के अंदर क्लास दबाओ
            val card = findCard(trainNode)
            clickAction(card ?: root, selCls)

            // 2. हरे रंग (AVL) की जाँच और पैसेंजर बटन क्लिक
            handler.postDelayed({
                val isAvl = findNode(root, "AVL") != null || findNode(root, "AVAILABLE") != null
                if (isAvl) {
                    clickAction(root, "PASSENGER DETAILS")
                }
            }, userDelay)
        } else {
            root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }

        clickAction(root, "OK")
        fillForm(root, prefs, userDelay)
    }

    private fun fillForm(root: AccessibilityNodeInfo, prefs: android.content.SharedPreferences, delay: Long) {
        val edits = mutableListOf<AccessibilityNodeInfo>()
        getEdits(root, edits)
        val total = (1..6).count { !prefs.getString("n$it", "").isNullOrEmpty() }

        if (edits.size >= 2 && (edits[0].text == null || edits[0].text.isEmpty())) {
            val n = prefs.getString("n$pIdx", "") ?: ""
            val a = prefs.getString("a$pIdx", "") ?: ""
            val g = if (prefs.getString("g$pIdx", "M")?.uppercase() == "F") "Female" else "Male"
            if (n.isNotEmpty()) {
                input(edits[0], n)
                input(edits[1], a)
                clickAction(root, g)
                handler.postDelayed({ clickAction(root, "Add Passenger"); pIdx++ }, delay)
            }
        } else if (pIdx > total && total > 0) {
            clickAction(root, "REVIEW JOURNEY DETAILS")
            if (findNode(root, "Payment") != null) pIdx = 1
        } else {
            clickAction(root, "+ Add New")
        }
    }

    private fun clickAction(root: AccessibilityNodeInfo?, text: String) {
        val nodes = root?.findAccessibilityNodeInfosByText(text) ?: return
        for (node in nodes) {
            var temp: AccessibilityNodeInfo? = node
            while (temp != null) {
                if (temp.isClickable) { temp.performAction(AccessibilityNodeInfo.ACTION_CLICK); return }
                temp = temp.parent
            }
        }
    }

    private fun findCard(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var p: AccessibilityNodeInfo? = n
        while (p?.parent != null) {
            p = p.parent
            if (p?.findAccessibilityNodeInfosByText("Refresh")?.isNotEmpty() == true) return p
        }
        return null
    }

    private fun findNode(root: AccessibilityNodeInfo, text: String) = root.findAccessibilityNodeInfosByText(text).firstOrNull()
    private fun input(node: AccessibilityNodeInfo, text: String) {
        val b = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }
    private fun getEdits(n: AccessibilityNodeInfo?, l: MutableList<AccessibilityNodeInfo>) {
        if (n == null) return
        if (n.className == "android.widget.EditText") l.add(n)
        for (i in 0 until n.childCount) getEdits(n.getChild(i), l)
    }
    private fun showFloatingStatus() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingStatus = TextView(this).apply { setBackgroundColor(Color.parseColor("#CC000000")); setTextColor(Color.WHITE); setPadding(20, 10, 20, 10) }
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = 80
        try { wm.addView(floatingStatus, params) } catch (e: Exception) {}
    }
    override fun onInterrupt() {}
}
