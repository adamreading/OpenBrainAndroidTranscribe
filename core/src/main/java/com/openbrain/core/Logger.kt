package com.openbrain.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object Logger {
    private const val TAG = "OpenBrain"
    private var logFile: File? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        // We use getExternalFilesDir(null) which is usually /storage/emulated/0/Android/data/com.openbrain.ambient/files
        // To truly be in the "root" of internal storage (/sdcard/OpenBrain), we'd need MANAGE_EXTERNAL_STORAGE.
        // For now, we'll use this location and log its path.
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val openBrainDir = File(dir, "logs")
        if (!openBrainDir.exists()) openBrainDir.mkdirs()
        logFile = File(openBrainDir, "openbrain_debug.log")
        log("Logger initialized. Log file: ${logFile?.absolutePath}")
    }

    fun log(message: String, level: Int = Log.DEBUG, throwable: Throwable? = null) {
        val timestamp = dateFormat.format(Date())
        val levelStr = when (level) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            else -> "U"
        }
        
        val logLine = "[$timestamp] $levelStr/$TAG: $message"
        
        // Log to Logcat
        when (level) {
            Log.VERBOSE -> Log.v(TAG, message, throwable)
            Log.DEBUG -> Log.d(TAG, message, throwable)
            Log.INFO -> Log.i(TAG, message, throwable)
            Log.WARN -> Log.w(TAG, message, throwable)
            Log.ERROR -> Log.e(TAG, message, throwable)
        }

        // Log to file
        scope.launch {
            try {
                logFile?.let { file ->
                    FileOutputStream(file, true).use { fos ->
                        fos.write((logLine + "\n").toByteArray())
                        throwable?.let { t ->
                            fos.write(Log.getStackTraceString(t).toByteArray())
                            fos.write("\n".toByteArray())
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write to log file", e)
            }
        }
    }

    fun e(message: String, throwable: Throwable? = null) = log(message, Log.ERROR, throwable)
    fun w(message: String) = log(message, Log.WARN)
    fun i(message: String) = log(message, Log.INFO)
    fun d(message: String) = log(message, Log.DEBUG)
    fun v(message: String) = log(message, Log.VERBOSE)

    fun getLogFilePath(): String = logFile?.absolutePath ?: "Not initialized"
}
