package com.thehbc.bilimusic.utils

import com.thehbc.bilimusic.data.model.LyricLine

object LrcParser {
    /**
     * Parses a standard LRC format string into a list of [LyricLine].
     * Example of LRC line: "[03:20.50]Lyric text here"
     */
    fun parseLrc(lrcText: String): List<LyricLine> {
        val lines = lrcText.split("\n")
        val lyricsList = mutableListOf<LyricLine>()
        
        // Regex to match the timestamp format [mm:ss.xx] or [mm:ss.xxx]
        val timeRegex = Regex("\\[(\\d{2,}):(\\d{2})(?:\\.(\\d{2,3}))?]")

        for (line in lines) {
            val matchResults = timeRegex.findAll(line)
            val text = line.replace(timeRegex, "").trim()
            
            // Skip lines with no text or just metadata (like [ar:Artist], [ti:Title])
            // Standard lyrics usually have timestamps that match the regex.
            if (text.isEmpty() && matchResults.none()) continue
            if (matchResults.none()) continue

            for (match in matchResults) {
                val minutes = match.groups[1]?.value?.toLongOrNull() ?: 0L
                val seconds = match.groups[2]?.value?.toLongOrNull() ?: 0L
                // Milliseconds part could be 2 or 3 digits. Usually it's centiseconds (2 digits)
                val msString = match.groups[3]?.value ?: "0"
                val milliseconds = if (msString.length == 2) {
                    msString.toLongOrNull()?.times(10) ?: 0L
                } else {
                    msString.toLongOrNull() ?: 0L
                }

                val timestampMs = minutes * 60 * 1000 + seconds * 1000 + milliseconds
                lyricsList.add(LyricLine(timestampMs, text))
            }
        }
        
        // Sort by timestamp in case the LRC has out-of-order tags
        return lyricsList.sortedBy { it.timestampMs }
    }
}
