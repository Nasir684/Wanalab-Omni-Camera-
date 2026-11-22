package com.wanalab.godeye

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FaceAlertHistoryActivity : AppCompatActivity() {

    private lateinit var listLayout: LinearLayout
    private lateinit var statusText: TextView
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setPadding(24, 24, 24, 24)
        }

        val title = TextView(this).apply {
            text = "Alert History - Unknown Faces"
            textSize = 20f
        }

        statusText = TextView(this).apply {
            text = "Loading..."
            setPadding(0, 12, 0, 12)
        }

        val backBtn = Button(this).apply {
            text = "Back to Camera"
            setOnClickListener { finish() }
        }

        val clearBtn = Button(this).apply {
            text = "Clear All Alerts"
            setOnClickListener { clearAll() }
        }

        listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this).apply { addView(listLayout) }

        root.addView(title)
        root.addView(statusText)
        root.addView(backBtn)
        root.addView(clearBtn)
        root.addView(scroll)
        setContentView(root)

        loadAlerts()
    }

    // FIX v2.2: Decode sampled thumbnail - 400px, not full 8MB - Prevents OOM on 3GB phones
    private fun decodeThumb(file: File): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            var sample = 1
            while (opts.outWidth / sample > 400 || opts.outHeight / sample > 400) sample *= 2
            val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, opts2)
        } catch (e: Exception) { null }
    }

    private fun loadAlerts() {
        scope.launch(Dispatchers.Default) {
            val files = FaceAlertManager.getAlertHistory(this@FaceAlertHistoryActivity)
            withContext(Dispatchers.Main) {
                listLayout.removeAllViews()
                statusText.text = if (files.isEmpty()) "No alerts yet - All faces known" else "${files.size} Unknown Alerts - Tap to View / Enroll"
                if (files.isEmpty()) {
                    val empty = TextView(this@FaceAlertHistoryActivity).apply {
                        text = "No unknown faces detected yet.\nWhen an unknown person appears, snapshot saves here automatically."
                        setPadding(0, 24, 0, 0)
                    }
                    listLayout.addView(empty)
                    return@withContext
                }

                for (file in files) {
                    val row = LinearLayout(this@FaceAlertHistoryActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(0, 12, 0, 12)
                    }

                    val thumb = ImageView(this@FaceAlertHistoryActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(-1, 400)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setImageBitmap(decodeThumb(file))
                    }

                    val timeStr = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.US).format(Date(file.lastModified()))
                    val timeTv = TextView(this@FaceAlertHistoryActivity).apply {
                        text = "Unknown - $timeStr\n${file.name}"
                        textSize = 14f
                        setPadding(0, 8, 0, 8)
                    }

                    val btnRow = LinearLayout(this@FaceAlertHistoryActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                    }

                    val viewBtn = Button(this@FaceAlertHistoryActivity).apply {
                        text = "View Full"
                        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                        setOnClickListener {
                            // FIX v2.2: Use thumb for preview, full only when needed
                            val bmp = decodeThumb(file)
                            FaceEnrollActivity.pendingBitmap = bmp
                            startActivity(Intent(this@FaceAlertHistoryActivity, FaceEnrollActivity::class.java))
                        }
                    }

                    val enrollBtn = Button(this@FaceAlertHistoryActivity).apply {
                        text = "Enroll This Person"
                        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                        setOnClickListener {
                            val bmp = decodeThumb(file)
                            FaceEnrollActivity.pendingBitmap = bmp
                            Toast.makeText(this@FaceAlertHistoryActivity, "Now enter name and Save", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this@FaceAlertHistoryActivity, FaceEnrollActivity::class.java))
                        }
                    }

                    val delBtn = Button(this@FaceAlertHistoryActivity).apply {
                        text = "Delete"
                        layoutParams = LinearLayout.LayoutParams(0, -2, 0.6f)
                        setOnClickListener {
                            file.delete()
                            Toast.makeText(this@FaceAlertHistoryActivity, "Deleted", Toast.LENGTH_SHORT).show()
                            loadAlerts()
                        }
                    }

                    btnRow.addView(viewBtn)
                    btnRow.addView(enrollBtn)
                    btnRow.addView(delBtn)

                    val divider = View(this@FaceAlertHistoryActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(-1, 2)
                        setBackgroundColor(android.graphics.Color.LTGRAY)
                    }

                    row.addView(thumb)
                    row.addView(timeTv)
                    row.addView(btnRow)
                    row.addView(divider)
                    listLayout.addView(row)
                }
            }
        }
    }

    private fun clearAll() {
        scope.launch(Dispatchers.Default) {
            val files = FaceAlertManager.getAlertHistory(this@FaceAlertHistoryActivity)
            for (f in files) f.delete()
            withContext(Dispatchers.Main) {
                Toast.makeText(this@FaceAlertHistoryActivity, "All alerts cleared", Toast.LENGTH_SHORT).show()
                loadAlerts()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadAlerts()
    }

    override fun onDestroy() {
        super.onDestroy()
        // FIX v2.2: Cancel scope - No leak on rotate
        try { scope.cancel() } catch (e: Exception) {}
    }
}
