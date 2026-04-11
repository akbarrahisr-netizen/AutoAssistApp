package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MyAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: ""
        if (!pkg.contains("irctc")) return

        val root = rootInActiveWindow ?: return
        val prefs = getSharedPreferences("PassengerData", Context.MODE_PRIVATE)
        val trainNumber = prefs.getString("sel_train", "") ?: ""
        val selectedClass = prefs.getString("sel_cls", "SL") ?: "SL"

        if (trainNumber.isEmpty()) return

        // 1. स्क्रीन पर ट्रेन नंबर ढूँढें
        val trainNode = findNodeByText(root, trainNumber)

        if (trainNode != null) {
            // ट्रेन मिल गई! अब उसके 'Parent' (पूरे कार्ड) के अंदर क्लास ढूँढो
            val trainCard = findParentCard(trainNode)
            if (trainCard != null) {
                clickInNode(trainCard, selectedClass)
                
                // अगर AVL दिखे तो आगे बढ़ें
                if (findNodeByText(trainCard, "AVL") != null || findNodeByText(trainCard, "AVAILABLE") != null) {
                    clickByText(root, "PASSENGER DETAILS")
                }
            }
        } else {
            // 2. ट्रेन नहीं मिली, तो स्क्रॉल डाउन करें
            scrollDown(root)
        }

        // बीच के पॉप-अप (OK) हटाते रहें
        clickByText(root, "OK")
    }

    // --- नीचे स्क्रॉल करने का फंक्शन ---
    private fun scrollDown(root: AccessibilityNodeInfo) {
        val scrollableNode = findScrollableNode(root)
        scrollableNode?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val found = findScrollableNode(child)
                if (found != null) return found
            }
        }
        return null
    }

    // ट्रेन का पूरा डिब्बा (Card) ढूँढना
    private fun findParentCard(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current = node
        while (current.parent != null) {
            current = current.parent
            // IRCTC में ट्रेन कार्ड आमतौर पर 'RelativeLayout' या 'LinearLayout' होते हैं
            if (current.className.contains("Layout") || current.className.contains("ViewGroup")) {
                // चेक करें कि क्या इस कार्ड के अंदर 'Refresh' या क्लास लिखी है
                if (current.findAccessibilityNodeInfosByText("Refresh").isNotEmpty()) return current
            }
        }
        return null
    }

    private fun clickInNode(parent: AccessibilityNodeInfo, text: String) {
        parent.findAccessibilityNodeInfosByText(text).forEach {
            if (it.isClickable) it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            else it.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    private fun clickByText(root: AccessibilityNodeInfo?, text: String) {
        root?.findAccessibilityNodeInfosByText(text)?.forEach {
            if (it.isClickable) it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            else it.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByText(text)
        return if (nodes.isNotEmpty()) nodes[0] else null
    }

    override fun onInterrupt() {}
}

