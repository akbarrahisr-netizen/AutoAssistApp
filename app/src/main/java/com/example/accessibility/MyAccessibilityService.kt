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
    private var isTriggered = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        // बैकग्राउंड में घड़ी चालू करें
        handler.postDelayed(object : Runnable {
            override fun run() {
                val prefs = getSharedPreferences("PassengerData", Context.MODE_PRIVATE)
                val target = prefs.getString("t_time", "10:59:58") ?: "10:59:58"
                val now = String.format("%02d:%02d:%02d", Calendar.getInstance().get(Calendar.HOUR_OF_DAY), Calendar.getInstance().get(Calendar.MINUTE), Calendar.getInstance().get(Calendar.SECOND))

                if (now == target && !isTriggered) {
                    clickByText(rootInActiveWindow, "Refresh")
                    isTriggered = true
                }
                handler.postDelayed(this, 500)
            }
        }, 500)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: ""
        if (!pkg.contains("irctc")) return

        val root = rootInActiveWindow ?: return
        val prefs = getSharedPreferences("PassengerData", Context.MODE_PRIVATE)
        val delay = prefs.getString("c_delay", "200")?.toLong() ?: 200L

        // 1. चुनी हुई क्लास (3A, SL आदि) पर क्लिक करें
        val selCls = prefs.getString("sel_cls", "SL") ?: "SL"
        clickByText(root, selCls)

        // 2. अगर हरा "AVL" दिखा, तो सीधे पैसेंजर डिटेल्स पर जाएँ
        if (findNode(root, "AVL") != null || findNode(root, "AVAILABLE") != null) {
            clickByText(root, "PASSENGER DETAILS")
        }

        // 3. पैसेंजर फॉर्म भरना
        clickByText(root, "OK") // पॉप-अप हटाएँ
        val edits = mutableListOf<AccessibilityNodeInfo>()
        findFields(root, edits)

        val total = (1..6).count { !prefs.getString("n$it", "").isNullOrEmpty() }

        if (edits.size >= 2 && edits[0].text.isNullOrEmpty()) {
            val name = prefs.getString("n$currentIdx", "") ?: ""
            val age = prefs.getString("a$currentIdx", "") ?: ""
            val gender = if (prefs.getString("g$currentIdx", "M") == "F") "Female" else "Male"

            if (name.isNotEmpty()) {
                input(edits[0], name)
                input(edits[1], age)
                clickByText(root, gender)
                handler.postDelayed({ clickByText(root, "Add Passenger"); currentIdx++ }, delay)
            }
        } else if (currentIdx > total && total > 0) {
            handler.postDelayed({ clickByText(root, "REVIEW JOURNEY DETAILS") }, delay)
        } else {
            clickByText(root, "+ Add New")
        }
    }

    private fun findFields(n: AccessibilityNodeInfo, l: MutableList<AccessibilityNodeInfo>) {
        if (n.className == "android.widget.EditText") l.add(n)
        for (i in 0 until n.childCount) n.getChild(i)?.let { findFields(it, l) }
    }

    private fun input(n: AccessibilityNodeInfo, t: String) {
        val b = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, t) }
        n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    private fun clickByText(r: AccessibilityNodeInfo?, t: String) {
        r?.findAccessibilityNodeInfosByText(t)?.forEach {
            if (it.isClickable) it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            else it.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    private fun findNode(r: AccessibilityNodeInfo, t: String): AccessibilityNodeInfo? {
        val n = r.findAccessibilityNodeInfosByText(t)
        return if (n.isNotEmpty()) n[0] else null
    }

    override fun onInterrupt() {}
}
