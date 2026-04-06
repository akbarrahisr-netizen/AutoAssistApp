package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*

class AssistiveFormService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    
    // Autofill Data
    private val userName = "Md Akbar"
    private val userAge = "28" 
    private val userGender = "Male"

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            val rootNode = rootInActiveWindow ?: return
            serviceScope.launch { handleForm(rootNode) }
        }
    }

    private suspend fun handleForm(root: AccessibilityNodeInfo) {
        val nameField = findNodeByHint(root, "Name")
        nameField?.let {
            setText(it, userName)
            delay(1200)
        }

        val ageField = findNodeByHint(root, "Age")
        ageField?.let {
            setText(it, userAge)
            delay(1200)
        }

        val genderNode = findNodeByText(root, userGender)
        genderNode?.let {
            it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(1500)
        }

        val proceedBtn = findNodeByText(root, "Proceed")
        proceedBtn?.let {
            it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(1500)
        }
    }

    private fun setText(node: AccessibilityNodeInfo, text: String) {
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val list = root.findAccessibilityNodeInfosByText(text)
        return list.firstOrNull()
    }

    private fun findNodeByHint(root: AccessibilityNodeInfo, hint: String): AccessibilityNodeInfo? {
        val queue = ArrayList<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeAt(0)
            val hintText = node.hintText?.toString() ?: ""
            if (hintText.contains(hint, ignoreCase = true)) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    override fun onInterrupt() {
        serviceScope.cancel()
    }
}
