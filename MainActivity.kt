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
        
        // मुख्य लेआउट
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 40, 40, 40)
        scrollView.addView(layout)

        val title = TextView(this)
        title.text = "अकबर भाई, पैसेंजर लिस्ट यहाँ भरें:"
        title.textSize = 22f
        title.setTextColor(Color.BLACK)
        layout.addView(title)

        val nameInputs = mutableListOf<EditText>()
        val ageInputs = mutableListOf<EditText>()

        // 4 पैसेंजर के लिए खाली डिब्बे बनाना
        for (i in 1..4) {
            val label = TextView(this)
            label.text = "\nपैसेंजर $i:"
            layout.addView(label)

            val nameInput = EditText(this)
            nameInput.hint = "नाम $i"
            nameInput.setText(prefs.getString("name_$i", ""))
            layout.addView(nameInput)
            nameInputs.add(nameInput)

            val ageInput = EditText(this)
            ageInput.hint = "उम्र $i"
            ageInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER
            ageInput.setText(prefs.getString("age_$i", ""))
            layout.addView(ageInput)
            ageInputs.add(ageInput)
        }

        // सेव बटन
        val saveBtn = Button(this)
        saveBtn.text = "डाटा सेव करें ✅"
        saveBtn.setOnClickListener {
            val editor = prefs.edit()
            for (i in 0 until 4) {
                editor.putString("name_${i + 1}", nameInputs[i].text.toString())
                editor.putString("age_${i + 1}", ageInputs[i].text.toString())
            }
            editor.apply()
            Toast.makeText(this, "अकबर भाई, डाटा सेव हो गया!", Toast.LENGTH_SHORT).show()
        }
        layout.addView(saveBtn)

        setContentView(scrollView)
    }
}

