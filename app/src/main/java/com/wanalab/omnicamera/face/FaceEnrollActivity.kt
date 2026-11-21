package com.godeye.app.face

import android.graphics.Bitmap
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

class FaceEnrollActivity : AppCompatActivity() {

    private lateinit var faceEngine: FaceEngine
    private lateinit var nameInput: EditText
    private lateinit var listLayout: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var countText: TextView
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 3-photo enroll: Front, Left, Right
    private val capturedPhotos = mutableListOf<Bitmap>()

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
            text = "Enroll Face - Offline - 3 Photos"
            textSize = 20f
        }

        statusText = TextView(this).apply {
            text = if (pendingBitmap != null) "Face captured, enter name" else "No face, go back to camera and tap a face"
            textSize = 14f
            setPadding(0, 16, 0, 16)
        }

        // NEW: Show 0/3, 1/3, 2/3
        countText = TextView(this).apply {
            text = "Photos: 0 / 3 - Need Front + Left + Right"
            textSize = 14f
        }

        nameInput = EditText(this).apply {
            hint = "Enter Name - e.g. Nasir, Ali, Guard"
            isSingleLine = true
        }

        // NEW: Add current photo to list, don't save yet
        val addPhotoBtn = Button(this).apply {
            text = "Add This Photo (1/3)"
            setOnClickListener { addPhoto() }
        }

        val saveBtn = Button(this).apply {
            text = "Save Face - Offline (Needs 3 Photos)"
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
        root.addView(countText)
        root.addView(nameInput)
        root.addView(addPhotoBtn)
        root.addView(saveBtn)
        root.addView(backBtn)
        root.addView(listTitle)
        root.addView(scroll)
        setContentView(root)

        // If coming from Camera, add first photo automatically
        pendingBitmap?.let { capturedPhotos.add(it) ; updateCount() }

        faceEngine = FaceEngine(this)
        scope.launch(Dispatchers.Default) {
            try { faceEngine.init() ; faceEngine.refreshCache() } catch (e: Exception) {}
            withContext(Dispatchers.Main) { loadKnownFaces() }
        }
    }

    private fun updateCount() {
        countText.text = "Photos: ${capturedPhotos.size} / 3 - Need Front + Left + Right"
        statusText.text = when (capturedPhotos.size) {
            0 -> "No photo - Go to camera, tap face"
            1 -> "1/3 - Now turn LEFT, go to camera, tap face again"
            2 -> "2/3 - Now turn RIGHT, go to camera, tap face again"
            else -> "3/3 - Ready to Save!"
        }
    }

    private fun addPhoto() {
        val bmp = pendingBitmap
        if (bmp == null) {
            Toast.makeText(this, "No new photo - Go to camera, tap face", Toast.LENGTH_SHORT).show()
            return
        }
        if (capturedPhotos.size >= 3) {
            Toast.makeText(this, "Already have 3 photos, press Save", Toast.LENGTH_SHORT).show()
            return
        }
        capturedPhotos.add(bmp.copy(Bitmap.Config.ARGB_8888, false))
        pendingBitmap = null
        updateCount()
        Toast.makeText(this, "Photo ${capturedPhotos.size}/3 added", Toast.LENGTH_SHORT).show()
    }

    private fun saveFace() {
        val name = nameInput.text.toString().trim()
        if (name.isBlank()) {
            Toast.makeText(this, "Enter name first", Toast.LENGTH_SHORT).show()
            return
        }
        if (capturedPhotos.size < 3) {
            Toast.makeText(this, "Need 3 photos - Have ${capturedPhotos.size}/3. Go to camera, tap face again", Toast.LENGTH_LONG).show()
            return
        }
        statusText.text = "Saving $name..."
        scope.launch(Dispatchers.Default) {
            val personId = "person_${System.currentTimeMillis()}"
            // FIX: Use new enrollPerson(name, id, List<Bitmap>) - averages 3 embeddings
            val ok = try {
                faceEngine.enrollPerson(name, personId, capturedPhotos)
            } catch (e: Exception) { false }
            withContext(Dispatchers.Main) {
                if (ok) {
                    Toast.makeText(this@FaceEnrollActivity, "Saved $name - Offline", Toast.LENGTH_SHORT).show()
                    statusText.text = "Saved $name"
                    nameInput.setText("")
                    // FIX: Clear all bitmaps, no leak
                    for (b in capturedPhotos) { try { b.recycle() } catch (e: Exception) {} }
                    capturedPhotos.clear()
                    pendingBitmap = null
                    updateCount()
                    loadKnownFaces()
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
                                faceEngine.refreshCache() // FIX: Refresh cache after delete
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
        // FIX: Clear static + list, no leak
        try { pendingBitmap?.recycle() } catch (e: Exception) {}
        pendingBitmap = null
        for (b in capturedPhotos) { try { b.recycle() } catch (e: Exception) {} }
        capturedPhotos.clear()
        try { scope.cancel() } catch (e: Exception) {}
    }
}
