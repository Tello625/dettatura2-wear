# Dettatura2 Wear OS App

App companion per Samsung Galaxy Watch 7 (e altri smartwatch Wear OS 3+).

## Funzionalità

- **Registrazione veloce**: Tap per registrare (max 10 secondi, auto-stop)
- **Sync manuale**: Pulsante "↑N" per inviare al cloud quando vuoi tu
- **Lista registrazioni**: Ultime 10 voci con stato sync (✓)
- **Riproduzione**: Riascolta direttamente dall'orologio
- **Vibrazione**: Feedback quando registrazione/sync completati
- **Configurazione PIN**: Stesso PIN dell'app mobile

## Flusso

```
[WATCH]                                    [CLOUD]                [PHONE]
   │                                          │                      │
   ├─ 1. Configura PIN (una volta)            │                      │
   ├─ 2. Tap "REC"                            │                      │
   ├─ 3. Parli (max 10 sec)                   │                      │
   ├─ 4. Vibra = salvato in locale            │                      │
   │                                          │                      │
   │   ... altre registrazioni ...            │                      │
   │                                          │                      │
   ├─ 5. Tap "↑N" per SYNC ──────────────────►├── Audio salvato ────►│
   │      (quando decidi tu)                  │                      │
   │                                          │    6. Apri app ──────┤
   │                                          │    7. Vedi registraz │
   │                                          │    8. Aggiungi foto  │
```

## Requisiti

- Android Studio Hedgehog (2023.1.1) o superiore
- Gradle 8.2+
- Kotlin 1.9+
- Samsung Galaxy Watch 7 o altro Wear OS 3+ con microfono
- WiFi per sincronizzazione cloud

## Come compilare

### Android Studio (consigliato)
1. Apri il progetto in Android Studio (File → Open)
2. Attendi il sync di Gradle
3. Collega il Galaxy Watch in modalità debug:
   - Sul watch: Impostazioni → Info → Tocca 7 volte "Versione software"
   - Impostazioni → Opzioni sviluppatore → Debug ADB → ON
   - Debug via WiFi → annota l'IP
4. Da terminale: `adb connect <IP-watch>:5555`
5. Seleziona il dispositivo Wear OS in Android Studio
6. Clicca **Run** ▶️

### Terminale
```bash
cd wear_os_app
./gradlew assembleDebug
# APK in: app/build/outputs/apk/debug/app-debug.apk

# Installa su watch
adb -s <IP-watch>:5555 install app/build/outputs/apk/debug/app-debug.apk
```

## Primo utilizzo

1. Apri l'app sul watch
2. Inserisci lo stesso **PIN** che usi nell'app mobile
3. Tocca "Conferma"
4. Ora puoi registrare!

## Interfaccia

### Schermata principale
- **REC** (verde): Inizia registrazione
- **STOP** (arancione): Ferma registrazione
- **Numero**: Contatore registrazioni salvate
- **↑N**: Registrazioni da sincronizzare (tap per sync manuale)
- **⚙**: Modifica PIN

### Lista registrazioni
- Tocca una voce per riprodurla
- **✓** verde = sincronizzata con cloud
- **▶** = Riproduci
- **✕** = Elimina

## Struttura codice

```
app/src/main/java/com/dettatura2/wear/
├── presentation/
│   └── MainActivity.kt      # UI principale (Jetpack Compose)
├── data/
│   ├── AudioRecorder.kt     # Registrazione/riproduzione audio
│   ├── RecordingRepository.kt # Salvataggio locale
│   └── CloudSyncService.kt  # Sincronizzazione con cloud
└── service/
    ├── DataLayerListenerService.kt  # (futuro) Sync diretto con telefono
    └── RecordComplicationService.kt # Complicazione watch face
```

## Colore registrazioni

Le registrazioni fatte dal watch appaiono in **arancione** nell'app mobile,
così puoi distinguerle da quelle fatte direttamente dal telefono.

## Troubleshooting

### "Sync fallita"
- Verifica che il watch sia connesso al WiFi
- Verifica che il PIN sia corretto
- Prova a fare sync manuale (pulsante ↑)

### "Registrazione non salvata"
- Verifica i permessi del microfono nelle impostazioni del watch

### Watch non rilevato da ADB
- Assicurati che Debug ADB sia attivo sul watch
- Riavvia il watch
- Prova: `adb kill-server && adb start-server`
