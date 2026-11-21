package com.godeye.app.face

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class CameraSettingsActivity : AppCompatActivity() {

    companion object {
        const val PREFS = "godeye_cameras"
        const val KEY_1 = "camera_1_url"
        const val KEY_2 = "camera_2_url"
        const val KEY_3 = "camera_3_url"

        fun getSavedUrls(context: Context): List<String> {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return listOf(
                prefs.getString(KEY_1, "")?.trim() ?: "",
                prefs.getString(KEY_2, "")?.trim() ?: "",
                prefs.getString(KEY_3, "")?.trim() ?: ""
            )
        }
    }

    private lateinit var input1: EditText
    private lateinit var input2: EditText
    private lateinit var input3: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(32, 32, 32, 32)
        }

        input1 = EditText(this).apply {
            hint = "Camera 1 URL - Home / Office"
            isSingleLine = true
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        input2 = EditText(this).apply {
            hint = "Camera 2 URL - Home / Office"
            isSingleLine = true
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        input3 = EditText(this).apply {
            hint = "Camera 3 URL - Home / Office"
            isSingleLine = true
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }

        val saved = getSavedUrls(this)
        input1.setText(saved[0])
        input2.setText(saved[1])
        input3.setText(saved[2])

        val saveBtn = Button(this).apply {
            text = "Save Cameras"
            setOnClickListener { save() }
        }

        val clearBtn = Button(this).apply {
            text = "Clear All"
            setOnClickListener { clear() }
        }

        root.addView(input1)
        root.addView(input2)
        root.addView(input3)
        root.addView(saveBtn)
        root.addView(clearBtn)
        setContentView(root)
    }

    private fun save() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_1, input1.text.toString().trim())
            .putString(KEY_2, input2.text.toString().trim())
            .putString(KEY_3, input3.text.toString().trim())
            .apply()
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun clear() {
        input1.setText("")
        input2.setText("")
        input3.setText("")
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show()
    }
}
