package com.koenote.app.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

class WearRecordingService : Service() {
    companion object {
        const val ACTION_START = "com.koenote.app.wear.START"
        const val ACTION_STOP = "com.koenote.app.wear.STOP"
        private const val CHANNEL_ID = "koenote_wear_rec"
        private const val NOTIFICATION_ID = 2001

        private val _isRecording = MutableStateFlow(false)
        val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

        private val _statusText = MutableStateFlow("待機中")
        val statusText: StateFlow<String> = _statusText.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, WearRecordingService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WearRecordingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopAndSend()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (_isRecording.value) return
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val dir = File(filesDir, "wear_recordings").apply { mkdirs() }
        val file = File(dir, "wear_${System.currentTimeMillis()}.m4a")
        outputFile = file

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
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mr
            _isRecording.value = true
            _statusText.value = "録音中..."
        }.onFailure {
            mr.release()
            _isRecording.value = false
            _statusText.value = "録音開始エラー"
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopAndSend() {
        if (!_isRecording.value) {
            stopSelf()
            return
        }
        runCatching {
            recorder?.apply {
                stop()
                release()
            }
        }
        recorder = null
        _isRecording.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)

        val file = outputFile
        outputFile = null
        if (file == null || !file.exists() || file.length() == 0L) {
            _statusText.value = "録音データなし"
            stopSelf()
            return
        }

        _statusText.value = "スマホへ送信中..."
        serviceScope.launch {
            sendAudioToPhone(file)
            stopSelf()
        }
    }

    private suspend fun sendAudioToPhone(file: File) {
        runCatching {
            val nodes = Wearable.getNodeClient(this).connectedNodes.await()
            val targetNode = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
            if (targetNode == null) {
                _statusText.value = "スマホ未接続"
                return
            }
            val channelClient = Wearable.getChannelClient(this)
            val channel = channelClient.openChannel(targetNode.id, "/koenote/audio").await()
            channelClient.sendFile(channel, Uri.fromFile(file)).await()
            file.delete()
            _statusText.value = "送信完了"
        }.onFailure {
            _statusText.value = "送信失敗"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "KoeNote Wear 録音",
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("KoeNote")
            .setContentText("Wear OS で録音中")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        runCatching { recorder?.release() }
        recorder = null
        _isRecording.value = false
        serviceScope.cancel()
        super.onDestroy()
    }
}
