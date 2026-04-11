package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MyAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val rootNode = rootInActiveWindow ?: return
        
        val prefs = getSharedPreferences("PassengerData", Context.MODE_PRIVATE)
        val passengers = mutableListOf<Pair<String, String>>()
        
        for (i in 1..4) {
            val name = prefs.getString("name_$i", "") ?: ""
            val age = prefs.getString("age_$i", "") ?: ""
            if (name.isNotEmpty()) {
                passengers.add(Pair(name, age))
            }
        }

        if (passengers.isEmpty()) return

        fillAllPassengers(rootNode, passengers)
    }

    private fun fillAllPassengers(rootNode: AccessibilityNodeInfo, passengers: List<Pair<String, String>>) {
        val editTexts = mutableListOf<AccessibilityNodeInfo>()
        findAllEditTexts(rootNode, editTexts)

        var passengerIndex = 0
        var i = 0
        while (i < editTexts.size && passengerIndex < passengers.size) {
            val nameField = editTexts[i]
            val ageField = if (i + 1 < editTexts.size) editTexts[i + 1] else null

            if (nameField.text == null || nameField.text.isEmpty()) {
                inputText(nameField, passengers[passengerIndex].first)
                
                if (ageField != null && (ageField.text == null || ageField.text.isEmpty())) {
                    inputText(ageField, passengers[passengerIndex].second)
                    i++ 
                }
                passengerIndex++
            }
            i++
        }
    }

    private fun findAllEditTexts(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        if (node.className == "android.widget.EditText") list.add(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) findAllEditTexts(child, list)
        }
    }

    private fun inputText(node: AccessibilityNodeInfo, text: String) {
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    override fun onInterrupt() {}
}
 
