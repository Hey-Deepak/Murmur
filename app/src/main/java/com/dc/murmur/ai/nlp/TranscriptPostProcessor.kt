package com.dc.murmur.ai.nlp

class TranscriptPostProcessor {

    private val whisperArtifacts = listOf(
        "[BLANK_AUDIO]",
        "(blank audio)",
        "[NO_SPEECH]",
        "[MUSIC]",
        "(music)",
        "[NOISE]"
    )

    fun process(rawText: String): String {
        if (rawText.isBlank()) return rawText

        var text = rawText.trim()

        // Remove Whisper artifacts
        for (artifact in whisperArtifacts) {
            text = text.replace(artifact, "", ignoreCase = true)
        }

        // Collapse multiple spaces / newlines into single space
        text = text.replace(Regex("\\s+"), " ").trim()

        if (text.isBlank()) return ""

        // Capitalize first letter
        if (text.isNotEmpty() && text[0].isLowerCase()) {
            text = text[0].uppercase() + text.substring(1)
        }
        // Capitalize after sentence-ending punctuation
        text = text.replace(Regex("([.!?]\\s+)([a-z])")) { match ->
            match.groupValues[1] + match.groupValues[2].uppercase()
        }

        // Add terminal period if missing
        if (text.isNotBlank() && text.last() !in ".!?") {
            text = "$text."
        }

        return text
    }
}
