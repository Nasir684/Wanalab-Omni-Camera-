package com.wanalab.godeye

import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FaceLogActivity : AppCompatActivity() {

    private lateinit var listLayout: LinearLayout
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        val title = TextView(this).apply {
            text = "Face Log - Who Came When"
            textSize = 20f
        }

        val clearBtn = Button(this).apply {
            text = "Clear Log"
            setOnClickListener {
                FaceLogManager.clearLogs(this@FaceLogActivity)
                load()
            }
        }

        val backBtn = Button(this).apply {
            text = "Back"
            setOnClickListener { finish() }
        }

        listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this).apply {
            addView(listLayout)
        }

        root.addView(title)
        root.addView(clearBtn)
        root.addView(backBtn)
        root.addView(scroll)
        setContentView(root)
        load()
    }

    private fun load() {
        scope.launch(Dispatchers.Default) {
            val logs = FaceLogManager.getLogs(this@FaceLogActivity)
            withContext(Dispatchers.Main) {
                listLayout.removeAllViews()
                if (logs.isEmpty()) {
                    listLayout.addView(TextView(this@FaceLogActivity).apply {
                        text = "No logs yet"
                    })
                } else {
                    for (l in logs) {
                        listLayout.addView(TextView(this@FaceLogActivity).apply {
                            text = l
                            setPadding(0, 8, 0, 8)
                            textSize = 14f
                        })
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { scope.cancel() } catch (e: Exception) {}
    }
}
