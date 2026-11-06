package com.ghostmago.app.util

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Record and play voice notes / call chunks.
 */
class VoiceManager(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var currentFile: File? = null
    private var recording = false

    fun isRecording() = recording

    fun startRecording(): File? {
        if (recording) return null
        return tryStart(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            ?: tryStart(MediaRecorder.AudioSource.MIC)
    }

    private fun tryStart(source: Int): File? {
        return try {
            releaseRecorder()
            val dir = File(context.filesDir, "voice").apply { mkdirs() }
            val file = File(dir, "voice_${System.currentTimeMillis()}.m4a")
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            rec.setAudioSource(source)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioEncodingBitRate(48000)
            rec.setAudioSamplingRate(16000)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
            recorder = rec
            currentFile = file
            recording = true
            file
        } catch (e: Exception) {
            Log.e("VoiceManager", "Start failed source=$source", e)
            releaseRecorder()
            null
        }
    }

    fun stopRecording(): File? {
        if (!recording) return null
        return try {
            recorder?.apply {
                try { stop() } catch (_: Exception) {}
                try { release() } catch (_: Exception) {}
            }
            recorder = null
            recording = false
            val f = currentFile
            currentFile = null
            if (f != null && f.exists() && f.length() > 0) f else null
        } catch (e: Exception) {
            Log.e("VoiceManager", "Stop failed", e)
            releaseRecorder()
            null
        }
    }

    fun play(file: File, onComplete: (() -> Unit)? = null) {
        stopPlayback()
        try {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    onComplete?.invoke()
                    release()
                    player = null
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("VoiceManager", "Play failed", e)
        }
    }

    fun playBytes(data: ByteArray, onComplete: (() -> Unit)? = null) {
        try {
            val tmp = File(context.cacheDir, "play_${System.currentTimeMillis()}.m4a")
            tmp.writeBytes(data)
            play(tmp) {
                tmp.delete()
                onComplete?.invoke()
            }
        } catch (e: Exception) {
            Log.e("VoiceManager", "PlayBytes failed", e)
        }
    }

    fun stopPlayback() {
        try {
            player?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
        }
        player = null
    }

    private fun releaseRecorder() {
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        recording = false
        currentFile = null
    }

    fun release() {
        stopPlayback()
        if (recording) {
            try { stopRecording() } catch (_: Exception) {}
        }
        releaseRecorder()
    }
}
