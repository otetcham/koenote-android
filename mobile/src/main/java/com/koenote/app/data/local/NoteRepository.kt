package com.koenote.app.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.koenote.app.data.model.AppSettings
import com.koenote.app.data.model.AsrProvider
import com.koenote.app.data.model.Note
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class NoteRepository(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val notesFile: File
        get() = File(context.filesDir, "koenote_notes.json")

    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "koenote_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _notes = MutableStateFlow<List<Note>>(loadNotes())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadNotes(): List<Note> {
        if (!notesFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<Note>>(notesFile.readText())
        }.getOrDefault(emptyList())
            .sortedByDescending { it.createdAt }
    }

    private fun persistNotes(list: List<Note>) {
        notesFile.writeText(json.encodeToString(list))
        _notes.value = list.sortedByDescending { it.createdAt }
    }

    fun upsertNote(note: Note) {
        val current = _notes.value.toMutableList()
        val idx = current.indexOfFirst { it.id == note.id }
        val updated = note.copy(updatedAt = System.currentTimeMillis())
        if (idx >= 0) {
            current[idx] = updated
        } else {
            current.add(0, updated)
        }
        persistNotes(current)
    }

    fun deleteNote(id: String) {
        val target = _notes.value.find { it.id == id }
        target?.audioPath?.let { path ->
            runCatching { File(path).delete() }
        }
        persistNotes(_notes.value.filterNot { it.id == id })
    }

    fun getNote(id: String): Note? = _notes.value.find { it.id == id }

    private fun loadSettings(): AppSettings {
        val providerName = prefs.getString("asr_provider", AsrProvider.OPENAI.name) ?: AsrProvider.OPENAI.name
        val provider = runCatching { AsrProvider.valueOf(providerName) }.getOrDefault(AsrProvider.OPENAI)
        return AppSettings(
            asrProvider = provider,
            openAiApiKey = prefs.getString("openai_key", "") ?: "",
            deepgramApiKey = prefs.getString("deepgram_key", "") ?: "",
            groqApiKey = prefs.getString("groq_key", "") ?: "",
            asrLanguage = prefs.getString("asr_language", "ja") ?: "ja",
            notionToken = prefs.getString("notion_token", "") ?: "",
            notionDatabaseId = prefs.getString("notion_db_id", "") ?: "",
            deleteAudioAfterTranscribe = prefs.getBoolean("delete_audio_after_transcribe", false),
        )
    }

    fun saveSettings(newSettings: AppSettings) {
        prefs.edit()
            .putString("asr_provider", newSettings.asrProvider.name)
            .putString("openai_key", newSettings.openAiApiKey.trim())
            .putString("deepgram_key", newSettings.deepgramApiKey.trim())
            .putString("groq_key", newSettings.groqApiKey.trim())
            .putString("asr_language", newSettings.asrLanguage.trim())
            .putString("notion_token", newSettings.notionToken.trim())
            .putString("notion_db_id", newSettings.notionDatabaseId.trim())
            .putBoolean("delete_audio_after_transcribe", newSettings.deleteAudioAfterTranscribe)
            .apply()
        _settings.value = newSettings
    }
}
