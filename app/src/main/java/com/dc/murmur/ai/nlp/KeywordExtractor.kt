package com.dc.murmur.ai.nlp

import org.json.JSONArray

class KeywordExtractor {

    companion object {
        private val STOP_WORDS = setOf(
            // English
            "a", "an", "the", "is", "it", "in", "on", "at", "to", "for",
            "of", "and", "or", "but", "not", "with", "this", "that", "was",
            "are", "were", "been", "be", "have", "has", "had", "do", "does",
            "did", "will", "would", "could", "should", "may", "might", "can",
            "shall", "i", "me", "my", "you", "your", "he", "she", "we",
            "they", "them", "his", "her", "its", "our", "their", "what",
            "which", "who", "whom", "when", "where", "why", "how", "all",
            "each", "every", "both", "few", "more", "most", "other", "some",
            "such", "no", "nor", "only", "own", "same", "so", "than", "too",
            "very", "just", "because", "as", "until", "while", "about",
            "between", "through", "during", "before", "after", "above",
            "below", "from", "up", "down", "out", "off", "over", "under",
            "again", "further", "then", "once", "here", "there", "if",
            "um", "uh", "like", "yeah", "okay", "right", "well", "know",
            "think", "going", "got", "get", "go", "come", "take", "make",
            "say", "said", "also", "one", "two", "thing", "things",
            // Hindi (transliterated common stop words)
            "hai", "hain", "ka", "ki", "ke", "ko", "se", "me", "mein",
            "par", "pe", "ye", "yeh", "wo", "woh", "jo", "aur", "ya",
            "bhi", "nahi", "nhi", "na", "toh", "to", "tha", "thi", "the",
            "hum", "tum", "aap", "mai", "main", "mera", "meri", "mere",
            "tera", "teri", "tere", "uska", "uski", "unka", "unki",
            "kya", "kab", "kaha", "kaise", "kyun", "kyon", "kaun",
            "ek", "do", "koi", "kuch", "sab", "bahut", "bohot",
            "wala", "wali", "wale", "kar", "karna", "karte", "karti",
            "haan", "ji", "achha", "hmm"
        )

        private val TIME_PATTERN = Regex("\\b\\d{1,2}:\\d{2}\\s*(am|pm|AM|PM)?\\b")
        private val DATE_PATTERN = Regex("\\b\\d{1,2}[/-]\\d{1,2}([/-]\\d{2,4})?\\b")
        private val NUMBER_PATTERN = Regex("\\b\\d+\\.?\\d*%?\\b")
    }

    fun extract(text: String, maxKeywords: Int = 10): List<String> {
        if (text.isBlank()) return emptyList()

        val keywords = mutableListOf<String>()

        // Extract special patterns first
        TIME_PATTERN.findAll(text).forEach { keywords.add(it.value) }
        DATE_PATTERN.findAll(text).forEach { keywords.add(it.value) }

        // Tokenize and compute TF scores
        val words = text.lowercase()
            .split(Regex("[\\s,.!?;:\"'()\\[\\]{}]+"))
            .filter { it.length > 2 && it !in STOP_WORDS && !it.all { c -> c.isDigit() } }

        val tf = mutableMapOf<String, Int>()
        for (word in words) {
            tf[word] = (tf[word] ?: 0) + 1
        }

        // Extract bigrams
        val bigrams = mutableMapOf<String, Int>()
        for (i in 0 until words.size - 1) {
            val bigram = "${words[i]} ${words[i + 1]}"
            if (words[i] !in STOP_WORDS && words[i + 1] !in STOP_WORDS) {
                bigrams[bigram] = (bigrams[bigram] ?: 0) + 1
            }
        }

        // Combine: single words by TF, then bigrams
        val ranked = tf.entries.sortedByDescending { it.value }
            .map { it.key }
            .take(maxKeywords)

        val rankedBigrams = bigrams.entries
            .filter { it.value >= 2 }
            .sortedByDescending { it.value }
            .map { it.key }
            .take(3)

        keywords.addAll(rankedBigrams)
        keywords.addAll(ranked)

        return keywords.distinct().take(maxKeywords)
    }

    fun toJson(keywords: List<String>): String {
        val array = JSONArray()
        keywords.forEach { array.put(it) }
        return array.toString()
    }

    fun fromJson(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { array.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Parses the Claude summary from the keywords column.
     * Handles the new object format {"summary":"...","tags":[...]}
     * and returns empty string for the old plain-array format.
     */
    fun parseSummary(json: String): String {
        if (json.isBlank()) return ""
        return try {
            org.json.JSONObject(json).optString("summary", "")
        } catch (e: Exception) {
            "" // old plain-array format — no summary field
        }
    }

    /**
     * Parses tags from either format:
     *   new: {"summary":"...","tags":["tag1","tag2"]}
     *   old: ["tag1","tag2"]
     */
    fun parseTags(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        return try {
            val obj = org.json.JSONObject(json)
            val arr = obj.optJSONArray("tags") ?: return emptyList()
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            // Old plain-array format
            fromJson(json)
        }
    }
}
