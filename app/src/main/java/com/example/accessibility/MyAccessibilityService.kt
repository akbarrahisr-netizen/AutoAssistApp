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

        // 1. ट्रेन नंबर मिलाएँ
        val trainNode = findNode(root, tNum)
        if (trainNode != null) {
            val trainCard = findTrainCard(trainNode)
            if (trainCard != null) {
                // 2. पहले क्लास (SL/3A) पर क्लिक करें
                clickAction(trainCard, selCls)

                // 3. क्लिक करने के बाद 'AVL' (हरा रंग) ढूँढें
                handler.postDelayed({
                    val isGreen = findNode(trainCard, "AVL") != null || findNode(trainCard, "AVAILABLE") != null
                    if (isGreen) {
                        // 4. हरा दिखा, तो सीधा Passenger Details दबाओ
                        clickAction(root, "PASSENGER DETAILS")
                        floatingStatus?.setBackgroundColor(Color.parseColor("#4CAF50"))
                    }
                }, userDelay) // आपके बॉक्स वाला डिले यहाँ काम करेगा
            }
        } else {
            root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }

        clickAction(root, "OK")
        fillForm(root, prefs, userDelay)
    }

    // --- ट्रेन कार्ड ढूँढने का स्मार्ट तरीका ---
    private fun findTrainCard(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var p: AccessibilityNodeInfo? = node
        while (p != null && p.parent != null) {
            p = p.parent
            if (p?.findAccessibilityNodeInfosByText("Refresh")?.isNotEmpty() == true || 
                p?.findAccessibilityNodeInfosByText("Updated")?.isNotEmpty() == true) return p
        }
        return null
    }

    private fun clickAction(root: AccessibilityNodeInfo?, text: String) {
        val nodes = root?.findAccessibilityNodeInfosByText(text) ?: return
        for (node in nodes) {
            var temp: AccessibilityNodeInfo? = node
            while (temp != null) {
                if (temp.isClickable) {
                    temp.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return
                }
                temp = temp.parent
            }
        }
    }

    private fun fillForm(root: AccessibilityNodeInfo, prefs: android.content.SharedPreferences, delay: Long) {
        val edits = mutableListOf<AccessibilityNodeInfo>()
        findEdits(root, edits)
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
                clickAction(root, g)
                handler.postDelayed({ clickAction(root, "Add Passenger"); pIdx++ }, delay)
            }
        } else if (pIdx > total && total > 0) {
            clickAction(root, "REVIEW JOURNEY DETAILS")
        } else {
            clickAction(root, "+ Add New")
        }
    }

    private fun findNode(root: AccessibilityNodeInfo, text: String) = root.findAccessibilityNodeInfosByText(text).firstOrNull()

    private fun findEdits(n: AccessibilityNodeInfo?, l: MutableList<AccessibilityNodeInfo>) {
        if (n == null) return
        if (n.className == "android.widget.EditText") l.add(n)
        for (i in 0 until n.childCount) findEdits(n.getChild(i), l)
    }

    private fun showFloatingStatus() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingStatus = TextView(this).apply {
            setBackgroundColor(Color.parseColor("#CC000000"))
            setTextColor(Color.WHITE)
            setPadding(20, 10, 20, 10)
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

