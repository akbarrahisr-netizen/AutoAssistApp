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

    private var pIdx = 1
    private val handler = Handler(Looper.getMainLooper())
    private var lastTrigger = ""
    private var floatingStatus: TextView? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        showFloatingStatus() // स्क्रीन पर निशान और घड़ी दिखाना

        handler.postDelayed(object : Runnable {
            override fun run() {
                val prefs = getSharedPreferences("AutoData", Context.MODE_PRIVATE)
                val target = prefs.getString("t_time", "10:59:59") ?: "10:59:59"
                
                // वर्तमान समय सेकंड के साथ (HH:mm:ss)
                val cal = Calendar.getInstance()
                val now = String.format("%02d:%02d:%02d", 
                    cal.get(Calendar.HOUR_OF_DAY), 
                    cal.get(Calendar.MINUTE), 
                    cal.get(Calendar.SECOND))

                // स्क्रीन पर चल रही घड़ी को अपडेट करना
                floatingStatus?.text = "AutoAssist: $now" 

                // अगर टाइम मैच हो गया तो रिफ्रेश दबाओ
                if (now == target && lastTrigger != now) {
                    val root = rootInActiveWindow
                    if (root?.packageName?.toString()?.contains("irctc") == true) {
                        if (!click(root, "Refresh")) click(root, "Updated")
                        lastTrigger = now
                        floatingStatus?.setBackgroundColor(Color.RED) // रिफ्रेश होते ही लाल रंग
                        floatingStatus?.text = "Refreshing..."
                    }
                } else if (now != target) {
                    floatingStatus?.setBackgroundColor(Color.parseColor("#CC000000")) // सामान्य काला रंग
                }
                
                handler.postDelayed(this, 500) // हर आधे सेकंड में टाइम अपडेट करो
            }
        }, 500)
    }

    private fun showFloatingStatus() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingStatus = TextView(this).apply {
            text = "AutoAssist: Loading..."
            setBackgroundColor(Color.parseColor("#CC000000")) // पारदर्शी काला
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
        params.y = 50 // स्क्रीन से थोड़ा नीचे
        wm.addView(floatingStatus, params)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString()?.contains("irctc") != true) return

        val prefs = getSharedPreferences("AutoData", Context.MODE_PRIVATE)
        val tNum = prefs.getString("t_num", "") ?: ""
        val selCls = prefs.getString("sel_cls", "SL") ?: "SL"

        // ट्रेन ढूँढना और क्लिक करना
        val train = find(root, tNum)
        if (train != null) {
            val card = findCard(train)
            if (card != null) {
                click(card, selCls)
                if (find(card, "AVL") != null || find(card, "AVAILABLE") != null) {
                    click(root, "PASSENGER DETAILS")
                }
            }
        } else {
            root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }

        click(root, "OK")
        fillForm(root, prefs)
    }

    private fun fillForm(root: AccessibilityNodeInfo, prefs: android.content.SharedPreferences) {
        val edits = mutableListOf<AccessibilityNodeInfo>()
        getEdits(root, edits)
        val total = (1..6).count { !prefs.getString("n$it", "").isNullOrEmpty() }

        if (edits.size >= 2 && (edits[0].text == null || edits[0].text.isEmpty())) {
            val n = prefs.getString("n$pIdx", "") ?: ""
            val a = prefs.getString("a$pIdx", "") ?: ""
            val g = if (prefs.getString("g$pIdx", "M")?.uppercase() == "F") "Female" else "Male"
            
            if (n.isNotEmpty()) {
                val b1 = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, n) }
                edits[0].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b1)
                val b2 = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, a) }
                edits[1].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b2)
                click(root, g)
                click(root, "Add Passenger")
                pIdx++
            }
        } else if (pIdx > total && total > 0) {
            click(root, "REVIEW JOURNEY DETAILS")
        } else {
            click(root, "+ Add New")
        }
    }

    private fun getEdits(n: AccessibilityNodeInfo?, l: MutableList<AccessibilityNodeInfo>) {
        if (n == null) return
        if (n.className == "android.widget.EditText") l.add(n)
        for (i in 0 until n.childCount) getEdits(n.getChild(i), l)
    }

    private fun click(r: AccessibilityNodeInfo?, t: String): Boolean {
        val ns = r?.findAccessibilityNodeInfosByText(t) ?: return false
        ns.forEach { it.performAction(AccessibilityNodeInfo.ACTION_CLICK); it.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
        return ns.isNotEmpty()
    }

    private fun find(r: AccessibilityNodeInfo?, t: String) = r?.findAccessibilityNodeInfosByText(t)?.firstOrNull()

    private fun findCard(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var p: AccessibilityNodeInfo? = n
        while (p?.parent != null) { p = p.parent; if (p?.findAccessibilityNodeInfosByText("Refresh")?.isNotEmpty() == true) return p }
        return null
    }

    override fun onInterrupt() {}
}

