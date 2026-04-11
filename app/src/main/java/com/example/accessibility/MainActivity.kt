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

        // 1. रिफ्रेश टाइम
        layout.addView(TextView(this).apply { text = "ऑटो रिफ्रेश टाइम (HH:mm:ss):"; textColor = Color.RED })
        val timeIn = EditText(this).apply { hint = "10:59:58"; setText(prefs.getString("t_time", "10:59:58")) }
        layout.addView(timeIn)

        // 2. बुकिंग क्लास (SL, 3A, आदि)
        layout.addView(TextView(this).apply { text = "\nक्लास चुनें (SL, 3A, 2A, 3E):"; textColor = Color.BLUE })
        val radioGroup = RadioGroup(this)
        val classes = listOf("SL", "3A", "2A", "3E")
        val savedCls = prefs.getString("sel_cls", "SL")
        for (cls in classes) {
            val rb = RadioButton(this).apply { text = cls; if (cls == savedCls) isChecked = true }
            radioGroup.addView(rb)
        }
        layout.addView(radioGroup)

        // 3. क्लिक डिले (स्पीड)
        layout.addView(TextView(this).apply { text = "\nक्लिक स्पीड (ms):" })
        val delayIn = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText(prefs.getString("c_delay", "200")) }
        layout.addView(delayIn)

        // 4. पैसेंजर लिस्ट (6 लोग)
        layout.addView(TextView(this).apply { text = "\nपैसेंजर (नाम, उम्र, M/F):" })
        val inputs = mutableListOf<Triple<EditText, EditText, EditText>>()
        for (i in 1..6) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val n = EditText(this).apply { hint = "नाम $i"; layoutParams = LinearLayout.LayoutParams(0, -2, 2f); setText(prefs.getString("n$i", "")) }
            val a = EditText(this).apply { hint = "उम्र"; layoutParams = LinearLayout.LayoutParams(0, -2, 1f); inputType = 2; setText(prefs.getString("a$i", "")) }
            val g = EditText(this).apply { hint = "M/F"; layoutParams = LinearLayout.LayoutParams(0, -2, 0.8f); setText(prefs.getString("g$i", "")) }
            row.addView(n); row.addView(a); row.addView(g); layout.addView(row)
            inputs.add(Triple(n, a, g))
        }

        val saveBtn = Button(this).apply { text = "सब सेव करें ✅" }
        saveBtn.setOnClickListener {
            val ed = prefs.edit()
            ed.putString("t_time", timeIn.text.toString())
            ed.putString("c_delay", delayIn.text.toString())
            val rb = findViewById<RadioButton>(radioGroup.checkedRadioButtonId)
            ed.putString("sel_cls", rb?.text.toString())
            for (i in 0..5) {
                ed.putString("n${i+1}", inputs[i].first.text.toString())
                ed.putString("a${i+1}", inputs[i].second.text.toString())
                ed.putString("g${i+1}", inputs[i].third.text.toString())
            }
            ed.apply()
            Toast.makeText(this, "डाटा सेट हो गया!", 0).show()
        }
        layout.addView(saveBtn)
        setContentView(scrollView)
    }
}
