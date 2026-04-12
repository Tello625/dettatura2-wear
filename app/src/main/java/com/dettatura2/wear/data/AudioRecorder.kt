package com.dettatura2.wear.data

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class AudioRecorder(private val context: Context) {
    
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentFilePath: String? = null
    private var startTime: Long = 0
    
    fun startRecording(): Boolean {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "dettatura_$timestamp.m4a"
            val file = File(context.filesDir, fileName)
            currentFilePath = file.absolutePath
            
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(currentFilePath)
                prepare()
                start()
            }
            
            startTime = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    fun stopRecording(): Recording? {
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            
            val duration = ((System.currentTimeMillis() - startTime) / 1000).toInt()
            val filePath = currentFilePath ?: return null
            
            Recording(
                id = UUID.randomUUID().toString(),
                filePath = filePath,
                duration = duration,
                createdAt = System.currentTimeMillis(),
                synced = false
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun playRecording(filePath: String, onComplete: () -> Unit) {
        try {
            stopPlayback()
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                setOnCompletionListener {
                    onComplete()
                }
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete()
        }
    }
    
    fun stopPlayback() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
    }
    
    fun release() {
        stopPlayback()
        mediaRecorder?.release()
        mediaRecorder = null
    }
}

data class Recording(
    val id: String,
    val filePath: String,
    val duration: Int,
    val createdAt: Long,
    val synced: Boolean,
    val transcription: String = ""  // Trascrizione automatica
) {
    val displayName: String
        get() {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            return "Voce ${sdf.format(Date(createdAt))}"
        }
    
    val fileName: String
        get() = File(filePath).name
    
    // Copia con trascrizione aggiornata
    fun withTranscription(text: String): Recording {
        return Recording(id, filePath, duration, createdAt, synced, text)
    }
}
