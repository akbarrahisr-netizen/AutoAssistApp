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
                val target = prefs.getString("t_time", "11:59:59") ?: "11:59:59"
                val now = String.format("%02d:%02d:%02d", Calendar.getInstance().get(Calendar.HOUR_OF_DAY), Calendar.getInstance().get(Calendar.MINUTE), Calendar.getInstance().get(Calendar.SECOND))

                floatingStatus?.text = "Time: $now | Status: Ready"

                if (now == target && lastT != now) {
                    val root = rootInActiveWindow
                    if (root?.packageName?.contains("irctc") == true) {
                        clickByText(root, "Refresh")
                        lastT = now
                        floatingStatus?.setBackgroundColor(Color.RED)
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

        // 1. सही ट्रेन का डिब्बा ढूँढना
        val trainNode = findNodeByText(root, tNum)
        if (trainNode != null) {
            val trainCard = findTrainCard(trainNode)
            if (trainCard != null) {
                // सिर्फ इसी ट्रेन की क्लास पर क्लिक करो
                handler.postDelayed({
                    clickByText(trainCard, selCls)
                    // पैसेंजर डिटेल्स बटन पूरे पेज पर कहीं भी हो सकता है
                    clickByText(root, "PASSENGER DETAILS")
                }, userDelay)
            }
        } else {
            // अगर ट्रेन नहीं दिख रही तो नीचे स्क्रॉल करो
            root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }

        // 2. पॉप-अप और पैसेंजर फॉर्म
        clickByText(root, "OK")
        fillPassengerForm(root, prefs, userDelay)
    }

    private fun fillPassengerForm(root: AccessibilityNodeInfo, prefs: android.content.SharedPreferences, delay: Long) {
        val edits = mutableListOf<AccessibilityNodeInfo>()
        findEditTexts(root, edits)
        val total = (1..6).count { !prefs.getString("n$it", "").isNullOrEmpty() }

        if (edits.size >= 2 && (edits[0].text == null || edits[0].text.isEmpty())) {
            val name = prefs.getString("n$pIdx", "") ?: ""
            val age = prefs.getString("a$pIdx", "") ?: ""
            val gender = if (prefs.getString("g$pIdx", "M")?.uppercase() == "F") "Female" else "Male"
            
            if (name.isNotEmpty()) {
                inputText(edits[0], name)
                inputText(edits[1], age)
                clickByText(root, gender)
                handler.postDelayed({ 
                    clickByText(root, "Add Passenger")
                    pIdx++ 
                }, delay)
            }
        } else if (pIdx > total && total > 0) {
            clickByText(root, "REVIEW JOURNEY DETAILS")
            if (findNodeByText(root, "Payment") != null) pIdx = 1 // रिसेट
        } else {
            clickByText(root, "+ Add New")
        }
    }

    // --- मददगार फंक्शन्स (Helper Functions) ---

    private fun clickByText(root: AccessibilityNodeInfo?, text: String): Boolean {
        val nodes = root?.findAccessibilityNodeInfosByText(text) ?: return false
        for (node in nodes) {
            if (performClick(node)) return true
        }
        return false
    }

    private fun performClick(node: AccessibilityNodeInfo?): Boolean {
        var temp = node
        while (temp != null) {
            if (temp.isClickable) {
                temp.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
            temp = temp.parent
        }
        return false
    }

    private fun findTrainCard(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var p = node
        while (p.parent != null) {
            p = p.parent
            // IRCTC में ट्रेन का डिब्बा 'Refresh' बटन से पहचाना जाता है
            if (p.findAccessibilityNodeInfosByText("Refresh").isNotEmpty() || 
                p.findAccessibilityNodeInfosByText("Updated").isNotEmpty()) return p
        }
        return null
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String) = 
        root.findAccessibilityNodeInfosByText(text).firstOrNull()

    private fun findEditTexts(n: AccessibilityNodeInfo?, l: MutableList<AccessibilityNodeInfo>) {
        if (n == null) return
        if (n.className == "android.widget.EditText") l.add(n)
        for (i in 0 until n.childCount) findEditTexts(n.getChild(i), l)
    }

    private fun inputText(node: AccessibilityNodeInfo, text: String) {
        val b = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    private fun showFloatingStatus() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingStatus = TextView(this).apply {
            setBackgroundColor(Color.parseColor("#CC000000"))
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
        try { wm.addView(floatingStatus, params) } catch (e: Exception) {}
    }

    override fun onInterrupt() {}
}
