package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class MyAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // स्क्रीन पर जो कुछ भी दिख रहा है उसे हासिल करें
        val rootNode = rootInActiveWindow ?: return

        // 1. यहाँ उस बटन का नाम लिखें जिसे आप क्लिक करवाना चाहते हैं (जैसे "Login" या "OK")
        val targetText = "Login" 
        val nodes = rootNode.findAccessibilityNodeInfosByText(targetText)

        for (node in nodes) {
            // 2. अगर वो बटन क्लिक करने लायक है, तो उसे दबा दें
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Toast.makeText(this, "अकबर भाई, मैंने '$targetText' बटन दबा दिया! ✅", Toast.LENGTH_SHORT).show()
            }
            node.recycle()
        }
    }

    override fun onInterrupt() {}
}

