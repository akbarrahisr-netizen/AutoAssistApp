package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Calendar

class MyAccessibilityService : AccessibilityService() {

    private var currentIdx = 1
    private val handler = Handler(Looper.getMainLooper())
    private var lastRefreshTime = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        // बैकग्राउंड टाइमर (हर 200ms पर चेक करेगा)
        handler.postDelayed(object : Runnable {
            override fun run() {
                val prefs = getSharedPreferences("PassengerData", Context.MODE_PRIVATE)
                val target = prefs.getString("t_time", "10:59:58") ?: "10:59:58"
                
                val now = String.format("%02d:%02d:%02d", 
                    Calendar.getInstance().get(Calendar.HOUR_OF_DAY), 
                    Calendar.getInstance().get(Calendar.MINUTE), 
                    Calendar.getInstance().get(Calendar.SECOND))

                if (now == target && lastRefreshTime != now) {
                    val root = rootInActiveWindow
                    if (root != null) {
                        // 1. "Refresh" शब्द ढूँढो या फिर नीचे वाले "Updated" वाले आइकन को दबाओ
                        if (!clickByText(root, "Refresh")) {
                            clickByText(root, "Updated") // इस फोटो वाले बटन के लिए
                        }
                        lastRefreshTime = now
                    }
                }
                handler.postDelayed(this, 200)
            }
        }, 200)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: ""
        if (!pkg.contains("irctc")) return

        val root = rootInActiveWindow ?: return
        val prefs = getSharedPreferences("PassengerData", Context.MODE_PRIVATE)
        val delay = prefs.getString("c_delay", "200")?.toLong() ?: 200L

        // 1. आपकी क्लास (SL, 3A आदि) को मज़बूती से क्लिक करना
        val selCls = prefs.getString("sel_cls", "SL") ?: "SL"
        clickByText(root, selCls)

        // 2. सीट चेक (AVL देखते ही आगे बढ़ना)
        if (findNode(root, "AVL") != null || findNode(root, "AVAILABLE") != null) {
            clickByText(root, "PASSENGER DETAILS")
        }

        // 3. बीच के पॉप-अप हटाना
        clickByText(root, "OK")

        // 4. फॉर्म भरने का काम
        fillForm(root, prefs, delay)
    }

    private fun fillForm(root: AccessibilityNodeInfo, prefs: android.content.SharedPreferences, delay: Long) {
        val edits = mutableListOf<AccessibilityNodeInfo>()
        findFields(root, edits)
        val total = (1..6).count { !prefs.getString("n$it", "").isNullOrEmpty() }

        if (edits.size >= 2 && edits[0].text.isNullOrEmpty()) {
            val name = prefs.getString("n$currentIdx", "") ?: ""
            val age = prefs.getString("a$currentIdx", "") ?: ""
            val gender = if (prefs.getString("g$currentIdx", "M")?.uppercase() == "F") "Female" else "Male"

            if (name.isNotEmpty()) {
                input(edits[0], name)
                input(edits[1], age)
                clickByText(root, gender)
                handler.postDelayed({ 
                    clickByText(root, "Add Passenger")
                    currentIdx++ 
                }, delay)
            }
        } else if (currentIdx > total && total > 0) {
            clickByText(root, "REVIEW JOURNEY DETAILS")
        } else {
            clickByText(root, "+ Add New")
        }
    }

    // --- क्लिक करने का सबसे ताक़तवर तरीका ---
    private fun clickByText(root: AccessibilityNodeInfo?, text: String): Boolean {
        var clicked = false
        root?.findAccessibilityNodeInfosByText(text)?.forEach { node ->
            // खुद बटन को दबाओ
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                clicked = true
            }
            // अगर बटन नहीं दबा, तो उसके 'पिता' (Parent) को दबाओ (डिब्बे को क्लिक करने के लिए)
            node.parent?.let { 
                if (it.isClickable) {
                    it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    clicked = true
                }
            }
        }
        return clicked
    }

    private fun findFields(n: AccessibilityNodeInfo, l: MutableList<AccessibilityNodeInfo>) {
        if (n.className == "android.widget.EditText") l.add(n)
        for (i in 0 until n.childCount) n.getChild(i)?.let { findFields(it, l) }
    }

    private fun input(n: AccessibilityNodeInfo, t: String) {
        val b = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, t) }
        n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    private fun findNode(r: AccessibilityNodeInfo, t: String): AccessibilityNodeInfo? {
        val nodes = r.findAccessibilityNodeInfosByText(t)
        return if (nodes.isNotEmpty()) nodes[0] else null
    }

    override fun onInterrupt() {}
}

