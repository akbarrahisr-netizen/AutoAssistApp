package com.example.accessibility

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.*
import android.view.ViewGroup
import android.widget.LinearLayout
import android.graphics.Color

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("PassengerData", Context.MODE_PRIVATE)
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 30, 30, 30) }
        scrollView.addView(layout)

        // --- टाइमिंग सेटिंग ---
        val timeTitle = TextView(this).apply { text = "ऑटो-रिफ्रेश टाइम (HH:mm:ss):"; textSize = 16f; setTextColor(Color.RED) }
        val timeInput = EditText(this).apply { hint = "10:59:58"; setText(prefs.getString("target_time", "10:59:58")) }
        layout.addView(timeTitle); layout.addView(timeInput)

        // --- अवेलेबल चेक सेटिंग ---
        val avlTitle = TextView(this).apply { text = "\nसीट चेक (AVL होने पर ही आगे बढ़ें):"; textSize = 16f }
        val avlSwitch = Switch(this).apply { text = "Auto-Proceed on AVL"; isChecked = prefs.getBoolean("check_avl", true) }
        layout.addView(avlTitle); layout.addView(avlSwitch)

        layout.addView(TextView(this).apply { text = "\n--- पैसेंजर लिस्ट (Max 6) ---"; textSize = 18f; setTextColor(Color.BLUE) })

        val inputs = mutableListOf<Triple<EditText, EditText, EditText>>()
        for (i in 1..6) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val name = EditText(this).apply { hint = "नाम $i"; layoutParams = LinearLayout.LayoutParams(0, -2, 2f); setText(prefs.getString("n$i", "")) }
            val age = EditText(this).apply { hint = "उम्र"; layoutParams = LinearLayout.LayoutParams(0, -2, 1f); inputType = 2; setText(prefs.getString("a$i", "")) }
            val gender = EditText(this).apply { hint = "M/F"; layoutParams = LinearLayout.LayoutParams(0, -2, 0.8f); setText(prefs.getString("g$i", "")) }
            row.addView(name); row.addView(age); row.addView(gender)
            layout.addView(row)
            inputs.add(Triple(name, age, gender))
        }

        val saveBtn = Button(this).apply { text = "सेटिंग्स सेव करें ✅" }
        saveBtn.setOnClickListener {
            val editor = prefs.edit()
            editor.putString("target_time", timeInput.text.toString())
            editor.putBoolean("check_avl", avlSwitch.isChecked)
            for (i in 0 until 6) {
                editor.putString("n${i+1}", inputs[i].first.text.toString())
                editor.putString("a${i+1}", inputs[i].second.text.toString())
                editor.putString("g${i+1}", inputs[i].third.text.toString())
            }
            editor.apply()
            Toast.makeText(this, "डाटा सुरक्षित हो गया!", Toast.LENGTH_SHORT).show()
        }
        layout.addView(saveBtn)
        setContentView(scrollView)
    }
}
