package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Handler
import android.os.Looper

class MyAccessibilityService : AccessibilityService() {

    private var currentPassengerIndex = 1
    private val handler = Handler(Looper.getMainLooper())

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val rootNode = rootInActiveWindow ?: return
        val prefs = getSharedPreferences("PassengerData", Context.MODE_PRIVATE)

        // 1. कुल पैसेंजर की गिनती (1 से 6)
        val totalToFill = getTotalNamesFilled(prefs)
        if (totalToFill == 0) return

        // 2. हर बार चेक करो - अगर 'OK' पॉप-अप दिखे तो तुरंत हटाओ (रुको मत)
        clickByText(rootNode, "OK")

        // 3. चेक करो - क्या हम 'Add Passenger' (फॉर्म) वाले पेज पर हैं?
        val editTexts = mutableListOf<AccessibilityNodeInfo>()
        findFields(rootNode, editTexts)

        if (editTexts.size >= 2 && editTexts[0].text.isNullOrEmpty()) {
            val name = prefs.getString("n$currentPassengerIndex", "") ?: ""
            val age = prefs.getString("a$currentPassengerIndex", "") ?: ""
            val gender = prefs.getString("g$currentPassengerIndex", "M") ?: "M"

            if (name.isNotEmpty()) {
                inputText(editTexts[0], name)
                inputText(editTexts[1], age)
                clickByText(rootNode, if (gender.uppercase() == "F") "Female" else "Male")

                handler.postDelayed({
                    clickByText(rootNode, "Add Passenger")
                    currentPassengerIndex++
                }, 100) // और भी तेज़ (100ms)
            }
            return
        }

        // 4. अगर पैसेंजर पूरे हो गए हैं, तो "REVIEW" बटन दबाओ
        // अगर 'Add New' नहीं भी दिखा, तब भी यह बटन दबाने की कोशिश करेगा
        if (currentPassengerIndex > totalToFill) {
            clickByText(rootNode, "REVIEW JOURNEY DETAILS")
            // काम पूरा होने के बाद काउंटर रिसेट
            if (findNodeByText(rootNode, "Select Payment Method") != null) {
                currentPassengerIndex = 1
            }
            return
        }

        // 5. अगर और पैसेंजर बाकी हैं, तो "+ Add New" ढूँढो और दबाओ
        if (currentPassengerIndex <= totalToFill) {
            clickByText(rootNode, "+ Add New")
        }
    }

    private fun getTotalNamesFilled(prefs: android.content.SharedPreferences): Int {
        var count = 0
        for (i in 1..6) {
            if (!prefs.getString("n$i", "").isNullOrEmpty()) count = i
        }
        return count
    }

    private fun findFields(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        if (node.className == "android.widget.EditText") list.add(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) findFields(child, list)
        }
    }

    private fun inputText(node: AccessibilityNodeInfo, text: String) {
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun clickByText(rootNode: AccessibilityNodeInfo, text: String) {
        val nodes = rootNode.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            if (node.isClickable) node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            else node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    private fun findNodeByText(rootNode: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodes = rootNode.findAccessibilityNodeInfosByText(text)
        return if (nodes.isNotEmpty()) nodes[0] else null
    }

    override fun onInterrupt() {}
}
