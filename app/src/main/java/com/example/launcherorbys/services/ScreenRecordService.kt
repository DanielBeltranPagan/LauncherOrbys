package com.example.launcherorbys.services

import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.launcherorbys.utils.Constants
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Servicio encargado de capturar la pantalla y el audio del sistema o micrófono.
 *
 * Se ejecuta como un **Foreground Service** para garantizar que el sistema no lo finalice
 * durante la grabación y para cumplir con los requisitos de seguridad de Android para el uso
 * de la API [MediaProjection].
 *
 * Soporta grabación en dispositivos desde Android 7.0 (Nougat) hasta las versiones más recientes,
 * manejando adecuadamente los permisos de almacenamiento y audio según la versión del SDK.
 */
class ScreenRecordService : Service() {

    /** Instancia de proyección de medios obtenida tras el consentimiento del usuario. */
    private var mediaProjection: MediaProjection? = null
    /** Grabador multimedia encargado de codificar y guardar el vídeo. */
    private var mediaRecorder: MediaRecorder? = null
    /** Pantalla virtual donde se renderiza la captura de pantalla para el [mediaRecorder]. */
    private var virtualDisplay: VirtualDisplay? = null
    /** URI del archivo de vídeo donde se está guardando la grabación. */
    private var videoUri: Uri? = null
    
    private var screenWidth = 720
    private var screenHeight = 1280
    private var screenDensity = 1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        initDimensions()
    }

    /**
     * Inicializa las dimensiones de captura basadas en la resolución real del dispositivo.
     * Ajusta los valores a múltiplos de 2 para compatibilidad con codificadores de vídeo.
     */
    private fun initDimensions() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            screenWidth = (bounds.width() / 2) * 2
            screenHeight = (bounds.height() / 2) * 2
            screenDensity = resources.displayMetrics.densityDpi
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            screenWidth = (metrics.widthPixels / 2) * 2
            screenHeight = (metrics.heightPixels / 2) * 2
            screenDensity = metrics.densityDpi
        }
    }

    /**
     * Procesa la intención de inicio para comenzar la grabación.
     * 
     * @param intent El [Intent] que contiene el `resultCode` y el `data` de la proyección.
     * @param flags Parámetros adicionales sobre la solicitud de inicio.
     * @param startId Un identificador único para esta solicitud de inicio.
     * @return Indica cómo debe comportarse el servicio si el sistema lo mata.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("data")
        }

        if (resultCode == Activity.RESULT_OK && data != null) {
            try {
                startForegroundNotification()
                setupMediaProjection(resultCode, data)
                startRecording()
            } catch (e: Exception) {
                Log.e("ScreenRecord", "Fallo al iniciar grabación", e)
                stopSelf()
            }
        } else {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    /**
     * Crea y muestra la notificación persistente necesaria para el servicio de primer plano.
     * En Android 10+ incluye los tipos de servicio específicos para proyección de medios y micrófono.
     */
    private fun startForegroundNotification() {
        val channelId = "screen_record_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Servicio de Grabación", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Capturando pantalla...")
            .setContentText("Tu pantalla está siendo grabada")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val hasAudio = ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (hasAudio && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(1001, notification, type)
        } else {
            startForeground(1001, notification)
        }
    }

    /**
     * Configura la sesión de [MediaProjection] y registra un callback para detener el servicio
     * si la proyección finaliza externamente.
     */
    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, Handler(Looper.getMainLooper()))
    }

    /**
     * Inicia el proceso de grabación configurando el [MediaRecorder] y creando la [VirtualDisplay].
     * Maneja el almacenamiento de archivos de forma diferente para versiones anteriores y posteriores a Android 10 (Scoped Storage).
     */
    private fun startRecording() {
        val fileName = "Orbys_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.mp4"
        val hasAudio = ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        
        val fileDescriptor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/LauncherOrbys")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            videoUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            videoUri?.let { contentResolver.openFileDescriptor(it, "rw")?.fileDescriptor }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "LauncherOrbys").apply { if (!exists()) mkdirs() }
            val file = File(dir, fileName)
            videoUri = Uri.fromFile(file)
            java.io.FileOutputStream(file).fd
        } ?: return

        mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()).apply {
            if (hasAudio) setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            if (hasAudio) setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(screenWidth, screenHeight)
            setVideoFrameRate(30)
            setVideoEncodingBitRate(6 * 1024 * 1024)
            setOutputFile(fileDescriptor)
            prepare()
        }

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecord", screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder?.surface, null, null
        )

        mediaRecorder?.start()
        sendBroadcast(Intent(Constants.ACTION_RECORDING_STARTED))
    }

    /**
     * Detiene la grabación y libera todos los recursos (MediaRecorder, VirtualDisplay, MediaProjection).
     * Si se usó Scoped Storage, marca el archivo de vídeo como completado (IS_PENDING = 0).
     */
    override fun onDestroy() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            virtualDisplay?.release()
            mediaProjection?.stop()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && videoUri != null) {
                val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                contentResolver.update(videoUri!!, values, null, null)
            }
        } catch (e: Exception) {
            Log.e("ScreenRecord", "Error al liberar recursos", e)
        }
        sendBroadcast(Intent(Constants.ACTION_RECORDING_STOPPED))
        super.onDestroy()
    }
}
