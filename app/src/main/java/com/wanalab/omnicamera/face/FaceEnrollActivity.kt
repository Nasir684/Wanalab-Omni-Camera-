package com.godeye.app.face

import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FaceEnrollActivity : AppCompatActivity() {

    private lateinit var faceEngine: FaceEngine
    private lateinit var nameInput: EditText
    private lateinit var listLayout: LinearLayout
    private lateinit var statusText: TextView
    private val scope = CoroutineScope(Dispatchers.Main)

    // Bitmap passed from CameraActivity via static holder - avoids Intent size limit
    companion object {
        var pendingBitmap: Bitmap? = null
        var pendingFaceBox: android.graphics.RectF? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "Enroll Face - Offline"
            textSize = 20f
        }

        statusText = TextView(this).apply {
            text = if (pendingBitmap != null) "Face captured, enter name" else "No face, go back to camera and tap a face"
            textSize = 14f
            setPadding(0, 16, 0, 16)
        }

        nameInput = EditText(this).apply {
            hint = "Enter Name - e.g. Nasir, Ali, Guard"
            isSingleLine = true
        }

        val saveBtn = Button(this).apply {
            text = "Save Face - Offline"
            setOnClickListener { saveFace() }
        }

        val backBtn = Button(this).apply {
            text = "Back to Camera"
            setOnClickListener { finish() }
        }

        val listTitle = TextView(this).apply {
            text = "Known Faces - Tap Delete to Remove"
            textSize = 16f
            setPadding(0, 32, 0, 16)
        }

        listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this).apply {
            addView(listLayout)
        }

        root.addView(title)
        root.addView(statusText)
        root.addView(nameInput)
        root.addView(saveBtn)
        root.addView(backBtn)
        root.addView(listTitle)
        root.addView(scroll)
        setContentView(root)

        faceEngine = FaceEngine(this)
        scope.launch(Dispatchers.Default) {
            try { faceEngine.init() } catch (e: Exception) {}
            withContext(Dispatchers.Main) { loadKnownFaces() }
        }
    }

    private fun saveFace() {
        val name = nameInput.text.toString().trim()
        val bitmap = pendingBitmap
        if (name.isBlank()) {
            Toast.makeText(this, "Enter name first", Toast.LENGTH_SHORT).show()
            return
        }
        if (bitmap == null) {
            Toast.makeText(this, "No face captured, tap face on camera", Toast.LENGTH_SHORT).show()
            return
        }
        statusText.text = "Saving $name..."
        scope.launch(Dispatchers.Default) {
            val personId = "person_${System.currentTimeMillis()}"
            val ok = try {
                faceEngine.enrollPerson(name, personId, bitmap)
            } catch (e: Exception) { false }
            withContext(Dispatchers.Main) {
                if (ok) {
                    Toast.makeText(this@FaceEnrollActivity, "Saved $name - Offline", Toast.LENGTH_SHORT).show()
                    statusText.text = "Saved $name"
                    nameInput.setText("")
                    pendingBitmap = null
                    loadKnownFaces()
                    // Online only - auto sync if internet
                    if (NetworkAutoManager.isOnline) {
                        Toast.makeText(this@FaceEnrollActivity, "Online - Face will auto-sync to cloud", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@FaceEnrollActivity, "No face found, try again", Toast.LENGTH_SHORT).show()
                    statusText.text = "Failed - No face in image"
                }
            }
        }
    }

    private fun loadKnownFaces() {
        scope.launch(Dispatchers.Default) {
            val db = FaceDatabase.get(this@FaceEnrollActivity)
            val list = try { db.dao().getAll() } catch (e: Exception) { emptyList() }
            withContext(Dispatchers.Main) {
                listLayout.removeAllViews()
                if (list.isEmpty()) {
                    val empty = TextView(this@FaceEnrollActivity).apply { text = "No known faces yet" }
                    listLayout.addView(empty)
                    return@withContext
                }
                for (person in list) {
                    val row = LinearLayout(this@FaceEnrollActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, 8, 0, 8)
                    }
                    val nameTv = TextView(this@FaceEnrollActivity).apply {
                        text = "${person.name} - ${person.personId}"
                        textSize = 15f
                        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    }
                    val delBtn = Button(this@FaceEnrollActivity).apply {
                        text = "Delete"
                        setOnClickListener {
                            scope.launch(Dispatchers.Default) {
                                db.dao().delete(person.personId)
                                withContext(Dispatchers.Main) { loadKnownFaces() }
                            }
                            Toast.makeText(this@FaceEnrollActivity, "Deleted ${person.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    row.addView(nameTv)
                    row.addView(delBtn)
                    listLayout.addView(row)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { faceEngine.close() } catch (e: Exception) {}
    }
}
