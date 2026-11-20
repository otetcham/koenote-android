package com.koenote.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.koenote.app.data.model.Note
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    note: Note,
    isBusy: Boolean,
    onBack: () -> Unit,
    onSave: (Note) -> Unit,
    onTranscribe: (Note) -> Unit,
    onSaveToNotion: (Note) -> Unit,
    onDelete: (String) -> Unit,
) {
    var title by remember(note.id) { mutableStateOf(note.title) }
    var memo by remember(note.id) { mutableStateOf(note.memo) }
    var transcript by remember(note.id, note.transcript) { mutableStateOf(note.transcript) }

    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun currentEdited(): Note = note.copy(
        title = title,
        memo = memo,
        transcript = transcript,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ノート編集") },
                navigationIcon = {
                    IconButton(onClick = {
                        onSave(currentEdited())
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(currentEdited().toMarkdown()))
                        scope.launch { snackbarHostState.showSnackbar("Markdownをコピーしました") }
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Markdownコピー")
                    }
                    IconButton(onClick = {
                        onDelete(note.id)
                        onBack()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "削除")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("タイトル") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (note.audioPath != null) {
                    Button(
                        onClick = { onTranscribe(currentEdited()) },
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Translate, contentDescription = null)
                        Spacer(modifier = Modifier.padding(4.dp))
                        Text("文字起こし")
                    }
                }
                OutlinedButton(
                    onClick = { onSaveToNotion(currentEdited()) },
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text("Notion保存")
                }
            }

            if (isBusy) {
                CircularProgressIndicator()
            }

            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it },
                label = { Text("手入力メモ") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            )

            OutlinedTextField(
                value = transcript,
                onValueChange = { transcript = it },
                label = { Text("文字起こし本文") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
            )

            Button(
                onClick = {
                    onSave(currentEdited())
                    scope.launch { snackbarHostState.showSnackbar("保存しました") }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("変更を保存")
            }
        }
    }
}
