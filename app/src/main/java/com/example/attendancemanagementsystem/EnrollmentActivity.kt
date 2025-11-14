package com.example.attendancemanagementsystem

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.attendancemanagementsystem.databinding.ActivityEnrollBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class EnrollmentActivity : BaseActivity() {

    private lateinit var binding: ActivityEnrollBinding
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val detectorHelper by lazy { FaceDetectorHelper(this) }
    private var cameraAnalyzer: CameraAnalyzer? = null
    private var faceEmbedder: TFLiteEmbedder? = null
    private val collectedEmbeddings = mutableListOf<FloatArray>()
    private var lastAlignedFace: Bitmap? = null

    private var classId: String = ""
    private var editingStudentId: String = ""
    private val minSamplesRequired = 3

    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    private var isFrontCamera: Boolean = true

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                showToast(getString(R.string.msg_camera_permission_required), Toast.LENGTH_LONG)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEnrollBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarEnroll)

        classId = intent.getStringExtra("classId") ?: ""
        editingStudentId = intent.getStringExtra("studentId") ?: ""

        loadEmbeddingModel()
        setupUI()
        checkCameraPermission()
    }

    private fun loadEmbeddingModel() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                faceEmbedder = TFLiteEmbedder.createFromAssets(this@EnrollmentActivity, "facenet.tflite")
                Log.i("EnrollmentActivity", "Face embedding model loaded successfully")
            } catch (e: Exception) {
                Log.w("EnrollmentActivity", "Failed to load embedding model: ${e.message}")
            }
        }
    }

    private fun setupUI() {
        if (editingStudentId.isNotBlank()) {
            StudentStorage.getStudent(this, editingStudentId)?.let { student ->
                binding.inputName.setText(student.name)
                binding.inputRoll.setText(student.roll)
                binding.txtInstruction.text = getString(R.string.msg_editing_student, student.name)
            }
        }

        binding.btnCapture.setOnClickListener { captureFaceEmbedding() }
        binding.btnSwitchCameraEnroll.setOnClickListener { toggleCamera() }
        binding.btnSubmit.setOnClickListener { submitEnrollment() }
    }

    private fun captureFaceEmbedding() {
        val faceBitmap = lastAlignedFace
        if (faceBitmap == null) {
            showToast(getString(R.string.msg_no_face_available), Toast.LENGTH_SHORT)
            return
        }

        val embedder = faceEmbedder
        if (embedder == null) {
            showToast(getString(R.string.msg_model_not_loaded), Toast.LENGTH_LONG)
            return
        }

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val embedding = embedder.getEmbedding(faceBitmap)
                collectedEmbeddings.add(embedding)
                runOnUiThread {
                    binding.txtInstruction.text = getString(R.string.msg_captured_samples, collectedEmbeddings.size)
                    showToast(getString(R.string.msg_captured_sample, collectedEmbeddings.size), Toast.LENGTH_SHORT)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showToast(getString(R.string.msg_embedding_failed, e.message ?: ""), Toast.LENGTH_LONG)
                }
            }
        }
    }

    private fun toggleCamera() {
        isFrontCamera = !isFrontCamera
        cameraSelector = if (isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                provider.unbindAll()
                startCamera()
            } catch (e: Exception) {
                Log.w("EnrollmentActivity", "Failed to toggle camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun submitEnrollment() {
        val name = binding.inputName.text.toString().trim()
        val roll = binding.inputRoll.text.toString().trim()

        if (name.isEmpty() || roll.isEmpty()) {
            showToast(getString(R.string.msg_enter_name_roll), Toast.LENGTH_SHORT)
            return
        }

        if (collectedEmbeddings.size < minSamplesRequired) {
            showToast(getString(R.string.msg_minimum_samples_required, minSamplesRequired), Toast.LENGTH_LONG)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val studentId = if (editingStudentId.isNotBlank()) {
                    editingStudentId
                } else {
                    "${roll}_${name.replace("\\s+".toRegex(), "_")}"
                }

                val student = Student(id = studentId, roll = roll, name = name, classId = classId)
                StudentStorage.createOrUpdate(applicationContext, student)

                if (collectedEmbeddings.isNotEmpty()) {
                    EmbeddingStorage.saveEnrollment(applicationContext, studentId, collectedEmbeddings)
                    RecognitionManager.enroll(studentId, collectedEmbeddings)
                }

                if (classId.isNotBlank()) {
                    ClassStorage.addStudentToClass(applicationContext, classId, studentId)
                    ensureMasterCsvRoster(studentId)
                }

                runOnUiThread {
                    showToast(getString(R.string.msg_saved_successfully, name, studentId), Toast.LENGTH_LONG)
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showToast(getString(R.string.msg_save_failed, e.message ?: ""), Toast.LENGTH_LONG)
                }
            }
        }
    }

    private fun ensureMasterCsvRoster(studentId: String) {
        try {
            AttendanceStorage.ensureMasterCsvHasRoster(applicationContext, classId)
        } catch (e: Exception) {
            Log.w("EnrollmentActivity", "Failed to update master CSV for $studentId: ${e.message}")
        }
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val previewView = findViewById<androidx.camera.view.PreviewView>(R.id.previewView)
        val overlayView = findViewById<OverlayView>(R.id.overlayView)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                provider.unbindAll()

                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                cameraAnalyzer?.stop()
                cameraAnalyzer = CameraAnalyzer(detectorHelper) { alignedFace, faceBox, bitmapWidth, bitmapHeight ->
                    lastAlignedFace = alignedFace
                    runOnUiThread {
                        overlayView.setBoundingBox(
                            faceBox,
                            bitmapWidth,
                            bitmapHeight,
                            previewView.width,
                            previewView.height,
                            isFrontCamera
                        )
                    }
                }

                imageAnalysis.setAnalyzer(cameraExecutor, cameraAnalyzer!!)
                provider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (e: Exception) {
                showToast(getString(R.string.msg_camera_start_failed, e.localizedMessage ?: ""), Toast.LENGTH_LONG)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showToast(message: String, duration: Int) {
        Toast.makeText(this, message, duration).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraAnalyzer?.stop()
        cameraExecutor.shutdown()
        faceEmbedder?.close()
    }
}
