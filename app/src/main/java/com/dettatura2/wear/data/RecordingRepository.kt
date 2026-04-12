package com.dettatura2.wear.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class RecordingRepository(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("recordings", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    fun getRecordings(): List<Recording> {
        val json = prefs.getString("recordings_list", "[]")
        val type = object : TypeToken<List<Recording>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
    
    fun saveRecording(recording: Recording) {
        val recordings = getRecordings().toMutableList()
        recordings.add(recording)
        
        // Mantieni solo le ultime 20 registrazioni
        while (recordings.size > 20) {
            val oldest = recordings.removeAt(0)
            // Elimina anche il file
            File(oldest.filePath).delete()
        }
        
        val json = gson.toJson(recordings)
        prefs.edit().putString("recordings_list", json).apply()
    }
    
    fun deleteRecording(recording: Recording) {
        val recordings = getRecordings().toMutableList()
        recordings.removeAll { it.id == recording.id }
        
        // Elimina il file
        File(recording.filePath).delete()
        
        val json = gson.toJson(recordings)
        prefs.edit().putString("recordings_list", json).apply()
    }
    
    fun markAsSynced(recordingId: String) {
        val recordings = getRecordings().map {
            if (it.id == recordingId) it.copy(synced = true) else it
        }
        val json = gson.toJson(recordings)
        prefs.edit().putString("recordings_list", json).apply()
    }
    
    fun updateTranscription(recordingId: String, transcription: String) {
        val recordings = getRecordings().map {
            if (it.id == recordingId) it.withTranscription(transcription) else it
        }
        val json = gson.toJson(recordings)
        prefs.edit().putString("recordings_list", json).apply()
    }
    
    fun getUnsyncedRecordings(): List<Recording> {
        return getRecordings().filter { !it.synced }
    }
    
    fun deleteAllRecordings() {
        val recordings = getRecordings()
        // Elimina tutti i file
        for (recording in recordings) {
            File(recording.filePath).delete()
        }
        // Svuota la lista
        prefs.edit().putString("recordings_list", "[]").apply()
    }
}
