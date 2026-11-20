package com.koenote.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.koenote.app.data.local.NoteRepository
import com.koenote.app.data.model.Note
import com.koenote.app.data.remote.AsrClient
import com.koenote.app.data.remote.NotionClient
import com.koenote.app.service.RecordingService
import com.koenote.app.ui.screens.HomeScreen
import com.koenote.app.ui.screens.NoteDetailScreen
import com.koenote.app.ui.screens.SettingsScreen
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = NoteRepository(applicationContext)
        val asrClient = AsrClient()
        val notionClient = NotionClient()

        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                val notes by repository.notes.collectAsState()
                val settings by repository.settings.collectAsState()
                val isRecording by RecordingService.isRecording.collectAsState()
                val lastRecorded by RecordingService.lastRecordedFile.collectAsState()
                var isBusy by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { grants ->
                    if (grants[Manifest.permission.RECORD_AUDIO] == true) {
                        RecordingService.start(this)
                    } else {
                        Toast.makeText(this, "マイク権限が必要です", Toast.LENGTH_SHORT).show()
                    }
                }

                LaunchedEffect(lastRecorded) {
                    val recorded = lastRecorded ?: return@LaunchedEffect
                    RecordingService.consumeLastRecordedFile()
                    val (path, durationSec) = recorded
                    val label = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date())
                    val newNote = Note(
                        title = "音声メモ ($label)",
                        audioPath = path,
                        durationSeconds = durationSec,
                        source = "mobile",
                    )
                    repository.upsertNote(newNote)
                    navController.navigate("detail/${newNote.id}")
                }

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            notes = notes,
                            isRecording = isRecording,
                            onToggleRecording = {
                                if (isRecording) {
                                    RecordingService.stop(this@MainActivity)
                                } else {
                                    val needed = buildList {
                                        add(Manifest.permission.RECORD_AUDIO)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            add(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }
                                    val allGranted = needed.all {
                                        ContextCompat.checkSelfPermission(
                                            this@MainActivity,
                                            it,
                                        ) == PackageManager.PERMISSION_GRANTED
                                    }
                                    if (allGranted) {
                                        RecordingService.start(this@MainActivity)
                                    } else {
                                        permissionLauncher.launch(needed.toTypedArray())
                                    }
                                }
                            },
                            onCreateEmptyNote = {
                                val newNote = Note(title = "新規メモ")
                                repository.upsertNote(newNote)
                                navController.navigate("detail/${newNote.id}")
                            },
                            onOpenNote = { id -> navController.navigate("detail/$id") },
                            onOpenSettings = { navController.navigate("settings") },
                        )
                    }

                    composable("detail/{noteId}") { backStackEntry ->
                        val noteId = backStackEntry.arguments?.getString("noteId") ?: return@composable
                        val note = notes.find { it.id == noteId } ?: return@composable

                        NoteDetailScreen(
                            note = note,
                            isBusy = isBusy,
                            onBack = { navController.popBackStack() },
                            onSave = { updated -> repository.upsertNote(updated) },
                            onTranscribe = { current ->
                                val path = current.audioPath ?: return@NoteDetailScreen
                                scope.launch {
                                    isBusy = true
                                    val result = asrClient.transcribe(File(path), settings)
                                    result.onSuccess { text ->
                                        val updated = current.copy(
                                            transcript = text,
                                            audioPath = if (settings.deleteAudioAfterTranscribe) null else current.audioPath,
                                        )
                                        repository.upsertNote(updated)
                                        if (settings.deleteAudioAfterTranscribe) {
                                            runCatching { File(path).delete() }
                                        }
                                        Toast.makeText(this@MainActivity, "文字起こしが完了しました", Toast.LENGTH_SHORT).show()
                                    }.onFailure { err ->
                                        Toast.makeText(this@MainActivity, err.message ?: "文字起こしに失敗しました", Toast.LENGTH_LONG).show()
                                    }
                                    isBusy = false
                                }
                            },
                            onSaveToNotion = { current ->
                                scope.launch {
                                    isBusy = true
                                    val result = notionClient.saveNoteToDatabase(current, settings)
                                    result.onSuccess { pageId ->
                                        repository.upsertNote(current.copy(notionPageId = pageId))
                                        Toast.makeText(this@MainActivity, "Notionに保存しました", Toast.LENGTH_SHORT).show()
                                    }.onFailure { err ->
                                        Toast.makeText(this@MainActivity, err.message ?: "Notion保存に失敗しました", Toast.LENGTH_LONG).show()
                                    }
                                    isBusy = false
                                }
                            },
                            onDelete = { id -> repository.deleteNote(id) },
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            settings = settings,
                            onSave = { updated ->
                                repository.saveSettings(updated)
                                Toast.makeText(this@MainActivity, "設定を保存しました", Toast.LENGTH_SHORT).show()
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
