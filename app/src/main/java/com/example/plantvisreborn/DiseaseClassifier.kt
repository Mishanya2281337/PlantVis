package com.example.plantvisreborn

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.task.vision.classifier.Classifications
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import org.tensorflow.lite.task.core.BaseOptions

/**
 * DiseaseClassifier — TFLite-модель для распознавания болезней растений.
 *
 * Использует модель, обученную на PlantVillage + iNaturalist.
 * Файл модели: assets/disease_classifier.tflite
 */
class DiseaseClassifier(private val context: Context) {

    companion object {
        private const val MODEL_FILE      = "disease_classifier.tflite"
        private const val MAX_RESULTS     = 3
        private const val SCORE_THRESHOLD = 0.05f
        private const val INPUT_SIZE      = 224
    }

    private var classifier: ImageClassifier? = null

    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .build()

    fun setup() {
        val baseOptions = BaseOptions.builder().useGpu().build()
        val options = ImageClassifier.ImageClassifierOptions.builder()
            .setBaseOptions(baseOptions)
            .setMaxResults(MAX_RESULTS)
            .setScoreThreshold(SCORE_THRESHOLD)
            .build()
        try {
            classifier = ImageClassifier.createFromFileAndOptions(context, MODEL_FILE, options)
        } catch (e: Exception) {
            val cpuOptions = ImageClassifier.ImageClassifierOptions.builder()
                .setMaxResults(MAX_RESULTS)
                .setScoreThreshold(SCORE_THRESHOLD)
                .build()
            classifier = ImageClassifier.createFromFileAndOptions(context, MODEL_FILE, cpuOptions)
        }
    }

    fun classify(bitmap: Bitmap): List<DiseaseResult> {
        val tensorImage = TensorImage.fromBitmap(bitmap)
        val processed   = imageProcessor.process(tensorImage)
        val results: List<Classifications> = classifier?.classify(processed) ?: return emptyList()

        return results.flatMap { it.categories }
            .map { cat -> DiseaseResult(cat.label, cat.score) }
            .sortedByDescending { it.confidence }
    }

    fun close() {
        classifier?.close()
        classifier = null
    }

    data class DiseaseResult(
        val label: String,
        val confidence: Float
    ) {
        val confidencePercent: String get() = "%.1f%%".format(confidence * 100)
        val isHealthy: Boolean get() = label.contains("healthy", ignoreCase = true)
    }
}
