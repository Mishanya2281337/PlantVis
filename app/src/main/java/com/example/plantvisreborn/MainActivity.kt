package com.example.plantvisreborn

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var photoHint: LinearLayout
    private lateinit var btnCamera: Button
    private lateinit var btnGallery: Button
    private lateinit var btnIdentify: Button
    private lateinit var btnDiagnose: Button
    private lateinit var btnPlantGuide: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView

    // Plant result
    private lateinit var resultsContainer: LinearLayout
    private lateinit var tvPlantName: TextView
    private lateinit var tvPlantSci: TextView
    private lateinit var tvConfidence: TextView
    private lateinit var tvLowConfidence: TextView
    private lateinit var tvOtherResults: TextView

    // Disease result
    private lateinit var diseaseCardResult: LinearLayout
    private lateinit var tvDiseaseName: TextView
    private lateinit var tvDiseaseConfidence: TextView
    private lateinit var tvPathogen: TextView
    private lateinit var tvSeverity: TextView
    private lateinit var tvSpread: TextView
    private lateinit var tvUrgency: TextView
    private lateinit var sectionSymptoms: LinearLayout
    private lateinit var tvSymptoms: TextView
    private lateinit var sectionCauses: LinearLayout
    private lateinit var tvCauses: TextView
    private lateinit var sectionTreatment: LinearLayout
    private lateinit var tvTreatment: TextView
    private lateinit var sectionPrevention: LinearLayout
    private lateinit var tvPrevention: TextView
    private lateinit var tvDiseaseOther: TextView

    private val plantClassifier = PlantClassifier(this)
    private val diseaseClassifier = DiseaseClassifier(this)
    private val diseaseAdvisor = DiseaseAdvisor(this)
    private var currentBitmap: Bitmap? = null
    private var modelsReady = false
    private var detectedPlantLabel: String? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) openCamera() else toast("Нет разрешения на камеру") }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap -> bitmap?.let { onImageSelected(it) } }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            @Suppress("DEPRECATION")
            val bmp = MediaStore.Images.Media.getBitmap(contentResolver, it)
            onImageSelected(bmp)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()

        btnCamera.setOnClickListener { checkCameraAndOpen() }
        btnGallery.setOnClickListener { galleryLauncher.launch("image/*") }
        btnIdentify.setOnClickListener { currentBitmap?.let { identify(it) } }
        btnDiagnose.setOnClickListener { currentBitmap?.let { diagnose(it) } }
        btnPlantGuide.setOnClickListener { PlantGuideActivity.start(this, detectedPlantLabel) }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                plantClassifier.setup()
                diseaseClassifier.setup()
                diseaseAdvisor.load()
                withContext(Dispatchers.Main) {
                    modelsReady = true
                    tvStatus.text = "Готово. Сфотографируйте растение!"
                    updateButtons()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "⚠️ Ошибка загрузки: ${e.message}"
                }
            }
        }
    }

    private fun bindViews() {
        imageView         = findViewById(R.id.imageView)
        photoHint         = findViewById(R.id.photoHint)
        btnCamera         = findViewById(R.id.btnCamera)
        btnGallery        = findViewById(R.id.btnGallery)
        btnIdentify       = findViewById(R.id.btnIdentify)
        btnDiagnose       = findViewById(R.id.btnDiagnose)
        btnPlantGuide     = findViewById(R.id.btnPlantGuide)
        progressBar       = findViewById(R.id.progressBar)
        tvStatus          = findViewById(R.id.tvStatus)
        resultsContainer  = findViewById(R.id.resultsContainer)
        tvPlantName       = findViewById(R.id.tvPlantName)
        tvPlantSci        = findViewById(R.id.tvPlantSci)
        tvConfidence      = findViewById(R.id.tvConfidence)
        tvLowConfidence   = findViewById(R.id.tvLowConfidence)
        tvOtherResults    = findViewById(R.id.tvOtherResults)
        diseaseCardResult = findViewById(R.id.diseaseCardResult)
        tvDiseaseName     = findViewById(R.id.tvDiseaseName)
        tvDiseaseConfidence = findViewById(R.id.tvDiseaseConfidence)
        tvPathogen        = findViewById(R.id.tvPathogen)
        tvSeverity        = findViewById(R.id.tvSeverity)
        tvSpread          = findViewById(R.id.tvSpread)
        tvUrgency         = findViewById(R.id.tvUrgency)
        sectionSymptoms   = findViewById(R.id.sectionSymptoms)
        tvSymptoms        = findViewById(R.id.tvSymptoms)
        sectionCauses     = findViewById(R.id.sectionCauses)
        tvCauses          = findViewById(R.id.tvCauses)
        sectionTreatment  = findViewById(R.id.sectionTreatment)
        tvTreatment       = findViewById(R.id.tvTreatment)
        sectionPrevention = findViewById(R.id.sectionPrevention)
        tvPrevention      = findViewById(R.id.tvPrevention)
        tvDiseaseOther    = findViewById(R.id.tvDiseaseOther)
    }

    private fun onImageSelected(bitmap: Bitmap) {
        currentBitmap = bitmap
        imageView.setImageBitmap(bitmap)
        photoHint.visibility = View.GONE
        resultsContainer.visibility = View.GONE
        diseaseCardResult.visibility = View.GONE
        detectedPlantLabel = null
        updateButtons()
        tvStatus.text = "Фото выбрано. Выберите анализ."
    }

    private fun updateButtons() {
        val ready = modelsReady && currentBitmap != null
        btnIdentify.isEnabled = ready
        btnIdentify.alpha = if (ready) 1f else 0.4f
        btnDiagnose.isEnabled = ready
        btnDiagnose.alpha = if (ready) 1f else 0.4f
    }

    private fun setLoading(loading: Boolean, msg: String = "") {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        if (msg.isNotEmpty()) tvStatus.text = msg
        btnIdentify.isEnabled = !loading
        btnIdentify.alpha = if (!loading && currentBitmap != null && modelsReady) 1f else 0.4f
        btnDiagnose.isEnabled = !loading
        btnDiagnose.alpha = if (!loading && currentBitmap != null && modelsReady) 1f else 0.4f
    }

    // ── Определение растения ──────────────────────────────────────────────────

    private fun identify(bitmap: Bitmap) {
        resultsContainer.visibility = View.GONE
        setLoading(true, "Определяем растение...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val results = plantClassifier.classify(bitmap)
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    showPlantResults(results)
                    detectedPlantLabel = results.firstOrNull()?.rawLabel
                    tvStatus.text = "Растение определено"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoading(false, "Ошибка: ${e.message}")
                }
            }
        }
    }

    // ── Диагностика болезней ──────────────────────────────────────────────────

    private fun diagnose(bitmap: Bitmap) {
        diseaseCardResult.visibility = View.GONE
        setLoading(true, "Диагностируем болезни...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val results = diseaseClassifier.classify(bitmap)
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    showDiseaseResults(results, detectedPlantLabel)
                    tvStatus.text = "Диагностика завершена"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoading(false, "Ошибка: ${e.message}")
                }
            }
        }
    }

    // ── Отображение результатов ───────────────────────────────────────────────

    private fun showPlantResults(results: List<PlantClassifier.ClassificationResult>) {
        if (results.isEmpty()) { resultsContainer.visibility = View.GONE; return }
        val top = results.first()
        tvPlantName.text = top.nameRu
        if (top.nameSci.isNotEmpty()) {
            tvPlantSci.text = top.nameSci
            tvPlantSci.visibility = View.VISIBLE
        } else {
            tvPlantSci.visibility = View.GONE
        }
        tvConfidence.text = "Уверенность: ${top.confidencePercent}"
        if (!top.isConfident) {
            tvLowConfidence.text = "⚠️ Низкая уверенность — улучшите освещение"
            tvLowConfidence.visibility = View.VISIBLE
        } else {
            tvLowConfidence.visibility = View.GONE
        }
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
    }

    private fun showDiseaseResults(
        results: List<DiseaseClassifier.DiseaseResult>,
        plantLabel: String?
    ) {
        if (results.isEmpty()) { diseaseCardResult.visibility = View.GONE; return }
        val top = results.first()
        val fullLabel = if (plantLabel != null) "${plantLabel}_${top.diseaseType}" else top.diseaseType
        val info = diseaseAdvisor.getInfo(fullLabel)

        tvDiseaseConfidence.text = "Уверенность: ${top.confidencePercent}"

        if (info == null) {
            tvDiseaseName.text = if (top.isHealthy) "✅ Растение здорово" else top.nameRu
            tvPathogen.text = ""
            tvSeverity.text = ""
            tvSpread.text = ""
            tvUrgency.text = if (top.isHealthy) "Видимых признаков болезней не обнаружено"
                             else "Обнаружено: ${top.nameRu}"
            tvUrgency.setTextColor(if (top.isHealthy) 0xFF4CAF50.toInt() else 0xFFFFA500.toInt())
            listOf(sectionSymptoms, sectionCauses, sectionTreatment, sectionPrevention)
                .forEach { it.visibility = View.GONE }
        } else {
            tvDiseaseName.text = info.nameRu
            tvPathogen.text = diseaseAdvisor.getPathogenName(info.pathogen)
            tvSeverity.text = "${info.severity.emoji} Тяжесть: ${info.severity.ru}"
            tvSpread.text = "Заразность: ${info.spread.ru}"
            tvUrgency.text = diseaseAdvisor.getUrgencyMessage(info)
            val color = when (info.severity) {
                DiseaseAdvisor.Severity.CRITICAL -> ContextCompat.getColor(this, android.R.color.holo_red_light)
                DiseaseAdvisor.Severity.HIGH     -> 0xFFFF6B35.toInt()
                DiseaseAdvisor.Severity.MEDIUM   -> 0xFFFFA500.toInt()
                else                             -> 0xFF4CAF50.toInt()
            }
            tvUrgency.setTextColor(color)
            showSection(sectionSymptoms, tvSymptoms, info.symptoms)
            showSection(sectionCauses, tvCauses, info.causes)
            showSection(sectionTreatment, tvTreatment, info.treatment)
            showSection(sectionPrevention, tvPrevention, info.prevention)
        }

        if (results.size > 1) {
            val others = results.drop(1).joinToString("\n") {
                val lbl = if (plantLabel != null) "${plantLabel}_${it.diseaseType}" else it.diseaseType
                val i = diseaseAdvisor.getInfo(lbl)
                "• ${i?.nameRu ?: it.nameRu} — ${it.confidencePercent}"
            }
            tvDiseaseOther.text = "Другие варианты:\n$others"
            tvDiseaseOther.visibility = View.VISIBLE
        } else {
            tvDiseaseOther.visibility = View.GONE
        }
        diseaseCardResult.visibility = View.VISIBLE
    }

    private fun showSection(container: LinearLayout, tv: TextView, items: List<String>) {
        if (items.isEmpty()) { container.visibility = View.GONE; return }
        container.visibility = View.VISIBLE
        tv.text = items.joinToString("\n") { "• $it" }
    }

    private fun checkCameraAndOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) openCamera()
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun openCamera() = cameraLauncher.launch(null)
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        plantClassifier.close()
        diseaseClassifier.close()
    }
}
