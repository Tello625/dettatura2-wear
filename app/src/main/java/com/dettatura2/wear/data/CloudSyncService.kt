package com.dettatura2.wear.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * Servizio per sincronizzare le registrazioni con il cloud
 * Usa lo stesso backend dell'app mobile Dettatura2
 */
class CloudSyncService(private val context: Context) {
    
    companion object {
        // URL del backend PRODUZIONE - stesso dell'app mobile
        private const val API_BASE_URL = "https://dettatura-2.emergent.host/api"
    }
    
    private val prefs = context.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE)
    
    // PIN salvato per la sincronizzazione
    var savedPin: String?
        get() = prefs.getString("pin", null)
        set(value) = prefs.edit().putString("pin", value).apply()
    
    /**
     * Carica una registrazione sul cloud
     */
    suspend fun uploadRecording(recording: Recording, pin: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(recording.filePath)
            if (!file.exists()) return@withContext false
            
            // Leggi il file audio e converti in Base64
            val audioBytes = file.readBytes()
            val audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
            
            // Crea il JSON per la richiesta
            val recordingJson = JSONObject().apply {
                put("id", "watch_${recording.id}")
                put("filename", "watch_${recording.fileName}")
                put("duration", recording.duration)
                put("color", "orange") // Colore arancione per registrazioni da watch
                put("created_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date(recording.createdAt)))
                put("transcription", if (recording.transcription.isNotEmpty()) recording.transcription else null)
                put("isProtected", false)
                put("audioBase64", audioBase64)
            }
            
            val requestBody = JSONObject().apply {
                put("pin", pin)
                put("recordings", JSONArray().put(recordingJson))
            }
            
            // Invia al server
            val url = URL("$API_BASE_URL/sync/upload")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 30000
                readTimeout = 30000
            }
            
            connection.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray())
            }
            
            val responseCode = connection.responseCode
            connection.disconnect()
            
            return@withContext responseCode == 200
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
    
    /**
     * Verifica se il PIN esiste sul cloud
     */
    suspend fun checkPin(pin: String): PinCheckResult = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("pin", pin)
            }
            
            val url = URL("$API_BASE_URL/sync/check")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
            }
            
            connection.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray())
            }
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                connection.disconnect()
                
                return@withContext PinCheckResult(
                    exists = json.optBoolean("exists", false),
                    recordingCount = json.optInt("recordingCount", 0)
                )
            }
            
            connection.disconnect()
            return@withContext PinCheckResult(false, 0)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext PinCheckResult(false, 0)
        }
    }
    
    /**
     * Carica tutte le registrazioni non sincronizzate
     */
    suspend fun syncAllUnsynced(repository: RecordingRepository): SyncResult {
        val pin = savedPin ?: return SyncResult(false, 0, "PIN non configurato")
        
        val unsynced = repository.getUnsyncedRecordings()
        if (unsynced.isEmpty()) {
            return SyncResult(true, 0, "Nessuna registrazione da sincronizzare")
        }
        
        var successCount = 0
        for (recording in unsynced) {
            if (uploadRecording(recording, pin)) {
                repository.markAsSynced(recording.id)
                successCount++
            }
        }
        
        return SyncResult(
            success = successCount > 0,
            count = successCount,
            message = if (successCount == unsynced.size) {
                "Sincronizzate $successCount registrazioni"
            } else {
                "Sincronizzate $successCount/${unsynced.size}"
            }
        )
    }
    
    /**
     * Trascrizione diretta: invia audio, riceve testo
     * Usato per trascrivere subito dopo la registrazione
     */
    suspend fun transcribeAudio(recording: Recording): TranscriptionResult = withContext(Dispatchers.IO) {
        val pin = savedPin ?: return@withContext TranscriptionResult(false, "", "PIN non configurato")
        
        try {
            val file = File(recording.filePath)
            if (!file.exists()) {
                return@withContext TranscriptionResult(false, "", "File non trovato")
            }
            
            // Leggi il file audio e converti in Base64
            val audioBytes = file.readBytes()
            val audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
            
            // Crea il JSON per la richiesta
            val requestBody = JSONObject().apply {
                put("audioBase64", audioBase64)
                put("pin", pin)
            }
            
            val url = URL("$API_BASE_URL/transcribe/direct")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 30000  // 30 secondi timeout
                readTimeout = 30000
            }
            
            connection.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray())
            }
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                connection.disconnect()
                
                val success = json.optBoolean("success", false)
                val transcription = json.optString("transcription", "")
                val error = json.optString("error", null)
                
                return@withContext TranscriptionResult(success, transcription, error)
            }
            
            connection.disconnect()
            return@withContext TranscriptionResult(false, "", "Errore HTTP: ${connection.responseCode}")
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext TranscriptionResult(false, "", e.message ?: "Errore sconosciuto")
        }
    }
}

data class PinCheckResult(
    val exists: Boolean,
    val recordingCount: Int
)

data class SyncResult(
    val success: Boolean,
    val count: Int,
    val message: String
)

data class TranscriptionResult(
    val success: Boolean,
    val transcription: String,
    val error: String? = null
)
