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
        // बैकग्राउंड टाइमर: यह बॉक्स में भरे टाइम को चेक करेगा
        handler.postDelayed(object : Runnable {
            override fun run() {
                val prefs = getSharedPreferences("PassengerData", Context.MODE_PRIVATE)
                val target = prefs.getString("t_time", "10:59:58") ?: "10:59:58"
                val now = String.format("%02d:%02d:%02d", Calendar.getInstance().get(Calendar.HOUR_OF_DAY), Calendar.getInstance().get(Calendar.MINUTE), Calendar.getInstance().get(Calendar.SECOND))

                if (now == target && !isTriggered) {
                    val root = rootInActiveWindow
                    clickByText(root, "Refresh") ?: clickByText(root, "Updated")
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
        val trainNum = prefs.getString("sel_train", "") ?: ""
        val selCls = prefs.getString("sel_cls", "SL") ?: "SL"
        val delay = prefs.getString("c_delay", "200")?.toLong() ?: 200L

        // 1. ट्रेन नंबर को ढूँढें और स्क्रॉल करें
        val trainNode = findNode(root, trainNum)
        if (trainNode != null) {
            val card = findParentCard(trainNode)
            if (card != null) {
                clickByText(card, selCls) // आपकी चुनी हुई क्लास पर क्लिक
                if (findNode(card, "AVL") != null || findNode(card, "AVAILABLE") != null) {
                    clickByText(root, "PASSENGER DETAILS") // सीट हरी होते ही आगे बढ़ें
                }
            }
        } else {
            scrollDown(root) // ट्रेन नहीं मिली तो नीचे स्क्रॉल करें
        }

        // 2. पैसेंजर फॉर्म भरना
        fillFormSequence(root, prefs, delay)
    }

    private fun fillFormSequence(root: AccessibilityNodeInfo, prefs: android.content.SharedPreferences, delay: Long) {
        clickByText(root, "OK")
        val edits = mutableListOf<AccessibilityNodeInfo>()
        findFields(root, edits)
        val total = (1..6).count { !prefs.getString("n$it", "").isNullOrEmpty() }

        if (edits.size >= 2 && edits[0].text.isNullOrEmpty()) {
            val n = prefs.getString("n$currentIdx", "") ?: ""
            val a = prefs.getString("a$currentIdx", "") ?: ""
            val g = if (prefs.getString("g$currentIdx", "M")?.uppercase() == "F") "Female" else "Male"
            if (n.isNotEmpty()) {
                input(edits[0], n); input(edits[1], a); clickByText(root, g)
                handler.postDelayed({ clickByText(root, "Add Passenger"); currentIdx++ }, delay)
            }
        } else if (currentIdx > total && total > 0) {
            clickByText(root, "REVIEW JOURNEY DETAILS")
        } else {
            clickByText(root, "+ Add New")
        }
    }

    private fun scrollDown(root: AccessibilityNodeInfo) {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        findScrollable(root, nodes)
        nodes.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    private fun findScrollable(n: AccessibilityNodeInfo, l: MutableList<AccessibilityNodeInfo>) {
        if (n.isScrollable) l.add(n)
        for (i in 0 until n.childCount) n.getChild(i)?.let { findScrollable(it, l) }
    }

    private fun findParentCard(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var p = n
        while (p.parent != null) {
            p = p.parent
            if (p.findAccessibilityNodeInfosByText("Refresh").isNotEmpty() || p.findAccessibilityNodeInfosByText("Updated").isNotEmpty()) return p
        }
        return null
    }

    private fun findFields(n: AccessibilityNodeInfo, l: MutableList<AccessibilityNodeInfo>) {
        if (n.className == "android.widget.EditText") l.add(n)
        for (i in 0 until n.childCount) n.getChild(i)?.let { findFields(it, l) }
    }

    private fun input(n: AccessibilityNodeInfo, t: String) {
        val b = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, t) }
        n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    private fun clickByText(r: AccessibilityNodeInfo?, t: String): Boolean {
        val ns = r?.findAccessibilityNodeInfosByText(t) ?: return false
        ns.forEach { if (it.isClickable) it.performAction(AccessibilityNodeInfo.ACTION_CLICK) else it.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
        return ns.isNotEmpty()
    }

    private fun findNode(r: AccessibilityNodeInfo, t: String) = r.findAccessibilityNodeInfosByText(t).firstOrNull()

    override fun onInterrupt() {}
}

