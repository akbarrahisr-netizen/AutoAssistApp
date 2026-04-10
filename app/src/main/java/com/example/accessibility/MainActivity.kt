package com.example.accessibility

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.view.Gravity

class MainActivity : Activity() { // यहाँ हमने AppCompat हटा दिया है
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val textView = TextView(this)
        textView.text = "अकबर भाई, AutoAssist अब तैयार है!\n\nAccessibility में जाकर इसे चालू करें।"
        textView.textSize = 22f
        textView.gravity = Gravity.CENTER
        setContentView(textView)
    }
}
