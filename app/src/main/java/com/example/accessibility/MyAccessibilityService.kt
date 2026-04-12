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

    private var pIdx = 1
    private val handler = Handler(Looper.getMainLooper())
    private var lastTrigger = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        // टाइमर: बॉक्स वाले टाइम पर तुरंत एक्शन
        handler.postDelayed(object : Runnable {
            override fun run() {
                val prefs = getSharedPreferences("AutoData", Context.MODE_PRIVATE)
                val target = prefs.getString("t_time", "10:59:59") ?: "10:59:59"
                val now = String.format("%02d:%02d:%02d", Calendar.getInstance().get(Calendar.HOUR_OF_DAY), Calendar.getInstance().get(Calendar.MINUTE), Calendar.getInstance().get(Calendar.SECOND))

                if (now == target && lastTrigger != now) {
                    val root = rootInActiveWindow
                    if (root?.packageName?.toString()?.contains("irctc") == true) {
                        if (!click(root, "Refresh")) click(root, "Updated")
                        lastTrigger = now
                    }
                }
                handler.postDelayed(this, 100)
            }
        }, 100)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString()?.contains("irctc") != true) return

        val prefs = getSharedPreferences("AutoData", Context.MODE_PRIVATE)
        val tNum = prefs.getString("t_num", "") ?: ""
        val cls = prefs.getString("sel_cls", "SL") ?: "SL"

        // 1. ट्रेन कार्ड ढूँढना और क्लास दबाना
        val train = find(root, tNum)
        if (train != null) {
            val card = findCard(train)
            if (card != null) {
                click(card, cls) // क्लास (SL/3A) दबाया
                // अगर AVL/AVAILABLE दिखा, तो PASSENGER DETAILS दबाओ
                if (find(card, "AVL") != null || find(card, "AVAILABLE") != null) {
                    click(root, "PASSENGER DETAILS")
                }
            }
        } else {
            root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }

        // 2. पैसेंजर फॉर्म (Zero Delay)
        click(root, "OK")
        val edits = mutableListOf<AccessibilityNodeInfo>()
        getEdits(root, edits)
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
        ns.forEach { 
            it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            it.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) // बॉक्स क्लिक के लिए ज़रूरी
        }
        return ns.isNotEmpty()
    }

    private fun find(r: AccessibilityNodeInfo?, t: String) = r?.findAccessibilityNodeInfosByText(t)?.firstOrNull()

    private fun findCard(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var p: AccessibilityNodeInfo? = n
        while (p?.parent != null) { 
            p = p.parent
            // अगर इस डिब्बे में Refresh या SL जैसा कुछ है, तो यही ट्रेन कार्ड है
            if (p?.findAccessibilityNodeInfosByText("Refresh")?.isNotEmpty() == true) return p
        }
        return null
    }

    override fun onInterrupt() {}
}
