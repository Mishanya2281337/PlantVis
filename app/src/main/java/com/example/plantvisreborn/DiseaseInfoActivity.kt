package com.example.plantvisreborn

import android.content.Intent
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

/**
 * DiseaseInfoActivity — экран диагностики болезней и рекомендаций.
 *
 * Запускается с MainActivity после определения вида растения.
 * Принимает изображение, запускает DiseaseClassifier и показывает:
 *   - диагноз с уверенностью
 *   - тип патогена и степень опасности
 *   - симптомы, причины, лечение, профилактику
 */
class DiseaseInfoActivity : AppCompatActivity() {

    private lateinit var imageView:       ImageView
    private lateinit var btnCamera:       Button
    private lateinit var btnGallery:      Button
    private lateinit var progressBar:     ProgressBar
    private lateinit var tvStatus:        TextView
    private lateinit var cardResult:      LinearLayout
    private lateinit var tvDiseaseName:   TextView
    private lateinit var tvPathogen:      TextView
    private lateinit var tvSeverity:      TextView
    private lateinit var tvSpread:        TextView
    private lateinit var tvUrgency:       TextView
    private lateinit var tvConfidence:    TextView
    private lateinit var sectionSymptoms: LinearLayout
    private lateinit var tvSymptoms:      TextView
    private lateinit var sectionCauses:   LinearLayout
    private lateinit var tvCauses:        TextView
    private lateinit var sectionTreatment:LinearLayout
    private lateinit var tvTreatment:     TextView
    private lateinit var sectionPrevention:LinearLayout
    private lateinit var tvPrevention:    TextView
    private lateinit var tvOtherResults:  TextView

    private val classifier = DiseaseClassifier(this)
    private val advisor     = DiseaseAdvisor(this)

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bmp -> bmp?.let { analyzeImage(it) } }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bmp = MediaStore.Images.Media.getBitmap(contentResolver, it)
            analyzeImage(bmp)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_disease_info)
        supportActionBar?.title = "Диагностика болезней"

        imageView        = findViewById(R.id.diseaseImageView)
        btnCamera        = findViewById(R.id.diseaseBtnCamera)
        btnGallery       = findViewById(R.id.diseaseBtnGallery)
        progressBar      = findViewById(R.id.diseaseProgressBar)
        tvStatus         = findViewById(R.id.diseaseTvStatus)
        cardResult       = findViewById(R.id.diseaseCardResult)
        tvDiseaseName    = findViewById(R.id.tvDiseaseName)
        tvPathogen       = findViewById(R.id.tvPathogen)
        tvSeverity       = findViewById(R.id.tvSeverity)
        tvSpread         = findViewById(R.id.tvSpread)
        tvUrgency        = findViewById(R.id.tvUrgency)
        tvConfidence     = findViewById(R.id.tvDiseaseConfidence)
        sectionSymptoms  = findViewById(R.id.sectionSymptoms)
        tvSymptoms       = findViewById(R.id.tvSymptoms)
        sectionCauses    = findViewById(R.id.sectionCauses)
        tvCauses         = findViewById(R.id.tvCauses)
        sectionTreatment = findViewById(R.id.sectionTreatment)
        tvTreatment      = findViewById(R.id.tvTreatment)
        sectionPrevention= findViewById(R.id.sectionPrevention)
        tvPrevention     = findViewById(R.id.tvPrevention)
        tvOtherResults   = findViewById(R.id.tvDiseaseOther)

        btnCamera.setOnClickListener  { cameraLauncher.launch(null) }
        btnGallery.setOnClickListener { galleryLauncher.launch("image/*") }

        // Инициализация в фоне
        lifecycleScope.launch(Dispatchers.IO) {
            classifier.setup()
            advisor.load()
            withContext(Dispatchers.Main) {
                tvStatus.text = "Сфотографируйте лист или поражённый участок растения"
            }
        }

        // Если запущено с уже готовым bitmap из MainActivity
        @Suppress("DEPRECATION")
        val bmp = intent.getParcelableExtra<Bitmap>("bitmap")
        bmp?.let {
            imageView.setImageBitmap(it)
            analyzeImage(it)
        }
    }

    private fun analyzeImage(bitmap: Bitmap) {
        imageView.setImageBitmap(bitmap)
        cardResult.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "Анализируем признаки болезни..."

        lifecycleScope.launch(Dispatchers.IO) {
            val results = classifier.classify(bitmap)
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                if (results.isEmpty()) {
                    tvStatus.text = "Не удалось определить состояние растения"
                } else {
                    showResults(results)
                }
            }
        }
    }

    private fun showResults(results: List<DiseaseClassifier.DiseaseResult>) {
        val top  = results.first()
        val info = advisor.getInfo(top.diseaseType)

        tvConfidence.text = "Уверенность: ${top.confidencePercent}"

        if (info == null) {
            // Модель ещё не обучена — показать технические метки
            tvDiseaseName.text = formatLabel(top.diseaseType)
            tvPathogen.text    = ""
            tvSeverity.text    = ""
            tvSpread.text      = ""
            tvUrgency.text     = ""
            hideAllSections()
        } else {
            tvDiseaseName.text = info.nameRu
            tvPathogen.text    = advisor.getPathogenName(info.pathogen)
            tvSeverity.text    = "${info.severity.emoji} Тяжесть: ${info.severity.ru}"
            tvSpread.text      = "Заразность: ${info.spread.ru}"
            tvUrgency.text     = advisor.getUrgencyMessage(info)

            // Цвет блока уверенности по тяжести
            val urgencyColor = when (info.severity) {
                DiseaseAdvisor.Severity.CRITICAL -> ContextCompat.getColor(this, android.R.color.holo_red_light)
                DiseaseAdvisor.Severity.HIGH     -> 0xFFFF6B35.toInt()
                DiseaseAdvisor.Severity.MEDIUM   -> 0xFFFFA500.toInt()
                else                             -> ContextCompat.getColor(this, android.R.color.holo_green_light)
            }
            tvUrgency.setTextColor(urgencyColor)

            // Секции
            showSection(sectionSymptoms,   tvSymptoms,   info.symptoms,   "Симптомы")
            showSection(sectionCauses,     tvCauses,     info.causes,     "Причины")
            showSection(sectionTreatment,  tvTreatment,  info.treatment,  "Лечение")
            showSection(sectionPrevention, tvPrevention, info.prevention, "Профилактика")
        }

        // Прочие варианты
        if (results.size > 1) {
            val others = results.drop(1).joinToString("\n") {
                val otherInfo = advisor.getInfo(it.diseaseType)
                val name = otherInfo?.nameRu ?: formatLabel(it.diseaseType)
                "• $name — ${it.confidencePercent}"
            }
            tvOtherResults.text = "Другие варианты:\n$others"
            tvOtherResults.visibility = View.VISIBLE
        } else {
            tvOtherResults.visibility = View.GONE
        }

        tvStatus.text = if (info?.isHealthy == true) "Растение здорово" else "Результат диагностики:"
        cardResult.visibility = View.VISIBLE
    }

    private fun showSection(container: LinearLayout, tv: TextView,
                            items: List<String>, title: String) {
        if (items.isEmpty()) {
            container.visibility = View.GONE
        } else {
            container.visibility = View.VISIBLE
            tv.text = items.joinToString("\n") { "• $it" }
        }
    }

    private fun hideAllSections() {
        listOf(sectionSymptoms, sectionCauses, sectionTreatment, sectionPrevention)
            .forEach { it.visibility = View.GONE }
    }

    private fun formatLabel(raw: String): String =
        raw.replace("_", " ").replaceFirstChar { it.uppercaseChar() }

    override fun onDestroy() {
        super.onDestroy()
        classifier.close()
    }
}
