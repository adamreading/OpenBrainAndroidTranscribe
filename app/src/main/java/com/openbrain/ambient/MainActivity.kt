package com.openbrain.ambient

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.openbrain.ambient.databinding.ActivityMainBinding
import com.openbrain.ui.AdminActivity
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AmbientState.init(this)

        checkPermissions()
        requestBatteryOptimisationExemption()

        setupUI()
        observeState()
    }

    private fun checkPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        } else {
            startAmbientService()
        }
    }

    private fun requestBatteryOptimisationExemption() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startAmbientService()
        }
    }

    private fun startAmbientService() {
        val serviceIntent = Intent(this, AmbientService::class.java)
        startForegroundService(serviceIntent)
    }

    private fun setupUI() {
        binding.toggleBtn.setOnClickListener {
            lifecycleScope.launch {
                val newState = !AmbientState.isActive.value
                AmbientState.setActive(this@MainActivity, newState)
            }
        }
        binding.openAdminBtn.setOnClickListener {
            val intent = Intent(this, AdminActivity::class.java)

            // Pass sync log data to AdminActivity
            val syncLog = AmbientState.syncLog.value
            intent.putExtra("sync_timestamps", syncLog.map { it.timestamp }.toLongArray())
            intent.putExtra("sync_statuses", syncLog.map { it.status }.toTypedArray())
            intent.putExtra("sync_messages", syncLog.map { it.message }.toTypedArray())

            startActivity(intent)
        }
        binding.clearTranscriptBtn.setOnClickListener {
            AmbientState.clearTranscript()
        }
    }

    private fun observeState() {
        AmbientState.isActive
            .onEach { isActive ->
                binding.statusTv.text = if (isActive) "Listening" else "Sleeping"
                binding.toggleBtn.text = if (isActive) "Go to sleep" else "Wake Up"
            }
            .launchIn(lifecycleScope)

        AmbientState.transcript
            .onEach { text ->
                binding.transcriptTv.text = text
            }
            .launchIn(lifecycleScope)
    }
}
