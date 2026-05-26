package com.example.launcherorbys.managers

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.launcherorbys.services.ScreenRecordService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Gestor encargado de la lógica de estado y temporización de la grabación de pantalla.
 * Separa la lógica de negocio de la visualización en los Overlays.
 */
class RecordingManager(private val context: Context) {

    var isRecording by mutableStateOf(false)
    var recordingSeconds by mutableIntStateOf(0)
    var showStopConfirmation by mutableStateOf(false)
    
    private var timerJob: Job? = null

    /**
     * Inicia el contador visual de la grabación.
     */
    fun startTimer(lifecycleOwner: LifecycleOwner) {
        if (isRecording) return
        isRecording = true
        recordingSeconds = 0
        showStopConfirmation = false

        timerJob?.cancel()
        timerJob = lifecycleOwner.lifecycleScope.launch {
            while (isRecording) {
                delay(1000)
                recordingSeconds++
            }
        }
    }

    /**
     * Detiene el servicio de grabación y el contador.
     */
    fun stopRecording() {
        context.stopService(Intent(context, ScreenRecordService::class.java))
        resetState()
    }

    /**
     * Limpia el estado interno cuando la grabación finaliza por cualquier motivo.
     */
    fun resetState() {
        isRecording = false
        showStopConfirmation = false
        timerJob?.cancel()
        timerJob = null
        recordingSeconds = 0
    }
}
