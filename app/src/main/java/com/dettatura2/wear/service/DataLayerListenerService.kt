package com.dettatura2.wear.service

import com.google.android.gms.wearable.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * Servizio per sincronizzare le registrazioni con il telefono
 * tramite Wear Data Layer API
 */
class DataLayerListenerService : WearableListenerService() {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    companion object {
        private const val RECORDINGS_PATH = "/dettatura2/recordings"
        private const val SYNC_REQUEST_PATH = "/dettatura2/sync_request"
    }
    
    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        
        when (messageEvent.path) {
            SYNC_REQUEST_PATH -> {
                // Il telefono ha richiesto la sincronizzazione
                scope.launch {
                    syncRecordingsToPhone()
                }
            }
        }
    }
    
    private suspend fun syncRecordingsToPhone() {
        try {
            val repository = com.dettatura2.wear.data.RecordingRepository(this)
            val unsyncedRecordings = repository.getUnsyncedRecordings()
            
            if (unsyncedRecordings.isEmpty()) return
            
            val dataClient = Wearable.getDataClient(this)
            
            for (recording in unsyncedRecordings) {
                val file = File(recording.filePath)
                if (!file.exists()) continue
                
                val asset = Asset.createFromBytes(file.readBytes())
                
                val dataMapRequest = PutDataMapRequest.create("$RECORDINGS_PATH/${recording.id}").apply {
                    dataMap.putAsset("audio", asset)
                    dataMap.putString("id", recording.id)
                    dataMap.putInt("duration", recording.duration)
                    dataMap.putLong("createdAt", recording.createdAt)
                    dataMap.putLong("timestamp", System.currentTimeMillis()) // Force sync
                }
                
                val request = dataMapRequest.asPutDataRequest().setUrgent()
                dataClient.putDataItem(request).await()
                
                // Marca come sincronizzato
                repository.markAsSynced(recording.id)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
