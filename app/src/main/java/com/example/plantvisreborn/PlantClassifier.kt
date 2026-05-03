package com.example.plantvisreborn

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class PlantClassifier(private val context: Context) {

    companion object {
        private const val MODEL_FILE  = "plant_classifier.tflite"
        private const val MAX_RESULTS = 5
        private const val THRESHOLD   = 0.05f
        private const val INPUT_SIZE  = 260

        val LABELS = listOf(
            "aloe_vera", "anthurium_andraeanum", "begonia_rex",
            "chlorophytum_comosum", "cyperus_alternifolius", "dieffenbachia",
            "dracaena_marginata", "ficus_benjamina", "fittonia",
            "hibiscus_rosa_sinensis", "kalanchoe_blossfeldiana", "monstera_deliciosa",
            "orchid_phalaenopsis", "pelargonium_zonale", "pothos_aureum",
            "sansevieria", "spathiphyllum", "zamioculcas"
        )

        val LABEL_TO_RUSSIAN = mapOf(
            "aloe_vera" to "Алоэ вера", "anthurium_andraeanum" to "Антуриум Андре",
            "begonia_rex" to "Бегония королевская", "chlorophytum_comosum" to "Хлорофитум хохлатый",
            "cyperus_alternifolius" to "Циперус зонтичный", "dieffenbachia" to "Диффенбахия пятнистая",
            "dracaena_marginata" to "Драцена окаймлённая", "ficus_benjamina" to "Фикус бенджамина",
            "fittonia" to "Фиттония", "hibiscus_rosa_sinensis" to "Гибискус китайский",
            "kalanchoe_blossfeldiana" to "Каланхоэ Блоссфельда", "monstera_deliciosa" to "Монстера деликатесная",
            "orchid_phalaenopsis" to "Орхидея фаленопсис", "pelargonium_zonale" to "Пеларгония зональная",
            "pothos_aureum" to "Потос золотистый", "sansevieria" to "Сансевиерия",
            "spathiphyllum" to "Спатифиллум", "zamioculcas" to "Замиокулькас"
        )

        val LABEL_TO_SCIENTIFIC = mapOf(
            "aloe_vera" to "Aloe vera", "anthurium_andraeanum" to "Anthurium andraeanum",
            "begonia_rex" to "Begonia rex", "chlorophytum_comosum" to "Chlorophytum comosum",
            "cyperus_alternifolius" to "Cyperus alternifolius", "dieffenbachia" to "Dieffenbachia maculata",
            "dracaena_marginata" to "Dracaena marginata", "ficus_benjamina" to "Ficus benjamina",
            "fittonia" to "Fittonia albivenis", "hibiscus_rosa_sinensis" to "Hibiscus rosa-sinensis",
            "kalanchoe_blossfeldiana" to "Kalanchoe blossfeldiana", "monstera_deliciosa" to "Monstera deliciosa",
            "orchid_phalaenopsis" to "Phalaenopsis sp.", "pelargonium_zonale" to "Pelargonium zonale",
            "pothos_aureum" to "Epipremnum aureum", "sansevieria" to "Sansevieria trifasciata",
            "spathiphyllum" to "Spathiphyllum wallisii", "zamioculcas" to "Zamioculcas zamiifolia"
        )
    }

    private var interpreter: Interpreter? = null

    fun setup() {
        val afd = context.assets.openFd(MODEL_FILE)
        val buffer = FileInputStream(afd.fileDescriptor).channel
            .map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
        val opts = Interpreter.Options()
        opts.numThreads = 4
        interpreter = Interpreter(buffer, opts)
    }

    fun classify(bitmap: Bitmap): List<ClassificationResult> {
        val interp = interpreter ?: return emptyList()
        val input = bitmapToByteBuffer(bitmap, INPUT_SIZE)
        val output = Array(1) { FloatArray(LABELS.size) }
        interp.run(input, output)
        return output[0].mapIndexed { i, score -> ClassificationResult(LABELS[i], score) }
            .filter { it.confidence >= THRESHOLD }
            .sortedByDescending { it.confidence }
            .take(MAX_RESULTS)
    }

    fun isReady(): Boolean = interpreter != null

    fun close() { interpreter?.close(); interpreter = null }

    private fun bitmapToByteBuffer(bitmap: Bitmap, size: Int): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val buf = ByteBuffer.allocateDirect(4 * size * size * 3)
        buf.order(ByteOrder.nativeOrder())
        // В методе bitmapToByteBuffer замени цикл на этот:
        for (y in 0 until size) for (x in 0 until size) {
            val px = scaled.getPixel(x, y)
            // Подаем чистые значения 0..255
            buf.putFloat(Color.red(px).toFloat())
            buf.putFloat(Color.green(px).toFloat())
            buf.putFloat(Color.blue(px).toFloat())
        }
        buf.rewind()
        return buf
    }

    data class ClassificationResult(val rawLabel: String, val confidence: Float) {
        val nameRu: String get() = LABEL_TO_RUSSIAN[rawLabel]
            ?: rawLabel.replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
        val nameSci: String get() = LABEL_TO_SCIENTIFIC[rawLabel] ?: ""
        val confidencePercent: String get() = "%.1f%%".format(confidence * 100)
        val isConfident: Boolean get() = confidence >= 0.70f
    }
}
