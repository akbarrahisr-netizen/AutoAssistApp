package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.util.Calendar

class MyAccessibilityService : AccessibilityService() {

    private var pIdx = 1
    private val handler = Handler(Looper.getMainLooper())
    private var lastT = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        showToast("AutoAssist सेवा शुरू हो गई! 🚀")
        
        handler.postDelayed(object : Runnable {
            override fun run() {
                val prefs = getSharedPreferences("AutoData", Context.MODE_PRIVATE)
                val target = prefs.getString("t_time", "10:59:59") ?: "10:59:59"
                val now = String.format("%02d:%02d:%02d", Calendar.getInstance().get(Calendar.HOUR_OF_DAY), Calendar.getInstance().get(Calendar.MINUTE), Calendar.getInstance().get(Calendar.SECOND))

                if (now == target && lastT != now) {
                    val root = rootInActiveWindow
                    if (root?.packageName?.toString()?.contains("irctc") == true) {
                        if (!click(root, "Refresh")) click(root, "Updated")
                        lastT = now
                        showToast("टाइम हो गया! रिफ्रेश दबाया।")
                    }
                }
                handler.postDelayed(this, 150)
            }
        }, 150)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: ""
        
        if (!pkg.contains("irctc")) return

        val prefs = getSharedPreferences("AutoData", Context.MODE_PRIVATE)
        val tNum = prefs.getString("t_num", "") ?: ""
        val cls = prefs.getString("sel_cls", "SL") ?: "SL"

        // 1. ट्रेन और सीट सर्च
        if (tNum.isNotEmpty()) {
            val train = find(root, tNum)
            if (train != null) {
                val card = findCard(train)
                if (card != null) {
                    click(card, cls)
                    if (find(card, "AVL") != null || find(card, "AVAILABLE") != null) {
                        click(root, "PASSENGER DETAILS")
                    }
                }
            } else {
                root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }
        }

        // 2. पॉप-अप और पैसेंजर भरना
        click(root, "OK")
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
                click(root, g)
                handler.postDelayed({ click(root, "Add Passenger"); pIdx++ }, 20)
            }
        } else if (pIdx > total && total > 0) {
            click(root, "REVIEW JOURNEY DETAILS")
            if (find(root, "Payment") != null) pIdx = 1 // पेमेंट पेज पर रिसेट
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
        ns.forEach { 
            it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            it.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        return ns.isNotEmpty()
    }

    private fun input(n: AccessibilityNodeInfo, t: String) {
        val b = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, t) }
        n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    private fun find(r: AccessibilityNodeInfo?, t: String) = r?.findAccessibilityNodeInfosByText(t)?.firstOrNull()

    private fun findCard(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var p: AccessibilityNodeInfo? = n
        while (p?.parent != null) { 
            p = p.parent 
            val isCard = p?.findAccessibilityNodeInfosByText("Refresh")?.isNotEmpty() == true || 
                         p?.findAccessibilityNodeInfosByText("Updated")?.isNotEmpty() == true
            if (isCard) return p 
        }
        return null
    }

    private fun showToast(msg: String) {
        handler.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show() }
    }

    override fun onInterrupt() {}
}

