package com.example.accessibility

import android.app.Activity
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.graphics.Color

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Overlay Permission
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }

        val prefs = getSharedPreferences("AutoData", Context.MODE_PRIVATE)

        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }
        scrollView.addView(layout)

        // TITLE
        layout.addView(TextView(this).apply {
            text = "AutoAssist Pro 🚉"
            textSize = 22f
            setTextColor(Color.parseColor("#1A237E"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
        })

        // TIME
        layout.addView(TextView(this).apply { text = "Time (HH:mm:ss)" })
        val timeIn = EditText(this).apply {
            hint = "11:00:00"
            inputType = InputType.TYPE_CLASS_DATETIME
            setText(prefs.getString("t_time", "11:00:00"))
        }
        layout.addView(timeIn)

        // TRAIN
        layout.addView(TextView(this).apply { text = "Train Number" })
        val trainIn = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getString("t_num", "12488"))
        }
        layout.addView(trainIn)

        // CLASS (3E ADDED 🔥)
        layout.addView(TextView(this).apply { text = "Select Class" })
        val classGroup = RadioGroup(this)
        val classes = listOf("SL", "3A", "2A", "3E") // 🔴 FIX
        val savedCls = prefs.getString("sel_cls", "SL")

        classes.forEachIndexed { i, cls ->
            val rb = RadioButton(this).apply {
                text = cls
                id = i + 100
                if (cls == savedCls) isChecked = true
            }
            classGroup.addView(rb)
        }
        layout.addView(classGroup)

        // PASSENGERS
        val inputs = mutableListOf<Triple<EditText, EditText, EditText>>()

        for (i in 1..6) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 10, 0, 10)
            }

            val name = EditText(this).apply {
                hint = "Name"
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f)
                setText(prefs.getString("n$i", ""))
            }

            val age = EditText(this).apply {
                hint = "Age"
                inputType = InputType.TYPE_CLASS_NUMBER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setText(prefs.getString("a$i", ""))
            }

            val gender = EditText(this).apply {
                hint = "M/F"
                isAllCaps = true
                filters = arrayOf(InputFilter.LengthFilter(1))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setText(prefs.getString("g$i", "M"))
            }

            row.addView(name)
            row.addView(age)
            row.addView(gender)
            layout.addView(row)

            inputs.add(Triple(name, age, gender))
        }

        // SAVE BUTTON
        val saveBtn = Button(this).apply {
            text = "SAVE ✅"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 20, 0, 10) } // 🔴 Margin Fix
        }

        saveBtn.setOnClickListener {

            // 🔴 Keyboard Hide FIX
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)

            val ed = prefs.edit()
            ed.putString("t_time", timeIn.text.toString())
            ed.putString("t_num", trainIn.text.toString())

            val rb = findViewById<RadioButton>(classGroup.checkedRadioButtonId)
            if (rb != null) ed.putString("sel_cls", rb.text.toString())

            for (i in inputs.indices) {
                ed.putString("n${i+1}", inputs[i].first.text.toString())
                ed.putString("a${i+1}", inputs[i].second.text.toString())
                ed.putString("g${i+1}", inputs[i].third.text.toString().uppercase())
            }

            ed.apply()
            Toast.makeText(this, "Saved Successfully!", Toast.LENGTH_SHORT).show()
        }

        layout.addView(saveBtn)

        // OPEN IRCTC
        val openBtn = Button(this).apply {
            text = "Open IRCTC 🚉"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 10, 0, 10) }
        }

        openBtn.setOnClickListener {
            val intent = packageManager.getLaunchIntentForPackage("cris.org.in.prs.ima")
            if (intent != null) startActivity(intent)
            else Toast.makeText(this, "IRCTC not installed", Toast.LENGTH_SHORT).show()
        }

        layout.addView(openBtn)

        // ENABLE SERVICE
        val serviceBtn = Button(this).apply {
            text = "Enable Service ⚙️"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 10, 0, 20) }
        }

        serviceBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        layout.addView(serviceBtn)

        setContentView(scrollView)
    }
}
