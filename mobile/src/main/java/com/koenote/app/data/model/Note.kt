package com.koenote.app.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "無題のノート",
    val transcript: String = "",
    val memo: String = "",
    val audioPath: String? = null,
    val source: String = "mobile", // "mobile" or "wear"
    val durationSeconds: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val notionPageId: String? = null,
) {
    fun toMarkdown(): String = buildString {
        appendLine("# $title")
        appendLine()
        if (memo.isNotBlank()) {
            appendLine("## メモ")
            appendLine(memo)
            appendLine()
        }
        appendLine("## 文字起こし")
        appendLine(transcript.ifBlank { "（文字起こしなし）" })
    }
}

@Serializable
enum class AsrProvider(val label: String) {
    OPENAI("OpenAI Whisper"),
    DEEPGRAM("Deepgram Nova-2"),
    GROQ("Groq Whisper"),
}

@Serializable
data class AppSettings(
    val asrProvider: AsrProvider = AsrProvider.OPENAI,
    val openAiApiKey: String = "",
    val deepgramApiKey: String = "",
    val groqApiKey: String = "",
    val asrLanguage: String = "ja",
    val notionToken: String = "",
    val notionDatabaseId: String = "",
    val deleteAudioAfterTranscribe: Boolean = false,
)
