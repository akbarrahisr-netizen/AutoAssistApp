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
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40) }
        scrollView.addView(layout)

        val title = TextView(this).apply { text = "6 पैसेंजर (नाम, उम्र, M/F) भरें:"; textSize = 18f; setTextColor(Color.BLUE) }
        layout.addView(title)

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

        val saveBtn = Button(this).apply { text = "डाटा सेव करें ✅" }
        saveBtn.setOnClickListener {
            val editor = prefs.edit()
            for (i in 0 until 6) {
                editor.putString("n${i+1}", inputs[i].first.text.toString())
                editor.putString("a${i+1}", inputs[i].second.text.toString())
                editor.putString("g${i+1}", inputs[i].third.text.toString())
            }
            editor.apply()
            Toast.makeText(this, "6 पैसेंजर का डाटा सेव हो गया!", Toast.LENGTH_SHORT).show()
        }
        layout.addView(saveBtn)
        setContentView(scrollView)
    }
}
