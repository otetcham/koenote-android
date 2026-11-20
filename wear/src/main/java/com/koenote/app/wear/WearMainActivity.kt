package com.koenote.app.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText

class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val isRecording by WearRecordingService.isRecording.collectAsState()
                val statusText by WearRecordingService.statusText.collectAsState()

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { grants ->
                    if (grants[Manifest.permission.RECORD_AUDIO] == true) {
                        WearRecordingService.start(this)
                    }
                }

                Scaffold(
                    timeText = { TimeText() },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "KoeNote",
                            style = MaterialTheme.typography.title3,
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                if (isRecording) {
                                    WearRecordingService.stop(this@WearMainActivity)
                                } else {
                                    val needed = buildList {
                                        add(Manifest.permission.RECORD_AUDIO)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            add(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }
                                    val granted = needed.all {
                                        ContextCompat.checkSelfPermission(
                                            this@WearMainActivity,
                                            it,
                                        ) == PackageManager.PERMISSION_GRANTED
                                    }
                                    if (granted) {
                                        WearRecordingService.start(this@WearMainActivity)
                                    } else {
                                        permissionLauncher.launch(needed.toTypedArray())
                                    }
                                }
                            },
                            colors = if (isRecording) {
                                ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error)
                            } else {
                                ButtonDefaults.primaryButtonColors()
                            },
                            modifier = Modifier.size(64.dp),
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = if (isRecording) "録音停止" else "録音開始",
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.caption2,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
