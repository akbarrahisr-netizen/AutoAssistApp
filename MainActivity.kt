package com.example.accessibility

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.view.Gravity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // एक सिंपल स्क्रीन जो बताएगी कि ऐप काम कर रहा है
        val textView = TextView(this)
        textView.text = "अकबर भाई, AutoAssist अब आपके सामने है!\n\nअब आप इसे Accessibility में जाकर चालू कर सकते हैं।"
        textView.textSize = 20f
        textView.gravity = Gravity.CENTER
        setContentView(textView)
    }
}
