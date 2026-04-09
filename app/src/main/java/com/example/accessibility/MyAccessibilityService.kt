package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class MyAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // जब भी कोई नई स्क्रीन या ऐप खुलेगा, यह मैसेज दिखाएगा
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val appName = event.packageName?.toString() ?: "App"
            Toast.makeText(this, "Service Active: $appName", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onInterrupt() {}
}
