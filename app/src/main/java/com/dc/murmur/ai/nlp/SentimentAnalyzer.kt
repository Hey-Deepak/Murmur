package com.dc.murmur.ai.nlp

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.task.text.nlclassifier.NLClassifier
import java.io.File

class SentimentAnalyzer(private val context: Context) {

    data class SentimentResult(
        val sentiment: String,   // "positive", "negative", "neutral"
        val score: Float         // 0.0 – 1.0 confidence
    )

    private var classifier: NLClassifier? = null

    suspend fun initialize(modelPath: String) = withContext(Dispatchers.IO) {
        if (classifier != null) return@withContext

        val modelFile = File(modelPath)
        if (modelFile.exists() && modelFile.length() > 100) {
            try {
                classifier = NLClassifier.createFromFile(modelFile)
            } catch (e: Exception) {
                // Model file invalid — fall back to rule-based
                classifier = null
            }
        }
    }

    fun analyze(text: String): SentimentResult {
        if (text.isBlank()) return SentimentResult("neutral", 0.5f)

        classifier?.let { nlClassifier ->
            return try {
                val results = nlClassifier.classify(text)
                if (results.isEmpty()) return fallbackAnalyze(text)

                var bestLabel = "neutral"
                var bestScore = 0f

                for (category in results) {
                    if (category.score > bestScore) {
                        bestScore = category.score
                        bestLabel = when {
                            category.label.lowercase().contains("pos") -> "positive"
                            category.label.lowercase().contains("neg") -> "negative"
                            else -> "neutral"
                        }
                    }
                }

                SentimentResult(bestLabel, bestScore)
            } catch (e: Exception) {
                fallbackAnalyze(text)
            }
        }

        return fallbackAnalyze(text)
    }

    private fun fallbackAnalyze(text: String): SentimentResult {
        val lower = text.lowercase()
        val positiveWords = setOf(
            // English
            "good", "great", "happy", "love", "nice", "wonderful", "excellent",
            "amazing", "awesome", "fantastic", "beautiful", "perfect", "best",
            "thank", "thanks", "appreciate", "enjoy", "glad", "pleased", "fun",
            // Hindi (transliterated)
            "accha", "achha", "acha", "badhiya", "khushi", "khush", "pyaar",
            "sundar", "shukriya", "dhanyavaad", "maza", "zabardast", "shandar",
            "bahut", "pasand", "mast", "sahi", "theek"
        )
        val negativeWords = setOf(
            // English
            "bad", "terrible", "hate", "awful", "horrible", "worst", "ugly",
            "angry", "sad", "disappointed", "annoyed", "frustrated", "upset",
            "wrong", "problem", "issue", "fail", "broken", "hurt", "pain",
            // Hindi (transliterated)
            "bura", "kharab", "nafrat", "gussa", "dukh", "dukhi", "pareshan",
            "takleef", "galat", "mushkil", "dikkat", "tut", "toota", "dard",
            "chinta", "dar", "bimar", "bekar"
        )

        val words = lower.split(Regex("\\W+"))
        val posCount = words.count { it in positiveWords }
        val negCount = words.count { it in negativeWords }
        val total = posCount + negCount

        return when {
            total == 0 -> SentimentResult("neutral", 0.5f)
            posCount > negCount -> SentimentResult("positive", posCount.toFloat() / total)
            negCount > posCount -> SentimentResult("negative", negCount.toFloat() / total)
            else -> SentimentResult("neutral", 0.5f)
        }
    }

    fun close() {
        classifier?.close()
        classifier = null
    }
}
