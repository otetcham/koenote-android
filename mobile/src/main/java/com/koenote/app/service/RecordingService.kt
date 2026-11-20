package com.koenote.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class RecordingService : Service() {
    companion object {
        const val ACTION_START = "com.koenote.app.action.START_RECORDING"
        const val ACTION_STOP = "com.koenote.app.action.STOP_RECORDING"
        private const val CHANNEL_ID = "koenote_recording"
        private const val NOTIFICATION_ID = 1001

        private val _isRecording = MutableStateFlow(false)
        val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

        private val _lastRecordedFile = MutableStateFlow<Pair<String, Long>?>(null)
        val lastRecordedFile: StateFlow<Pair<String, Long>?> = _lastRecordedFile.asStateFlow()

        fun consumeLastRecordedFile() {
            _lastRecordedFile.value = null
        }

        fun start(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMs: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (_isRecording.value) return
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val recordingsDir = File(filesDir, "recordings").apply { mkdirs() }
        val file = File(recordingsDir, "rec_${System.currentTimeMillis()}.m4a")
        outputFile = file
        startTimeMs = System.currentTimeMillis()

        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        runCatching {
            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mr
            _isRecording.value = true
        }.onFailure {
            mr.release()
            _isRecording.value = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopRecording() {
        if (!_isRecording.value) {
            stopSelf()
            return
        }
        val durationSec = ((System.currentTimeMillis() - startTimeMs) / 1000L).coerceAtLeast(1L)
        runCatching {
            recorder?.apply {
                stop()
                release()
            }
        }
        recorder = null
        _isRecording.value = false

        outputFile?.let { file ->
            if (file.exists() && file.length() > 0) {
                _lastRecordedFile.value = file.absolutePath to durationSec
            }
        }
        outputFile = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "KoeNote 録音",
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("KoeNote 録音中")
            .setContentText("マイク音声を録音しています")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        runCatching { recorder?.release() }
        recorder = null
        _isRecording.value = false
        super.onDestroy()
    }
}
