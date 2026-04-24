package com.example.plantvisreborn

import android.content.Context
import org.json.JSONObject

/**
 * DiseaseAdvisor — загружает disease_info.json из assets и предоставляет
 * рекомендации по лечению и профилактике болезней растений.
 */
class DiseaseAdvisor(private val context: Context) {

    data class DiseaseInfo(
        val folder:     String,
        val nameRu:     String,
        val nameEn:     String,
        val plant:      String,
        val pathogen:   String,
        val severity:   Severity,
        val spread:     Spread,
        val symptoms:   List<String>,
        val causes:     List<String>,
        val treatment:  List<String>,
        val prevention: List<String>,
    ) {
        val isHealthy: Boolean get() = pathogen == "none"
    }

    enum class Severity(val ru: String, val emoji: String) {
        NONE    ("Нет угрозы",   "✅"),
        LOW     ("Низкая",       "🟡"),
        MEDIUM  ("Средняя",      "🟠"),
        HIGH    ("Высокая",      "🔴"),
        CRITICAL("Критическая",  "🚨");
    }

    enum class Spread(val ru: String) {
        NONE  ("Не заразна"),
        LOW   ("Низкая"),
        MEDIUM("Средняя"),
        HIGH  ("Высокая");
    }

    // Карта патогенов на русский язык
    private val pathogenNames = mapOf(
        "none"          to "Здоровое растение",
        "fungal"        to "Грибковое заболевание",
        "bacterial"     to "Бактериальное заболевание",
        "viral"         to "Вирусное заболевание",
        "oomycete"      to "Оомицет (ложный гриб)",
        "pest"          to "Вредитель (насекомое/клещ)",
        "physiological" to "Физиологическое нарушение",
    )

    private var db: Map<String, DiseaseInfo> = emptyMap()
    private var plantMap: Map<String, List<String>> = emptyMap()

    // ── Загрузка ─────────────────────────────────────────────────────────

    fun load() {
        db       = loadDiseaseInfo()
        plantMap = loadPlantMap()
    }

    private fun loadDiseaseInfo(): Map<String, DiseaseInfo> {
        val result = mutableMapOf<String, DiseaseInfo>()
        return try {
            val json = context.assets.open("disease_info.json")
                .bufferedReader().readText()
            val root = JSONObject(json)
            val keys = root.keys()
            while (keys.hasNext()) {
                val key  = keys.next()
                val obj  = root.getJSONObject(key)
                result[key] = DiseaseInfo(
                    folder     = key,
                    nameRu     = obj.optString("ru", key),
                    nameEn     = obj.optString("en", key),
                    plant      = obj.optString("plant", ""),
                    pathogen   = obj.optString("pathogen", "none"),
                    severity   = parseSeverity(obj.optString("severity", "none")),
                    spread     = parseSpread(obj.optString("spread", "none")),
                    symptoms   = jsonArrayToList(obj.optJSONArray("symptoms")),
                    causes     = jsonArrayToList(obj.optJSONArray("causes")),
                    treatment  = jsonArrayToList(obj.optJSONArray("treatment")),
                    prevention = jsonArrayToList(obj.optJSONArray("prevention")),
                )
            }
            result
        } catch (e: Exception) {
            // Если файл ещё не добавлен в assets — вернуть пустую карту
            emptyMap()
        }
    }

    private fun loadPlantMap(): Map<String, List<String>> {
        return try {
            val json = context.assets.open("plant_disease_map.json")
                .bufferedReader().readText()
            val root = JSONObject(json)
            val map  = mutableMapOf<String, List<String>>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = jsonArrayToList(root.optJSONArray(key))
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // ── Публичный API ────────────────────────────────────────────────────

    /** Получить полную информацию о болезни по метке класса. */
    fun getInfo(label: String): DiseaseInfo? = db[label]

    /** Получить русское название патогена. */
    fun getPathogenName(pathogen: String): String =
        pathogenNames[pathogen] ?: pathogen

    /** Список болезней для конкретного растения. */
    fun getDiseasesForPlant(plantFolder: String): List<DiseaseInfo> {
        val diseaseKeys = plantMap[plantFolder] ?: return emptyList()
        return diseaseKeys.mapNotNull { db[it] }
    }

    /**
     * Сформировать краткое предупреждение об опасности.
     * Используется на карточке результата.
     */
    fun getUrgencyMessage(info: DiseaseInfo): String {
        if (info.isHealthy) return "Растение здорово"
        return when (info.severity) {
            Severity.CRITICAL -> "⚠️ Требуется срочное лечение!"
            Severity.HIGH     -> "Необходима обработка в течение 1–2 дней"
            Severity.MEDIUM   -> "Рекомендуется обработка в ближайшие дни"
            Severity.LOW      -> "Наблюдайте за растением"
            Severity.NONE     -> ""
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun parseSeverity(s: String) = when (s) {
        "low"      -> Severity.LOW
        "medium"   -> Severity.MEDIUM
        "high"     -> Severity.HIGH
        "critical" -> Severity.CRITICAL
        else       -> Severity.NONE
    }

    private fun parseSpread(s: String) = when (s) {
        "low"    -> Spread.LOW
        "medium" -> Spread.MEDIUM
        "high"   -> Spread.HIGH
        else     -> Spread.NONE
    }

    private fun jsonArrayToList(arr: org.json.JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.getString(it) }
    }
}
