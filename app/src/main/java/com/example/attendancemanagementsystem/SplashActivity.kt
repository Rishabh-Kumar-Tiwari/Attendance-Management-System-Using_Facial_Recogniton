package com.example.attendancemanagementsystem

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : AppCompatActivity() {

    private companion object {
        const val SPLASH_DURATION_MS = 1200L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        lifecycleScope.launch {
            val modelWarmupJob = async(Dispatchers.IO) { warmupEmbeddingModel() }
            val splashDelayJob = async { delay(SPLASH_DURATION_MS) }

            modelWarmupJob.await()
            splashDelayJob.await()

            navigateToMainActivity()
        }
    }

    private fun warmupEmbeddingModel(): Boolean {
        return try {
            val embedder = try {
                TFLiteEmbedder.createFromAssets(this, "facenet.tflite")
            } catch (e: Exception) {
                Log.w("SplashActivity", "Model warmup skipped: ${e.message}")
                null
            }

            embedder?.close()
            true
        } catch (e: Exception) {
            Log.w("SplashActivity", "Model warmup failed: ${e.message}")
            false
        }
    }

    private suspend fun navigateToMainActivity() {
        withContext(Dispatchers.Main) {
            try {
                val intent = Intent(this@SplashActivity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Log.w("SplashActivity", "Failed to start MainActivity: ${e.message}")
            }
        }
    }
}
