package com.example.accessibility

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.*
import android.view.ViewGroup
import android.widget.LinearLayout
import android.graphics.Color
import android.text.InputType

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("PassengerData", Context.MODE_PRIVATE)
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 30, 30, 30) }
        scrollView.addView(layout)

        // --- 1. ट्रेन नंबर डालने का डिब्बा ---
        layout.addView(TextView(this).apply { text = "ट्रेन नंबर (जैसे 12487):"; textColor = Color.parseColor("#FF5722"); textSize = 16f })
        val trainNumIn = EditText(this).apply { 
            hint = "ट्रेन नंबर यहाँ लिखें"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getString("sel_train", "")) 
        }
        layout.addView(trainNumIn)

        // --- बाकी पुरानी सेटिंग्स (Time, Class, Delay, Passengers) ---
        // (पुराना कोड यहाँ जारी रखें...)
        layout.addView(TextView(this).apply { text = "\nरिफ्रेश टाइम (HH:mm:ss):" })
        val timeIn = EditText(this).apply { setText(prefs.getString("t_time", "10:59:58")) }
        layout.addView(timeIn)

        layout.addView(TextView(this).apply { text = "\nक्लास (SL, 3A, 2A):" })
        val radioGroup = RadioGroup(this)
        listOf("SL", "3A", "2A", "3E").forEach { cls ->
            val rb = RadioButton(this).apply { text = cls; if (cls == prefs.getString("sel_cls", "SL")) isChecked = true }
            radioGroup.addView(rb)
        }
        layout.addView(radioGroup)

        // --- सेव बटन ---
        val saveBtn = Button(this).apply { text = "सब सेटिंग्स सेव करें ✅" }
        saveBtn.setOnClickListener {
            val ed = prefs.edit()
            ed.putString("sel_train", trainNumIn.text.toString()) // ट्रेन नंबर सेव
            ed.putString("t_time", timeIn.text.toString())
            val rb = findViewById<RadioButton>(radioGroup.checkedRadioButtonId)
            ed.putString("sel_cls", rb?.text.toString())
            ed.apply()
            Toast.makeText(this, "ट्रेन $trainNumIn सेट हो गई!", 0).show()
        }
        layout.addView(saveBtn)
        setContentView(scrollView)
    }
}

