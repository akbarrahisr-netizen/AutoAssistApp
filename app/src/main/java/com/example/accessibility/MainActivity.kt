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
        val prefs = getSharedPreferences("AutoData", Context.MODE_PRIVATE)
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 30, 30, 30) }
        scrollView.addView(layout)

        layout.addView(TextView(this).apply { text = "ऑटो रिफ्रेश टाइम (HH:mm:ss):"; setTextColor(Color.RED) })
        val timeIn = EditText(this).apply { hint = "11:59:59"; setText(prefs.getString("t_time", "11:59:59")) }
        layout.addView(timeIn)

        layout.addView(TextView(this).apply { text = "\nट्रेन नंबर:"; setTextColor(Color.BLUE) })
        val trainIn = EditText(this).apply { hint = "12488"; inputType = 2; setText(prefs.getString("t_num", "")) }
        layout.addView(trainIn)

        layout.addView(TextView(this).apply { text = "\nक्लिक डिले (ms):"; setTextColor(Color.BLACK) })
        val delayIn = EditText(this).apply { hint = "0"; inputType = 2; setText(prefs.getString("c_delay", "200")) }
        layout.addView(delayIn)

        // रेडियो ग्रुप फिक्स
        layout.addView(TextView(this).apply { text = "\nक्लास चुनें:" })
        val radioGroup = RadioGroup(this)
        val classes = listOf("SL", "3A", "2A", "3E")
        val savedCls = prefs.getString("sel_cls", "SL")
        
        classes.forEachIndexed { index, cls ->
            val rb = RadioButton(this).apply { 
                text = cls
                id = index + 100 // अलग ID देना ज़रूरी है
                if (cls == savedCls) isChecked = true 
            }
            radioGroup.addView(rb)
        }
        layout.addView(radioGroup)

        val inputs = mutableListOf<Triple<EditText, EditText, EditText>>()
        for (i in 1..6) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val n = EditText(this).apply { hint = "नाम $i"; layoutParams = LinearLayout.LayoutParams(0, -2, 2f); setText(prefs.getString("n$i", "")) }
            val a = EditText(this).apply { hint = "उम्र"; layoutParams = LinearLayout.LayoutParams(0, -2, 1f); inputType = 2; setText(prefs.getString("a$i", "")) }
            val g = EditText(this).apply { hint = "M/F"; layoutParams = LinearLayout.LayoutParams(0, -2, 0.8f); setText(prefs.getString("g$i", "")) }
            row.addView(n); row.addView(a); row.addView(g); layout.addView(row)
            inputs.add(Triple(n, a, g))
        }

        val saveBtn = Button(this).apply { text = "डाटा सेव करें ✅"; setBackgroundColor(Color.GREEN) }
        saveBtn.setOnClickListener {
            val ed = prefs.edit()
            ed.putString("t_time", timeIn.text.toString())
            ed.putString("t_num", trainIn.text.toString())
            ed.putString("c_delay", delayIn.text.toString())
            val checkedId = radioGroup.checkedRadioButtonId
            if (checkedId != -1) {
                val rb = findViewById<RadioButton>(checkedId)
                ed.putString("sel_cls", rb.text.toString())
            }
            for (i in 0..5) {
                ed.putString("n${i+1}", inputs[i].first.text.toString())
                ed.putString("a${i+1}", inputs[i].second.text.toString())
                ed.putString("g${i+1}", inputs[i].third.text.toString())
            }
            ed.apply()
            Toast.makeText(this, "डाटा सेव हो गया!", Toast.LENGTH_SHORT).show()
        }
        layout.addView(saveBtn)
        setContentView(scrollView)
    }
}
