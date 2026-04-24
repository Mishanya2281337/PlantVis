package com.example.plantvisreborn

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * PlantGuideActivity — справочник растений с фотогалереей и подробным описанием ухода.
 *
 * Запускается с MainActivity при нажатии кнопки «Справочник».
 * Опционально принимает plantLabel — метку распознанного растения,
 * чтобы сразу открыть нужную карточку.
 *
 * Использует: plant_guide.json из assets, фото с Unsplash (бесплатно, без ключа).
 */
class PlantGuideActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PLANT_LABEL = "plant_label"

        fun start(context: Context, plantLabel: String? = null) {
            val intent = Intent(context, PlantGuideActivity::class.java)
            plantLabel?.let { intent.putExtra(EXTRA_PLANT_LABEL, it) }
            context.startActivity(intent)
        }
    }

    // ── Данные ────────────────────────────────────────────────────────────────

    data class CareInfo(
        val light: String,
        val watering: String,
        val humidity: String,
        val temperature: String,
        val soil: String,
        val fertilizing: String,
        val repotting: String,
        val propagation: String,
        val toxicity: String,
        val difficulty: String
    )

    data class PlantInfo(
        val key: String,
        val nameRu: String,
        val nameSci: String,
        val family: String,
        val origin: String,
        val photos: List<String>,
        val shortDesc: String,
        val care: CareInfo
    )

    // ── State ─────────────────────────────────────────────────────────────────

    private var plants: List<PlantInfo> = emptyList()
    private var currentPlant: PlantInfo? = null
    private var currentPhotoIndex = 0
    private val photoCache = mutableMapOf<String, Bitmap>()

    // ── Views ─────────────────────────────────────────────────────────────────

    // Список
    private lateinit var listContainer: LinearLayout
    private lateinit var scrollList: NestedScrollView

    // Детальная карточка
    private lateinit var cardContainer: NestedScrollView
    private lateinit var btnBack: Button
    private lateinit var photoView: ImageView
    private lateinit var photoDots: LinearLayout
    private lateinit var btnPhotoLeft: ImageButton
    private lateinit var btnPhotoRight: ImageButton
    private lateinit var tvDetailName: TextView
    private lateinit var tvDetailSci: TextView
    private lateinit var tvDetailFamily: TextView
    private lateinit var tvDetailOrigin: TextView
    private lateinit var tvDetailDesc: TextView
    private lateinit var tvDetailDifficulty: TextView
    private lateinit var tvLight: TextView
    private lateinit var tvWatering: TextView
    private lateinit var tvHumidity: TextView
    private lateinit var tvTemperature: TextView
    private lateinit var tvSoil: TextView
    private lateinit var tvFertilizing: TextView
    private lateinit var tvRepotting: TextView
    private lateinit var tvPropagation: TextView
    private lateinit var tvToxicity: TextView
    private lateinit var photoProgress: ProgressBar

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plant_guide)
        supportActionBar?.title = "Справочник растений"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        bindViews()
        bindListeners()

        lifecycleScope.launch(Dispatchers.IO) {
            val loaded = loadPlants()
            withContext(Dispatchers.Main) {
                plants = loaded
                val openLabel = intent.getStringExtra(EXTRA_PLANT_LABEL)
                if (openLabel != null) {
                    val plant = plants.find { it.key == openLabel }
                    if (plant != null) { openCard(plant); return@withContext }
                }
                buildList()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (cardContainer.visibility == View.VISIBLE) {
            showList()
            return true
        }
        finish()
        return true
    }

    override fun onBackPressed() {
        if (cardContainer.visibility == View.VISIBLE) showList()
        else super.onBackPressed()
    }

    // ── Views binding ─────────────────────────────────────────────────────────

    private fun bindViews() {
        listContainer  = findViewById(R.id.guideListContainer)
        scrollList     = findViewById(R.id.guideScrollList)
        cardContainer  = findViewById(R.id.guideCardContainer)
        btnBack        = findViewById(R.id.guideBtnBack)
        photoView      = findViewById(R.id.guidePhotoView)
        photoDots      = findViewById(R.id.guidePhotoDots)
        btnPhotoLeft   = findViewById(R.id.guideBtnPhotoLeft)
        btnPhotoRight  = findViewById(R.id.guideBtnPhotoRight)
        tvDetailName   = findViewById(R.id.guideDetailName)
        tvDetailSci    = findViewById(R.id.guideDetailSci)
        tvDetailFamily = findViewById(R.id.guideDetailFamily)
        tvDetailOrigin = findViewById(R.id.guideDetailOrigin)
        tvDetailDesc   = findViewById(R.id.guideDetailDesc)
        tvDetailDifficulty = findViewById(R.id.guideDetailDifficulty)
        tvLight        = findViewById(R.id.guideCareLight)
        tvWatering     = findViewById(R.id.guideCareWatering)
        tvHumidity     = findViewById(R.id.guideCareHumidity)
        tvTemperature  = findViewById(R.id.guideCareTemperature)
        tvSoil         = findViewById(R.id.guideCareSoil)
        tvFertilizing  = findViewById(R.id.guideCareFertilizing)
        tvRepotting    = findViewById(R.id.guideCareRepotting)
        tvPropagation  = findViewById(R.id.guideCarePropagation)
        tvToxicity     = findViewById(R.id.guideCareToxicity)
        photoProgress  = findViewById(R.id.guidePhotoProgress)
    }

    private fun bindListeners() {
        btnBack.setOnClickListener { showList() }
        btnPhotoLeft.setOnClickListener {
            val plant = currentPlant ?: return@setOnClickListener
            if (currentPhotoIndex > 0) {
                currentPhotoIndex--
                loadPhoto(plant.photos[currentPhotoIndex])
                updateDots(plant.photos.size)
            }
        }
        btnPhotoRight.setOnClickListener {
            val plant = currentPlant ?: return@setOnClickListener
            if (currentPhotoIndex < plant.photos.size - 1) {
                currentPhotoIndex++
                loadPhoto(plant.photos[currentPhotoIndex])
                updateDots(plant.photos.size)
            }
        }
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private fun loadPlants(): List<PlantInfo> {
        val json = assets.open("plant_guide.json").bufferedReader().readText()
        val root = JSONObject(json)
        val list = mutableListOf<PlantInfo>()
        root.keys().forEach { key ->
            val obj  = root.getJSONObject(key)
            val care = obj.getJSONObject("care")
            val photosArr = obj.getJSONArray("photos")
            val photos = (0 until photosArr.length()).map { photosArr.getString(it) }
            list += PlantInfo(
                key       = key,
                nameRu    = obj.getString("nameRu"),
                nameSci   = obj.getString("nameSci"),
                family    = obj.getString("family"),
                origin    = obj.getString("origin"),
                photos    = photos,
                shortDesc = obj.getString("shortDesc"),
                care = CareInfo(
                    light       = care.getString("light"),
                    watering    = care.getString("watering"),
                    humidity    = care.getString("humidity"),
                    temperature = care.getString("temperature"),
                    soil        = care.getString("soil"),
                    fertilizing = care.getString("fertilizing"),
                    repotting   = care.getString("repotting"),
                    propagation = care.getString("propagation"),
                    toxicity    = care.getString("toxicity"),
                    difficulty  = care.getString("difficulty")
                )
            )
        }
        return list.sortedBy { it.nameRu }
    }

    // ── List screen ───────────────────────────────────────────────────────────

    private fun buildList() {
        listContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        plants.forEach { plant ->
            val row = inflater.inflate(R.layout.item_plant_guide, listContainer, false)
            row.findViewById<TextView>(R.id.itemPlantName).text   = plant.nameRu
            row.findViewById<TextView>(R.id.itemPlantSci).text    = plant.nameSci
            row.findViewById<TextView>(R.id.itemPlantDiff).text   = difficultyLabel(plant.care.difficulty)
            row.findViewById<TextView>(R.id.itemPlantFamily).text = plant.family
            // Загружаем первое фото превью
            val thumb = row.findViewById<ImageView>(R.id.itemPlantThumb)
            loadThumbnail(plant.photos.firstOrNull(), thumb)
            row.setOnClickListener { openCard(plant) }
            listContainer.addView(row)
        }
        showList()
    }

    private fun loadThumbnail(url: String?, imageView: ImageView) {
        if (url == null) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bmp = photoCache.getOrPut(url) {
                    URL(url).openStream().use { BitmapFactory.decodeStream(it) }
                }
                withContext(Dispatchers.Main) { imageView.setImageBitmap(bmp) }
            } catch (_: Exception) {}
        }
    }

    // ── Card screen ───────────────────────────────────────────────────────────

    private fun openCard(plant: PlantInfo) {
        currentPlant = plant
        currentPhotoIndex = 0

        tvDetailName.text   = plant.nameRu
        tvDetailSci.text    = plant.nameSci
        tvDetailFamily.text = "Семейство: ${plant.family}"
        tvDetailOrigin.text = "Родина: ${plant.origin}"
        tvDetailDesc.text   = plant.shortDesc
        tvDetailDifficulty.text = "Сложность: ${plant.care.difficulty}"

        tvLight.text       = plant.care.light
        tvWatering.text    = plant.care.watering
        tvHumidity.text    = plant.care.humidity
        tvTemperature.text = plant.care.temperature
        tvSoil.text        = plant.care.soil
        tvFertilizing.text = plant.care.fertilizing
        tvRepotting.text   = plant.care.repotting
        tvPropagation.text = plant.care.propagation
        tvToxicity.text    = plant.care.toxicity

        buildDots(plant.photos.size)
        loadPhoto(plant.photos.first())
        updateDots(plant.photos.size)

        cardContainer.visibility = View.VISIBLE
        scrollList.visibility    = View.GONE
        cardContainer.scrollTo(0, 0)
    }

    private fun showList() {
        cardContainer.visibility = View.GONE
        scrollList.visibility    = View.VISIBLE
    }

    // ── Photos ────────────────────────────────────────────────────────────────

    private fun loadPhoto(url: String) {
        photoProgress.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bmp = photoCache.getOrPut(url) {
                    URL(url).openStream().use { BitmapFactory.decodeStream(it) }
                }
                withContext(Dispatchers.Main) {
                    photoView.setImageBitmap(bmp)
                    photoProgress.visibility = View.GONE
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { photoProgress.visibility = View.GONE }
            }
        }
    }

    private fun buildDots(count: Int) {
        photoDots.removeAllViews()
        repeat(count) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(16, 16).apply {
                    marginEnd = 8
                }
                setBackgroundResource(R.drawable.dot_inactive)
            }
            photoDots.addView(dot)
        }
    }

    private fun updateDots(count: Int) {
        for (i in 0 until count) {
            val dot = photoDots.getChildAt(i) as? View ?: continue
            dot.setBackgroundResource(
                if (i == currentPhotoIndex) R.drawable.dot_active else R.drawable.dot_inactive
            )
        }
        btnPhotoLeft.visibility  = if (currentPhotoIndex > 0) View.VISIBLE else View.INVISIBLE
        btnPhotoRight.visibility = if (currentPhotoIndex < count - 1) View.VISIBLE else View.INVISIBLE
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun difficultyLabel(d: String) = when (d) {
        "Очень просто" -> "🟢 Очень просто"
        "Легко"        -> "🟡 Легко"
        "Средне"       -> "🟠 Средне"
        "Сложно"       -> "🔴 Сложно"
        else           -> d
    }
}
