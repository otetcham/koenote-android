package com.koenote.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.koenote.app.data.model.AppSettings
import com.koenote.app.data.model.AsrProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSave: (AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    var provider by remember { mutableStateOf(settings.asrProvider) }
    var openAiKey by remember { mutableStateOf(settings.openAiApiKey) }
    var deepgramKey by remember { mutableStateOf(settings.deepgramApiKey) }
    var groqKey by remember { mutableStateOf(settings.groqApiKey) }
    var language by remember { mutableStateOf(settings.asrLanguage) }
    var notionToken by remember { mutableStateOf(settings.notionToken) }
    var notionDatabaseId by remember { mutableStateOf(settings.notionDatabaseId) }
    var deleteAudio by remember { mutableStateOf(settings.deleteAudioAfterTranscribe) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("音声認識 (ASR) プロバイダ", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AsrProvider.entries.forEach { item ->
                    FilterChip(
                        selected = provider == item,
                        onClick = { provider = item },
                        label = { Text(item.label) },
                    )
                }
            }

            OutlinedTextField(
                value = openAiKey,
                onValueChange = { openAiKey = it },
                label = { Text("OpenAI API Key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = deepgramKey,
                onValueChange = { deepgramKey = it },
                label = { Text("Deepgram API Key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = groqKey,
                onValueChange = { groqKey = it },
                label = { Text("Groq API Key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = language,
                onValueChange = { language = it },
                label = { Text("言語コード (例: ja, en)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("文字起こし後に音声ファイルを削除", modifier = Modifier.weight(1f))
                Switch(checked = deleteAudio, onCheckedChange = { deleteAudio = it })
            }

            Text("Notion 連携", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = notionToken,
                onValueChange = { notionToken = it },
                label = { Text("Notion Integration Token") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = notionDatabaseId,
                onValueChange = { notionDatabaseId = it },
                label = { Text("Notion Database ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Button(
                onClick = {
                    onSave(
                        AppSettings(
                            asrProvider = provider,
                            openAiApiKey = openAiKey,
                            deepgramApiKey = deepgramKey,
                            groqApiKey = groqKey,
                            asrLanguage = language,
                            notionToken = notionToken,
                            notionDatabaseId = notionDatabaseId,
                            deleteAudioAfterTranscribe = deleteAudio,
                        )
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("設定を保存")
            }
        }
    }
}
