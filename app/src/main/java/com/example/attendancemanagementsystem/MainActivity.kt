package com.example.attendancemanagementsystem

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.attendancemanagementsystem.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val inferenceExecutor = Executors.newSingleThreadExecutor()
    private val inferenceDispatcher = inferenceExecutor.asCoroutineDispatcher()

    private var cameraAnalyzer: CameraAnalyzer? = null
    private var faceEmbedder: TFLiteEmbedder? = null
    private val detectorHelper by lazy { FaceDetectorHelper(this) }

    private var autoMarkEnabled = true
    private val consecutiveRecognitionsNeeded = 3
    private val recognitionCounters = mutableMapOf<String, Int>()
    private val recentlyMarkedTimestamps = mutableMapOf<String, Long>()
    private val recognitionCooldownMs = 10_000L

    private var selectedClassId: String = ""
    private var selectedClassName: String = ""

    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    private var isFrontCamera: Boolean = true

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else showToast(getString(R.string.msg_camera_permission_required))
        }

    private val selectClassLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                selectedClassId = result.data!!.getStringExtra("classId") ?: ""
                selectedClassName = result.data!!.getStringExtra("className") ?: ""
                if (selectedClassId.isNotBlank()) {
                    showToast(getString(R.string.msg_selected_class, selectedClassName))
                    loadClassEmbeddings()
                    if (hasCameraPermission()) startCamera() else requestCameraPermission()
                } else {
                    showToast(getString(R.string.msg_no_class_selected))
                }
            } else if (selectedClassId.isBlank()) {
                showToast(getString(R.string.msg_please_select_class))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topAppBar)

        initializeUI()
        loadEmbeddingModel()
        promptClassSelection()
    }

    private fun initializeUI() = with(binding) {
        txtStatus.text = getString(R.string.status_idle)
        txtRecognizedName.text = getString(R.string.no_one_detected)
        txtRecognizedId.text = ""
        btnMarkPresent.isEnabled = false
        txtMode.text = getString(R.string.mode_auto_mark)
        switchAutoMark.isChecked = true

        btnStart.setOnClickListener {
            if (selectedClassId.isNotBlank()) {
                if (hasCameraPermission()) startCamera() else requestCameraPermission()
            } else {
                promptClassSelection()
            }
        }

        btnStop.setOnClickListener { stopCamera() }

        btnEnroll.setOnClickListener {
            navigateToEnrollment()
        }

        fabAttendance.setOnClickListener {
            navigateToAttendance()
        }

        btnSwitchCamera.setOnClickListener {
            toggleCamera()
        }

        switchAutoMark.setOnCheckedChangeListener { _, checked ->
            autoMarkEnabled = checked
            txtMode.text = if (checked) getString(R.string.mode_auto_mark) else getString(R.string.mode_manual_mark)
            recognitionCounters.clear()
            btnMarkPresent.isEnabled = !checked && txtRecognizedId.tag != null
        }

        btnMarkPresent.setOnClickListener {
            val studentId = txtRecognizedId.tag as? String
            if (studentId != null) {
                markStudentManually(studentId)
            } else {
                showToast(getString(R.string.msg_no_recognized_student))
            }
        }

        refreshAttendanceCount()
    }

    private fun navigateToEnrollment() {
        val intent = Intent(this, EnrollmentActivity::class.java).apply {
            if (selectedClassId.isNotBlank()) putExtra("classId", selectedClassId)
        }
        startActivity(intent)
    }

    private fun navigateToAttendance() {
        val intent = Intent(this, AttendanceActivity::class.java).apply {
            if (selectedClassId.isNotBlank()) putExtra("classId", selectedClassId)
        }
        startActivity(intent)
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
                Log.w("MainActivity", "Failed to toggle camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun promptClassSelection() {
        selectClassLauncher.launch(Intent(this, ClassSelectionActivity::class.java))
    }

    private fun loadEmbeddingModel() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                faceEmbedder = TFLiteEmbedder.createFromAssets(this@MainActivity, "facenet.tflite")
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed to load embedding model: ${e.message}")
            }
        }
    }

    private fun loadClassEmbeddings() {
        if (selectedClassId.isBlank()) {
            RecognitionManager.clear()
            return
        }

        val studentIds = ClassStorage.getClass(this, selectedClassId)?.studentIds ?: emptyList()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                EmbeddingStorage.loadIntoRecognitionManager(this@MainActivity, studentIds)
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed to load class embeddings: ${e.message}")
            }
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        if (selectedClassId.isBlank()) {
            showToast(getString(R.string.msg_select_class_first))
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                provider.unbindAll()

                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(binding.previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                cameraAnalyzer?.stop()
                cameraAnalyzer = CameraAnalyzer(detectorHelper) { faceBitmap, faceBox, bitmapWidth, bitmapHeight ->
                    runOnUiThread {
                        binding.overlayView.setBoundingBox(
                            faceBox,
                            bitmapWidth,
                            bitmapHeight,
                            binding.previewView.width,
                            binding.previewView.height,
                            isFrontCamera
                        )
                    }

                    val embedder = faceEmbedder ?: return@CameraAnalyzer
                    lifecycleScope.launch(inferenceDispatcher) {
                        try {
                            val embedding = embedder.getEmbedding(faceBitmap)
                            val match = RecognitionManager.recognize(embedding)
                            if (match != null) {
                                handleRecognitionMatch(match.id, match.confidence)
                            } else {
                                runOnUiThread { updateRecognizedDisplay(null, 0f) }
                            }
                        } catch (e: Exception) {
                            Log.w("MainActivity", "Recognition error: ${e.message}")
                            runOnUiThread {
                                binding.txtStatus.text = getString(R.string.status_recognition_error)
                            }
                        }
                    }
                }

                imageAnalysis.setAnalyzer(cameraExecutor, cameraAnalyzer!!)
                provider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)

                val cameraType = if (isFrontCamera) getString(R.string.camera_front) else getString(R.string.camera_back)
                runOnUiThread {
                    binding.txtStatus.text = getString(R.string.status_camera_started_class, cameraType, selectedClassName)
                }

            } catch (e: Exception) {
                showToast(getString(R.string.msg_camera_start_failed, e.localizedMessage ?: ""))
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleRecognitionMatch(studentId: String, confidence: Float) {
        val currentTime = System.currentTimeMillis()
        val lastMarkedTime = recentlyMarkedTimestamps[studentId] ?: 0L

        if (autoMarkEnabled) {
            if (currentTime - lastMarkedTime < recognitionCooldownMs) {
                runOnUiThread {
                    binding.txtStatus.text = getString(R.string.msg_recently_marked, formatStudentDisplay(studentId))
                }
                return
            }

            val recognitionCount = (recognitionCounters[studentId] ?: 0) + 1
            recognitionCounters[studentId] = recognitionCount
            runOnUiThread { updateRecognizedDisplay(studentId, confidence, recognitionCount) }

            if (recognitionCount >= consecutiveRecognitionsNeeded) {
                recognitionCounters[studentId] = 0
                recentlyMarkedTimestamps[studentId] = currentTime

                lifecycleScope.launch(Dispatchers.IO) {
                    val (roll, name) = parseStudentId(studentId)
                    val marked = ClassAttendanceManager.markIfNotMarked(
                        this@MainActivity, selectedClassId, studentId, name, roll
                    )
                    runOnUiThread {
                        if (marked) {
                            showToast(getString(R.string.msg_marked_present, name))
                            binding.txtStatus.text = getString(R.string.msg_auto_marked, name)
                            refreshAttendanceCount()
                        } else {
                            binding.txtStatus.text = getString(R.string.msg_already_marked, name)
                        }
                    }
                }
            }
        } else {
            runOnUiThread { updateRecognizedDisplay(studentId, confidence) }
        }
    }

    private fun updateRecognizedDisplay(studentId: String?, confidence: Float, recognitionCount: Int = 0) {
        if (studentId == null) {
            binding.txtRecognizedName.text = getString(R.string.no_one_detected)
            binding.txtRecognizedId.text = ""
            binding.txtRecognizedId.tag = null
            binding.btnMarkPresent.isEnabled = false
            binding.txtStatus.text = getString(R.string.status_unknown)
            return
        }

        val (roll, name) = parseStudentId(studentId)
        binding.txtRecognizedName.text = name
        binding.txtRecognizedId.text = getString(R.string.format_roll_conf, roll, "%.2f".format(confidence))
        binding.txtRecognizedId.tag = studentId
        binding.btnMarkPresent.isEnabled = !autoMarkEnabled

        binding.txtStatus.text = if (recognitionCount > 0) {
            getString(R.string.status_recognizing, name, recognitionCount, consecutiveRecognitionsNeeded)
        } else {
            getString(R.string.status_detected, name)
        }
    }

    private fun markStudentManually(studentId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val (roll, name) = parseStudentId(studentId)
            val marked = ClassAttendanceManager.markIfNotMarked(
                this@MainActivity, selectedClassId, studentId, name, roll
            )
            runOnUiThread {
                val message = if (marked) getString(R.string.msg_marked_present, name) else getString(R.string.msg_already_marked, name)
                showToast(message)
                binding.txtStatus.text = message
                if (marked) refreshAttendanceCount()
            }
        }
    }

    private fun parseStudentId(studentId: String): Pair<String, String> {
        val separatorIndex = studentId.indexOf('_')
        return if (separatorIndex <= 0) {
            "" to studentId
        } else {
            studentId.substring(0, separatorIndex) to studentId.substring(separatorIndex + 1).replace("_", " ")
        }
    }

    private fun formatStudentDisplay(studentId: String): String {
        val (roll, name) = parseStudentId(studentId)
        return if (roll.isBlank()) name else getString(R.string.format_name_roll, name, roll)
    }

    private fun stopCamera() {
        try {
            ProcessCameraProvider.getInstance(this).get().unbindAll()
            cameraAnalyzer?.stop()
            binding.overlayView.clear()
            binding.txtStatus.text = getString(R.string.status_camera_stopped)
        } catch (e: Exception) {
            showToast(getString(R.string.msg_stop_camera_error, e.localizedMessage ?: ""))
        }
    }

    private fun refreshAttendanceCount() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val records = ClassAttendanceManager.getRecordsForDate(
                    this@MainActivity, java.util.Date(), selectedClassId
                )
                runOnUiThread {
                    binding.txtAttendanceCount.text = getString(R.string.today_marked, records.size)
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed to refresh attendance count: ${e.message}")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAttendanceCount()
        loadClassEmbeddings()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        runCatching { cameraAnalyzer?.stop() }
        runCatching { faceEmbedder?.close() }
        runCatching { inferenceExecutor.shutdownNow() }
    }

    @SuppressLint("MissingSuperCall")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        selectClassLauncher.launch(Intent(this, ClassSelectionActivity::class.java))
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}
