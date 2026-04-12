package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class MyAccessibilityService : AccessibilityService() {

    enum class Step { IDLE, SEARCH, ACTION, FILL }

    private var currentStep = Step.IDLE
    private val handler = Handler(Looper.getMainLooper())

    private var isRunning = false
    private var lastActionTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        showToast("Accessibility Service Started")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isRunning || event == null) return

        val root = rootInActiveWindow ?: return

        // simple throttle (safe)
        val now = System.currentTimeMillis()
        if (now - lastActionTime < 150) return

        process(root)
    }

    private fun process(root: AccessibilityNodeInfo) {
        when (currentStep) {

            Step.IDLE -> {
                currentStep = Step.SEARCH
            }

            Step.SEARCH -> {
                val found = findText(root, "Passenger")
                if (found) {
                    currentStep = Step.ACTION
                }
            }

            Step.ACTION -> {
                if (clickByText(root, "Continue")) {
                    showToast("Button Clicked")
                    currentStep = Step.FILL
                }
            }

            Step.FILL -> {
                fillFirstField(root, "Test User")
                currentStep = Step.IDLE
            }
        }
    }

    // ---------- SAFE HELPERS ----------

    private fun findText(root: AccessibilityNodeInfo, text: String): Boolean {
        return root.findAccessibilityNodeInfosByText(text).isNotEmpty()
    }

    private fun clickByText(root: AccessibilityNodeInfo, text: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByText(text)

        for (node in nodes) {
            var current: AccessibilityNodeInfo? = node

            repeat(5) {
                if (current?.isClickable == true && current.isEnabled) {
                    current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    lastActionTime = System.currentTimeMillis()
                    return true
                }
                current = current?.parent
            }
        }
        return false
    }

    private fun fillFirstField(root: AccessibilityNodeInfo, value: String) {
        val nodes = root.findAccessibilityNodeInfosByViewId("android:id/edit")

        for (node in nodes) {
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    value
                )
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            break
        }
    }

    private fun showToast(msg: String) {
        handler.post {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }
}
