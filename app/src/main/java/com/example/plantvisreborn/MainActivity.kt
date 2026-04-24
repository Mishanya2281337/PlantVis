package com.example.plantvisreborn

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var imageView:          ImageView
    private lateinit var btnCamera:          Button
    private lateinit var btnGallery:         Button
    private lateinit var btnDiagnoseDisease: Button
    private lateinit var btnPlantGuide:      Button
    private lateinit var progressBar:        ProgressBar
    private lateinit var tvStatus:           TextView
    private lateinit var resultsContainer:   LinearLayout
    private lateinit var tvPlantName:        TextView
    private lateinit var tvPlantSci:         TextView   // новое — научное название
    private lateinit var tvConfidence:       TextView
    private lateinit var tvLowConfidence:    TextView   // предупреждение при <70%
    private lateinit var tvOtherResults:     TextView

    private val classifier = PlantClassifier(this)
    private var currentBitmap: Bitmap? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) openCamera() else showToast("Нет разрешения на камеру")
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap -> bitmap?.let { processImage(it) } }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            @Suppress("DEPRECATION")
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, it)
            processImage(bitmap)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView          = findViewById(R.id.imageView)
        btnCamera          = findViewById(R.id.btnCamera)
        btnGallery         = findViewById(R.id.btnGallery)
        btnDiagnoseDisease = findViewById(R.id.btnDiagnoseDisease)
        btnPlantGuide = findViewById(R.id.btnPlantGuide)
        btnPlantGuide.setOnClickListener {
            PlantGuideActivity.start(this)
        }
        progressBar        = findViewById(R.id.progressBar)
        tvStatus           = findViewById(R.id.tvStatus)
        resultsContainer   = findViewById(R.id.resultsContainer)
        tvPlantName        = findViewById(R.id.tvPlantName)
        tvPlantSci         = findViewById(R.id.tvPlantSci)
        tvConfidence       = findViewById(R.id.tvConfidence)
        tvLowConfidence    = findViewById(R.id.tvLowConfidence)
        tvOtherResults     = findViewById(R.id.tvOtherResults)


        btnCamera.setOnClickListener { checkCameraPermissionAndOpen() }
        btnGallery.setOnClickListener { galleryLauncher.launch("image/*") }
        btnDiagnoseDisease.setOnClickListener {
            val intent = Intent(this, DiseaseInfoActivity::class.java)
            currentBitmap?.let { bmp -> intent.putExtra("bitmap", bmp) }
            startActivity(intent)
        }

        // Загружаем модель в фоне
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                classifier.setup()
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Модель готова. Сфотографируйте растение!"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "⚠️ Ошибка загрузки модели: ${e.message}"
                }
            }
        }
    }

    private fun checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openCamera() = cameraLauncher.launch(null)

    private fun processImage(bitmap: Bitmap) {
        currentBitmap = bitmap
        imageView.setImageBitmap(bitmap)
        resultsContainer.visibility = View.GONE
        progressBar.visibility      = View.VISIBLE
        tvStatus.text = "Определяем растение..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val results = classifier.classify(bitmap)
                withContext(Dispatchers.Main) { showResults(results) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    tvStatus.text = "Ошибка: ${e.message}"
                }
            }
        }
    }

    private fun showResults(results: List<PlantClassifier.ClassificationResult>) {
        progressBar.visibility = View.GONE

        if (results.isEmpty()) {
            tvStatus.text = "Не удалось определить растение"
            return
        }

        val top = results.first()

        // Русское название
        tvPlantName.text = top.nameRu

        // Научное название (курсивом через разметку, если пустое — скрыть)
        if (top.nameSci.isNotEmpty()) {
            tvPlantSci.text       = top.nameSci
            tvPlantSci.visibility = View.VISIBLE
        } else {
            tvPlantSci.visibility = View.GONE
        }

        // Уверенность
        tvConfidence.text = "Уверенность: ${top.confidencePercent}"

        // Предупреждение если модель не уверена
        if (!top.isConfident) {
            tvLowConfidence.text       = "⚠️ Низкая уверенность — поднесите камеру ближе или улучшите освещение"
            tvLowConfidence.visibility = View.VISIBLE
        } else {
            tvLowConfidence.visibility = View.GONE
        }

        // Другие варианты (только если уверенность > 5%)
        val others = results.drop(1).filter { it.confidence > 0.05f }
        if (others.isNotEmpty()) {
            tvOtherResults.text = "Другие варианты:\n" + others.joinToString("\n") {
                "• ${it.nameRu} — ${it.confidencePercent}"
            }
            tvOtherResults.visibility = View.VISIBLE
        } else {
            tvOtherResults.visibility = View.GONE
        }

        resultsContainer.visibility = View.VISIBLE
        tvStatus.text = "Результат:"

        btnPlantGuide.setOnClickListener {
            PlantGuideActivity.start(this, top.rawLabel)
        }
    }

    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        classifier.close()
    }
}
