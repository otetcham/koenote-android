package com.koenote.app.data.remote

import com.koenote.app.data.model.AppSettings
import com.koenote.app.data.model.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class NotionClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun saveNoteToDatabase(note: Note, settings: AppSettings): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(settings.notionToken.isNotBlank()) { "Notion Integration Token が未設定です" }
            require(settings.notionDatabaseId.isNotBlank()) { "Notion Database ID が未設定です" }

            val contentText = buildString {
                if (note.memo.isNotBlank()) {
                    appendLine("【メモ】")
                    appendLine(note.memo)
                    appendLine()
                }
                appendLine("【文字起こし】")
                append(note.transcript.ifBlank { "（なし）" })
            }.take(1900) // Notion rich_text block limit is 2000 chars

            val payload = buildJsonObject {
                put("parent", buildJsonObject {
                    put("database_id", settings.notionDatabaseId)
                })
                put("properties", buildJsonObject {
                    put("Name", buildJsonObject {
                        put("title", buildJsonArray {
                            add(buildJsonObject {
                                put("text", buildJsonObject {
                                    put("content", note.title.ifBlank { "無題のノート" })
                                })
                            })
                        })
                    })
                })
                put("children", buildJsonArray {
                    add(buildJsonObject {
                        put("object", "block")
                        put("type", "paragraph")
                        put("paragraph", buildJsonObject {
                            put("rich_text", buildJsonArray {
                                add(buildJsonObject {
                                    put("type", "text")
                                    put("text", buildJsonObject {
                                        put("content", contentText)
                                    })
                                })
                            })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url("https://api.notion.com/v1/pages")
                .header("Authorization", "Bearer ${settings.notionToken}")
                .header("Notion-Version", "2022-06-28")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("Notion 保存エラー (${response.code}): $raw")
                }
                val root = json.parseToJsonElement(raw).jsonObject
                root["id"]?.jsonPrimitive?.content ?: ""
            }
        }
    }
}
