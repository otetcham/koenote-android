package com.koenote.app.wear

import android.net.Uri
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.koenote.app.data.local.NoteRepository
import com.koenote.app.data.model.Note
import com.koenote.app.data.remote.AsrClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WearDataLayerListenerService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path != "/koenote/audio") return

        val recordingsDir = File(filesDir, "recordings").apply { mkdirs() }
        val targetFile = File(recordingsDir, "wear_${System.currentTimeMillis()}.m4a")
        val channelClient = Wearable.getChannelClient(this)

        channelClient.receiveFile(channel, Uri.fromFile(targetFile), false)
            .addOnSuccessListener {
                channelClient.close(channel)
                serviceScope.launch {
                    handleIncomingWearAudio(targetFile)
                }
            }
            .addOnFailureListener {
                channelClient.close(channel)
            }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/koenote/ping") {
            Wearable.getMessageClient(this)
                .sendMessage(messageEvent.sourceNodeId, "/koenote/pong", ByteArray(0))
        }
    }

    private suspend fun handleIncomingWearAudio(audioFile: File) {
        val repository = NoteRepository(applicationContext)
        val settings = repository.settings.value
        val timestampLabel = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date())

        val initialNote = Note(
            title = "Wear OS メモ ($timestampLabel)",
            audioPath = audioFile.absolutePath,
            source = "wear",
        )
        repository.upsertNote(initialNote)

        // Auto-transcribe if an API key is configured for the selected provider
        val asrClient = AsrClient()
        asrClient.transcribe(audioFile, settings).onSuccess { text ->
            repository.upsertNote(
                initialNote.copy(
                    transcript = text,
                    audioPath = if (settings.deleteAudioAfterTranscribe) null else initialNote.audioPath,
                )
            )
            if (settings.deleteAudioAfterTranscribe) {
                runCatching { audioFile.delete() }
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
