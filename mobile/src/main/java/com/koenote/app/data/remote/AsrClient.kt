package com.koenote.app.data.remote

import com.koenote.app.data.model.AppSettings
import com.koenote.app.data.model.AsrProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class AsrClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun transcribe(audioFile: File, settings: AppSettings): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(audioFile.exists() && audioFile.length() > 0) {
                "音声ファイルが見つかりません"
            }
            when (settings.asrProvider) {
                AsrProvider.OPENAI -> transcribeOpenAiCompatible(
                    url = "https://api.openai.com/v1/audio/transcriptions",
                    apiKey = settings.openAiApiKey,
                    model = "whisper-1",
                    audioFile = audioFile,
                    language = settings.asrLanguage,
                )
                AsrProvider.GROQ -> transcribeOpenAiCompatible(
                    url = "https://api.groq.com/openai/v1/audio/transcriptions",
                    apiKey = settings.groqApiKey,
                    model = "whisper-large-v3-turbo",
                    audioFile = audioFile,
                    language = settings.asrLanguage,
                )
                AsrProvider.DEEPGRAM -> transcribeDeepgram(
                    apiKey = settings.deepgramApiKey,
                    audioFile = audioFile,
                    language = settings.asrLanguage,
                )
            }
        }
    }

    private fun transcribeOpenAiCompatible(
        url: String,
        apiKey: String,
        model: String,
        audioFile: File,
        language: String,
    ): String {
        require(apiKey.isNotBlank()) { "APIキーが設定されていません" }
        val mime = if (audioFile.extension.lowercase() == "wav") "audio/wav" else "audio/mp4"
        val bodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", audioFile.name, audioFile.asRequestBody(mime.toMediaType()))
            .addFormDataPart("model", model)
        if (language.isNotBlank()) {
            bodyBuilder.addFormDataPart("language", language)
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(bodyBuilder.build())
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("ASR エラー (${response.code}): $raw")
            }
            val root = json.parseToJsonElement(raw).jsonObject
            return root["text"]?.jsonPrimitive?.content.orEmpty().trim()
        }
    }

    private fun transcribeDeepgram(
        apiKey: String,
        audioFile: File,
        language: String,
    ): String {
        require(apiKey.isNotBlank()) { "Deepgram APIキーが設定されていません" }
        val langParam = if (language.isNotBlank()) "&language=$language" else ""
        val url = "https://api.deepgram.com/v1/listen?model=nova-2&smart_format=true$langParam"
        val mime = if (audioFile.extension.lowercase() == "wav") "audio/wav" else "audio/mp4"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Token $apiKey")
            .post(audioFile.asRequestBody(mime.toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Deepgram エラー (${response.code}): $raw")
            }
            val root = json.parseToJsonElement(raw).jsonObject
            val transcript = root["results"]?.jsonObject
                ?.get("channels")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("alternatives")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("transcript")?.jsonPrimitive?.content
            return transcript.orEmpty().trim()
        }
    }
}
