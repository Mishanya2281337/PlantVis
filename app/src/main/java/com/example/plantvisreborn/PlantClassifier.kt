package com.example.plantvisreborn

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.classifier.Classifications
import org.tensorflow.lite.task.vision.classifier.ImageClassifier

/**
 * PlantClassifier — классификатор комнатных растений.
 *
 * Модель: EfficientNetB2 (Float16 TFLite), 18 классов.
 * Входной размер: 260x260 (нативный для EfficientNetB2).
 */
class PlantClassifier(private val context: Context) {

    companion object {
        private const val MODEL_FILE      = "plant_classifier.tflite"
        private const val MAX_RESULTS     = 5
        private const val SCORE_THRESHOLD = 0.05f
        private const val INPUT_SIZE      = 260   // EfficientNetB2 = 260, MobileNetV2 = 224

        val LABEL_TO_RUSSIAN = mapOf(
            "aloe_vera"                to "Алоэ вера",
            "anthurium_andraeanum"     to "Антуриум Андре",
            "begonia_rex"              to "Бегония королевская",
            "chlorophytum_comosum"     to "Хлорофитум хохлатый",
            "cyperus_alternifolius"    to "Циперус зонтичный",
            "dieffenbachia"            to "Диффенбахия пятнистая",
            "dracaena_marginata"       to "Драцена окаймлённая",
            "ficus_benjamina"          to "Фикус бенджамина",
            "fittonia"                 to "Фиттония",
            "hibiscus_rosa_sinensis"   to "Гибискус китайский",
            "kalanchoe_blossfeldiana"  to "Каланхоэ Блоссфельда",
            "monstera_deliciosa"       to "Монстера деликатесная",
            "orchid_phalaenopsis"      to "Орхидея фаленопсис",
            "pelargonium_zonale"       to "Пеларгония зональная",
            "pothos_aureum"            to "Потос золотистый",
            "sansevieria"              to "Сансевиерия",
            "spathiphyllum"            to "Спатифиллум",
            "zamioculcas"              to "Замиокулькас"
        )

        val LABEL_TO_SCIENTIFIC = mapOf(
            "aloe_vera"                to "Aloe vera",
            "anthurium_andraeanum"     to "Anthurium andraeanum",
            "begonia_rex"              to "Begonia rex",
            "chlorophytum_comosum"     to "Chlorophytum comosum",
            "cyperus_alternifolius"    to "Cyperus alternifolius",
            "dieffenbachia"            to "Dieffenbachia maculata",
            "dracaena_marginata"       to "Dracaena marginata",
            "ficus_benjamina"          to "Ficus benjamina",
            "fittonia"                 to "Fittonia albivenis",
            "hibiscus_rosa_sinensis"   to "Hibiscus rosa-sinensis",
            "kalanchoe_blossfeldiana"  to "Kalanchoe blossfeldiana",
            "monstera_deliciosa"       to "Monstera deliciosa",
            "orchid_phalaenopsis"      to "Phalaenopsis sp.",
            "pelargonium_zonale"       to "Pelargonium zonale",
            "pothos_aureum"            to "Epipremnum aureum",
            "sansevieria"              to "Sansevieria trifasciata",
            "spathiphyllum"            to "Spathiphyllum wallisii",
            "zamioculcas"              to "Zamioculcas zamiifolia"
        )
    }

    private var classifier: ImageClassifier? = null

    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .build()

    fun setup() {
        // GPU -> NNAPI -> CPU
        try {
            val opts = ImageClassifier.ImageClassifierOptions.builder()
                .setBaseOptions(BaseOptions.builder().useGpu().build())
                .setMaxResults(MAX_RESULTS).setScoreThreshold(SCORE_THRESHOLD).build()
            classifier = ImageClassifier.createFromFileAndOptions(context, MODEL_FILE, opts)
            return
        } catch (_: Exception) {}
        try {
            val opts = ImageClassifier.ImageClassifierOptions.builder()
                .setBaseOptions(BaseOptions.builder().useNnapi().build())
                .setMaxResults(MAX_RESULTS).setScoreThreshold(SCORE_THRESHOLD).build()
            classifier = ImageClassifier.createFromFileAndOptions(context, MODEL_FILE, opts)
            return
        } catch (_: Exception) {}
        val opts = ImageClassifier.ImageClassifierOptions.builder()
            .setMaxResults(MAX_RESULTS).setScoreThreshold(SCORE_THRESHOLD).build()
        classifier = ImageClassifier.createFromFileAndOptions(context, MODEL_FILE, opts)
    }

    fun classify(bitmap: Bitmap): List<ClassificationResult> {
        val processed = imageProcessor.process(TensorImage.fromBitmap(bitmap))
        val results: List<Classifications> = classifier?.classify(processed) ?: return emptyList()
        return results.flatMap { it.categories }
            .map { ClassificationResult(it.label, it.score) }
            .sortedByDescending { it.confidence }
    }

    fun isReady(): Boolean = classifier != null

    fun close() { classifier?.close(); classifier = null }

    data class ClassificationResult(val rawLabel: String, val confidence: Float) {
        val nameRu: String get() = LABEL_TO_RUSSIAN[rawLabel]
            ?: rawLabel.replace("_", " ").split(" ")
                .joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
        val nameSci: String  get() = LABEL_TO_SCIENTIFIC[rawLabel] ?: ""
        val confidencePercent: String get() = "%.1f%%".format(confidence * 100)
        val isConfident: Boolean get() = confidence >= 0.70f
    }
}
