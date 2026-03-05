package com.dc.murmur.core.util

/**
 * Strips common markdown formatting from text so it displays cleanly as plain text.
 */
fun stripMarkdown(text: String): String {
    if (text.isBlank()) return text
    return text
        // Remove headers: # ## ### etc.
        .replace(Regex("""^#{1,6}\s+""", RegexOption.MULTILINE), "")
        // Remove bold: **text** or __text__
        .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
        .replace(Regex("""__(.+?)__"""), "$1")
        // Remove italic: *text* or _text_ (single)
        .replace(Regex("""\*(.+?)\*"""), "$1")
        .replace(Regex("""(?<!\w)_(.+?)_(?!\w)"""), "$1")
        // Remove strikethrough: ~~text~~
        .replace(Regex("""~~(.+?)~~"""), "$1")
        // Remove inline code: `code`
        .replace(Regex("""`(.+?)`"""), "$1")
        // Remove code blocks: ```...```
        .replace(Regex("""```[\s\S]*?```"""), "")
        // Remove bullet markers: - or * at line start
        .replace(Regex("""^[\s]*[-*+]\s+""", RegexOption.MULTILINE), "")
        // Remove numbered list markers: 1. 2. etc.
        .replace(Regex("""^[\s]*\d+\.\s+""", RegexOption.MULTILINE), "")
        // Remove links: [text](url) -> text
        .replace(Regex("""\[(.+?)]\(.+?\)"""), "$1")
        // Remove images: ![alt](url)
        .replace(Regex("""!\[.*?]\(.+?\)"""), "")
        // Remove blockquotes: > at line start
        .replace(Regex("""^>\s*""", RegexOption.MULTILINE), "")
        // Remove horizontal rules: --- or *** or ___
        .replace(Regex("""^[-*_]{3,}\s*$""", RegexOption.MULTILINE), "")
        // Collapse multiple blank lines
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
}
