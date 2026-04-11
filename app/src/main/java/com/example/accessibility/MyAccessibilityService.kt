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

    private var currentPassengerIndex = 1
    private val handler = Handler(Looper.getMainLooper())
    private var isTimeTriggered = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        startClock()
    }

    private fun startClock() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                val prefs = getSharedPreferences("PassengerData", Context.MODE_PRIVATE)
                val target = prefs.getString("target_time", "10:59:58") ?: "10:59:58"
                
                val cal = Calendar.getInstance()
                val now = String.format("%02d:%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND))

                if (now == target && !isTimeTriggered) {
                    clickByText(rootInActiveWindow, "Refresh")
                    isTimeTriggered = true
                }
                handler.postDelayed(this, 1000)
            }
        }, 1000)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: ""
        if (!packageName.contains("irctc")) return

        val rootNode = rootInActiveWindow ?: return
        val prefs = getSharedPreferences("PassengerData", Context.MODE_PRIVATE)

        // --- 'अवेलेबल' (AVL) चेक ---
        if (prefs.getBoolean("check_avl", true)) {
            val avlNodes = rootNode.findAccessibilityNodeInfosByText("AVL")
            val availableNodes = rootNode.findAccessibilityNodeInfosByText("AVAILABLE")
            
            if (avlNodes.isNotEmpty() || availableNodes.isNotEmpty()) {
                clickByText(rootNode, "PASSENGER DETAILS")
            }
        }

        // पैसेंजर फॉर्म भरने वाला पुराना लॉजिक यहाँ काम करता रहेगा
        processPassengerForms(rootNode, prefs)
    }

    private fun processPassengerForms(rootNode: AccessibilityNodeInfo, prefs: android.content.SharedPreferences) {
        val editTexts = mutableListOf<AccessibilityNodeInfo>()
        findFields(rootNode, editTexts)
        
        val total = (1..6).count { !prefs.getString("n$it", "").isNullOrEmpty() }

        if (editTexts.size >= 2 && editTexts[0].text.isNullOrEmpty()) {
            val name = prefs.getString("n$currentPassengerIndex", "") ?: ""
            val age = prefs.getString("a$currentPassengerIndex", "") ?: ""
            val gender = prefs.getString("g$currentPassengerIndex", "M") ?: "M"

            if (name.isNotEmpty()) {
                inputText(editTexts[0], name)
                inputText(editTexts[1], age)
                clickByText(rootNode, if (gender.uppercase() == "F") "Female" else "Male")
                handler.postDelayed({ clickByText(rootNode, "Add Passenger"); currentPassengerIndex++ }, 150)
            }
        } else if (currentPassengerIndex > total && total > 0) {
            clickByText(rootNode, "REVIEW JOURNEY DETAILS")
        } else {
            clickByText(rootNode, "OK")
            clickByText(rootNode, "+ Add New")
        }
    }

    private fun findFields(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        if (node.className == "android.widget.EditText") list.add(node)
        for (i in 0 until node.childCount) node.getChild(i)?.let { findFields(it, list) }
    }

    private fun inputText(node: AccessibilityNodeInfo, text: String) {
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun clickByText(rootNode: AccessibilityNodeInfo?, text: String) {
        rootNode?.findAccessibilityNodeInfosByText(text)?.forEach {
            if (it.isClickable) it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            else it.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    override fun onInterrupt() {}
}
