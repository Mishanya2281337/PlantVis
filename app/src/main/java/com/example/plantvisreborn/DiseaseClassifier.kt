package com.example.plantvisreborn

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * DiseaseClassifier — определяет ТИП болезни (6 классов).
 * Выход модели: [1, 6] — healthy, chlorosis, leaf_burn, mealybug, root_rot, spider_mite
 * Полный лейбл формируется как "{plant}_{disease}" снаружи.
 */
class DiseaseClassifier(private val context: Context) {

    companion object {
        private const val MODEL_FILE  = "disease_classifier.tflite"
        private const val MAX_RESULTS = 3
        private const val THRESHOLD   = 0.05f
        private const val INPUT_SIZE  = 260

        // 6 типов болезней — порядок должен совпадать с выходом модели
        val DISEASE_TYPES = listOf(
            "healthy", "chlorosis", "leaf_burn", "mealybug", "root_rot", "spider_mite"
        )

        val DISEASE_TYPE_RU = mapOf(
            "healthy"     to "Здорово",
            "chlorosis"   to "Хлороз",
            "leaf_burn"   to "Ожог листьев",
            "mealybug"    to "Мучнистый червец",
            "root_rot"    to "Корневая гниль",
            "spider_mite" to "Паутинный клещ"
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

    fun classify(bitmap: Bitmap): List<DiseaseResult> {
        val interp = interpreter ?: return emptyList()
        val input = bitmapToByteBuffer(bitmap, INPUT_SIZE)
        val output = Array(1) { FloatArray(DISEASE_TYPES.size) }
        interp.run(input, output)
        return output[0].mapIndexed { i, score ->
            DiseaseResult(DISEASE_TYPES[i], score)
        }.filter { it.confidence >= THRESHOLD }
         .sortedByDescending { it.confidence }
         .take(MAX_RESULTS)
    }

    fun close() { interpreter?.close(); interpreter = null }

    private fun bitmapToByteBuffer(bitmap: Bitmap, size: Int): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val buf = ByteBuffer.allocateDirect(4 * size * size * 3)
        buf.order(ByteOrder.nativeOrder())
        for (y in 0 until size) for (x in 0 until size) {
            val px = scaled.getPixel(x, y)
            buf.putFloat(Color.red(px) / 255f)
            buf.putFloat(Color.green(px) / 255f)
            buf.putFloat(Color.blue(px) / 255f)
        }
        buf.rewind()
        return buf
    }

    data class DiseaseResult(val diseaseType: String, val confidence: Float) {
        val confidencePercent: String get() = "%.1f%%".format(confidence * 100)
        val isHealthy: Boolean get() = diseaseType == "healthy"
        val nameRu: String get() = DISEASE_TYPE_RU[diseaseType] ?: diseaseType
    }
}
