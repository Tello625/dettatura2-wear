package com.dettatura2.wear.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.*
import com.dettatura2.wear.data.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var repository: RecordingRepository
    private lateinit var cloudSync: CloudSyncService
    private lateinit var vibrator: Vibrator
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Permission handled
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            audioRecorder = AudioRecorder(this)
            repository = RecordingRepository(this)
            cloudSync = CloudSyncService(this)
            vibrator = getSystemService(Vibrator::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Check permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        
        setContent {
            Dettatura2WearApp(
                audioRecorder = audioRecorder,
                repository = repository,
                cloudSync = cloudSync,
                onRecordingComplete = { vibrateSuccess() },
                onSyncComplete = { success -> if (success) vibrateSuccess() else vibrateError() }
            )
        }
    }
    
    private fun vibrateSuccess() {
        try {
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun vibrateError() {
        try {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 100, 100), -1))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        audioRecorder.release()
    }
}

@Composable
fun Dettatura2WearApp(
    audioRecorder: AudioRecorder,
    repository: RecordingRepository,
    cloudSync: CloudSyncService,
    onRecordingComplete: () -> Unit,
    onSyncComplete: (Boolean) -> Unit
) {
    var isRecording by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(10) }
    var recordings by remember { mutableStateOf(repository.getRecordings()) }
    var currentScreen by remember { mutableStateOf(
        if (cloudSync.savedPin == null) "setup" else "main"
    )}
    var selectedRecording by remember { mutableStateOf<Recording?>(null) }
    var previousScreen by remember { mutableStateOf("list") }
    var isSyncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var isTranscribing by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    // Funzione per trascrivere in background (non blocca)
    fun transcribeInBackground(recording: Recording) {
        scope.launch {
            isTranscribing = true
            try {
                val result = cloudSync.transcribeAudio(recording)
                if (result.success && result.transcription.isNotEmpty()) {
                    repository.updateTranscription(recording.id, result.transcription)
                    recordings = repository.getRecordings()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isTranscribing = false
        }
    }
    
    // Countdown timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            countdown = 10
            while (countdown > 0 && isRecording) {
                kotlinx.coroutines.delay(1000)
                countdown--
            }
            if (isRecording) {
                // Auto-stop
                isRecording = false
                val recording = audioRecorder.stopRecording()
                if (recording != null) {
                    repository.saveRecording(recording)
                    recordings = repository.getRecordings()
                    onRecordingComplete()
                    // Trascrizione in background (non blocca)
                    transcribeInBackground(recording)
                }
            }
        }
    }
    
    MaterialTheme {
        when (currentScreen) {
            "setup" -> SetupScreen(
                cloudSync = cloudSync,
                onComplete = { currentScreen = "main" }
            )
            
            "main" -> {
                // Calcola l'ultima trascrizione
                val lastTranscription = recordings
                    .filter { it.transcription.isNotEmpty() }
                    .maxByOrNull { it.createdAt }
                    ?.transcription
                
                val transcriptionCount = recordings.count { it.transcription.isNotEmpty() }
                
                MainScreen(
                    isRecording = isRecording,
                    countdown = countdown,
                    isSyncing = isSyncing,
                    isTranscribing = isTranscribing,
                    syncMessage = syncMessage,
                    unsyncedCount = repository.getUnsyncedRecordings().size,
                    lastTranscription = lastTranscription,
                    transcriptionCount = transcriptionCount,
                    onRecordClick = {
                        scope.launch {
                            if (isRecording) {
                                isRecording = false
                                val recording = audioRecorder.stopRecording()
                                if (recording != null) {
                                    repository.saveRecording(recording)
                                    recordings = repository.getRecordings()
                                    onRecordingComplete()
                                    // Trascrizione in background (non blocca)
                                    transcribeInBackground(recording)
                                }
                            } else {
                                if (audioRecorder.startRecording()) {
                                    isRecording = true
                                }
                            }
                        }
                    },
                    onListClick = { currentScreen = "list" },
                    onTranscriptionsClick = { currentScreen = "transcriptions" },
                    onSyncClick = {
                        scope.launch {
                            isSyncing = true
                            val result = cloudSync.syncAllUnsynced(repository)
                            isSyncing = false
                            syncMessage = result.message
                            recordings = repository.getRecordings()
                            onSyncComplete(result.success)
                            kotlinx.coroutines.delay(2000)
                            syncMessage = null
                        }
                    },
                    onSettingsClick = { currentScreen = "setup" },
                    recordingsCount = recordings.size
                )
            }
            
            "list" -> RecordingsListScreen(
                recordings = recordings,
                onBack = { currentScreen = "main" },
               onRecordingClick = { rec ->
                    selectedRecording = rec
                    previousScreen = "list"
                    currentScreen = "player"
                },
                onDeleteAll = {
                    repository.deleteAllRecordings()
                    recordings = repository.getRecordings()
                    currentScreen = "main"
                }
            )
            
            "player" -> selectedRecording?.let { rec ->
                PlayerScreen(
                    recording = rec,
                    audioRecorder = audioRecorder,
                   onBack = { currentScreen = previousScreen },
                    onDelete = {
                        repository.deleteRecording(rec)
                        recordings = repository.getRecordings()
                        currentScreen = "list"
                    }
                )
            }
            
            "transcriptions" -> TranscriptionsListScreen(
                recordings = recordings,
               onTranscriptionClick = { rec ->
                    selectedRecording = rec
                    previousScreen = "transcriptions"
                    currentScreen = "player"
                },
                onBack = { currentScreen = "main" }
            )
        }
    }
}

@Composable
fun SetupScreen(
    cloudSync: CloudSyncService,
    onComplete: () -> Unit
) {
    var pin by remember { mutableStateOf(cloudSync.savedPin ?: "") }
    var isChecking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Inserisci PIN",
                color = Color.White,
                fontSize = 16.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // PIN Input
            BasicTextField(
                value = pin,
                onValueChange = { if (it.length <= 6) pin = it },
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .background(Color(0xFF333333), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(12.dp)
                    .width(100.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Messaggi
            errorMessage?.let {
                Text(text = it, color = Color.Red, fontSize = 12.sp)
            }
            successMessage?.let {
                Text(text = it, color = Color.Green, fontSize = 12.sp)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Pulsante Conferma
            Button(
                onClick = {
                    if (pin.length >= 4) {
                        scope.launch {
                            isChecking = true
                            errorMessage = null
                            
                            val result = cloudSync.checkPin(pin)
                            
                            if (result.exists || pin.length >= 4) {
                                cloudSync.savedPin = pin
                                successMessage = "PIN salvato!"
                                kotlinx.coroutines.delay(1000)
                                onComplete()
                            } else {
                                errorMessage = "PIN non valido"
                            }
                            
                            isChecking = false
                        }
                    } else {
                        errorMessage = "Minimo 4 cifre"
                    }
                },
                enabled = !isChecking && pin.length >= 4,
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text(if (isChecking) "..." else "Conferma")
            }
            
            // Skip se già configurato
            if (cloudSync.savedPin != null) {
                Spacer(modifier = Modifier.height(8.dp))
                CompactChip(
                    onClick = onComplete,
                    label = { Text("Annulla") },
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    isRecording: Boolean,
    countdown: Int,
    isSyncing: Boolean,
    isTranscribing: Boolean,
    syncMessage: String?,
    unsyncedCount: Int,
    lastTranscription: String?,
    transcriptionCount: Int,
    onRecordClick: () -> Unit,
    onListClick: () -> Unit,
    onTranscriptionsClick: () -> Unit,
    onSyncClick: () -> Unit,
    onSettingsClick: () -> Unit,
    recordingsCount: Int
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Ultima trascrizione o stato
            Text(
                text = when {
                    isSyncing -> "Sync..."
                    isTranscribing -> "📝..."
                    syncMessage != null -> syncMessage
                    isRecording -> "$countdown"
                    lastTranscription != null -> lastTranscription
                    else -> "Trascrizioni 0"
                },
                color = when {
                    isSyncing -> Color(0xFF2196F3)
                    isTranscribing -> Color(0xFF9C27B0)
                    syncMessage != null -> Color(0xFF4CAF50)
                    isRecording -> Color(0xFFFF5722)
                    else -> Color.White
                },
                fontSize = when {
                    isRecording -> 48.sp
                    else -> 14.sp
                },
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Riga con pulsante trascrizioni + REC
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pulsante elenco trascrizioni (a sinistra, più piccolo)
                if (!isRecording && transcriptionCount > 0) {
                    Button(
                        onClick = onTranscriptionsClick,
                        modifier = Modifier.size(55.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF9C27B0)
                        )
                    ) {
                        Text(
                            text = "📝",
                            fontSize = 20.sp
                        )
                    }
                }
                
                // Pulsante REGISTRA grande
                Button(
                    onClick = onRecordClick,
                    modifier = Modifier.size(80.dp),
                    enabled = !isSyncing,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (isRecording) Color(0xFFFF5722) else Color(0xFF009688)
                    )
                ) {
                    Text(
                        text = if (isRecording) "STOP" else "REC",
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Riga pulsanti secondari
            if (!isRecording) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Lista
                    if (recordingsCount > 0) {
                        CompactChip(
                            onClick = onListClick,
                            label = { Text("$recordingsCount") },
                            colors = ChipDefaults.secondaryChipColors()
                        )
                    }
                    
                    // Sync manuale (se ci sono non sincronizzate)
                    if (unsyncedCount > 0) {
                        CompactChip(
                            onClick = onSyncClick,
                            label = { Text("↑$unsyncedCount") },
                            colors = ChipDefaults.chipColors(
                                backgroundColor = Color(0xFF2196F3)
                            )
                        )
                    }
                    
                    // Settings
                    CompactChip(
                        onClick = onSettingsClick,
                        label = { Text("⚙") },
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }
            }
        }
    }
}

@Composable
fun RecordingsListScreen(
    recordings: List<Recording>,
    onBack: () -> Unit,
    onRecordingClick: (Recording) -> Unit,
    onDeleteAll: () -> Unit = {}
) {
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Registrazioni",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )
        }
        
        items(recordings.takeLast(10).reversed().size) { index ->
            val recording = recordings.takeLast(10).reversed()[index]
            Chip(
                onClick = { onRecordingClick(recording) },
                label = {
                    Text(
                        text = recording.displayName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                secondaryLabel = {
                    Row {
                        Text("${recording.duration}s")
                        if (recording.transcription.isNotEmpty()) {
                            Text(" 📝", color = Color(0xFF9C27B0))
                        }
                        if (recording.synced) {
                            Text(" ✓", color = Color(0xFF4CAF50))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(0.9f),
                colors = ChipDefaults.secondaryChipColors()
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Pulsante Elimina Tutte
        if (recordings.isNotEmpty()) {
            item {
                Chip(
                    onClick = onDeleteAll,
                    label = { Text("Elimina tutte") },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = Color(0xFFE53935)
                    ),
                    modifier = Modifier.fillMaxWidth(0.9f)
                )
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(4.dp))
            CompactChip(
                onClick = onBack,
                label = { Text("Indietro") },
                colors = ChipDefaults.secondaryChipColors()
            )
        }
    }
}

@Composable
fun PlayerScreen(
    recording: Recording,
    audioRecorder: AudioRecorder,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = recording.displayName,
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Row {
                Text(
                    text = "${recording.duration}s",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                if (recording.synced) {
                    Text(
                        text = " • Sync ✓",
                        color = Color(0xFF4CAF50),
                        fontSize = 12.sp
                    )
                }
            }
            
            // Mostra trascrizione se presente
            if (recording.transcription.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = recording.transcription,
                    color = Color(0xFFBBBBBB),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Play/Pause
                Button(
                    onClick = {
                        if (isPlaying) {
                            audioRecorder.stopPlayback()
                            isPlaying = false
                        } else {
                            audioRecorder.playRecording(recording.filePath) {
                                isPlaying = false
                            }
                            isPlaying = true
                        }
                    },
                    modifier = Modifier.size(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF009688)
                    )
                ) {
                    Text(if (isPlaying) "||" else "▶", color = Color.White)
                }
                
                // Delete
                Button(
                    onClick = {
                        audioRecorder.stopPlayback()
                        onDelete()
                    },
                    modifier = Modifier.size(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFFE53935)
                    )
                ) {
                    Text("✕", color = Color.White)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            CompactChip(
                onClick = {
                    audioRecorder.stopPlayback()
                    onBack()
                },
                label = { Text("Indietro") }
            )
        }
    }
}


@Composable
fun TranscriptionsListScreen(
    recordings: List<Recording>,
    onTranscriptionClick: (Recording) -> Unit,
    onBack: () -> Unit
) {
    // Filtra solo le registrazioni con trascrizione
    val transcribedRecordings = recordings.filter { it.transcription.isNotEmpty() }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header fisso
            Text(
                text = "Trascrizioni (${transcribedRecordings.size})",
                color = Color(0xFF9C27B0),
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 8.dp),
                textAlign = TextAlign.Center
            )
            
            if (transcribedRecordings.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nessuna trascrizione",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            } else {
                // Lista scrollabile
                ScalingLazyColumn(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(transcribedRecordings.reversed().size) { index ->
                        val recording = transcribedRecordings.reversed()[index]
                        Text(
                            text = recording.transcription,
                            color = Color.White,
                            fontSize = 16.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .padding(vertical = 6.dp)
                                .clickable { onTranscriptionClick(recording) }
                        )
                        // Separatore
                        if (index < transcribedRecordings.size - 1) {
                           Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(1.dp)
                                .background(Color(0xFF333333))
                        )
                        }
                    }
                }
            }
            
            // Pulsante Indietro
            CompactChip(
                onClick = onBack,
                label = { Text("Indietro") },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 8.dp)
            )
        }
    }
}

