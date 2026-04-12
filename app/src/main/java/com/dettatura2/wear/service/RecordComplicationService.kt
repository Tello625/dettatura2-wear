package com.dettatura2.wear.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.dettatura2.wear.R
import com.dettatura2.wear.presentation.MainActivity

/**
 * Complicazione per il watch face
 * Permette di avviare la registrazione direttamente dalla schermata principale
 */
class RecordComplicationService : ComplicationDataSourceService() {
    
    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val complicationData = when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("REC").build(),
                    contentDescription = PlainComplicationText.Builder("Registra").build()
                )
                    .setTapAction(pendingIntent)
                    .build()
            }
            
            ComplicationType.MONOCHROMATIC_IMAGE -> {
                MonochromaticImageComplicationData.Builder(
                    monochromaticImage = MonochromaticImage.Builder(
                        Icon.createWithResource(this, R.drawable.ic_mic)
                    ).build(),
                    contentDescription = PlainComplicationText.Builder("Registra").build()
                )
                    .setTapAction(pendingIntent)
                    .build()
            }
            
            else -> null
        }
        
        if (complicationData != null) {
            listener.onComplicationData(complicationData)
        }
    }
    
    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("REC").build(),
                    contentDescription = PlainComplicationText.Builder("Registra").build()
                ).build()
            }
            else -> null
        }
    }
}
