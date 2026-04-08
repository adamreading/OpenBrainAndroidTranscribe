package com.openbrain.ambient

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.openbrain.ambient.databinding.ActivityMainBinding
import androidx.activity.result.contract.ActivityResultContracts
import com.openbrain.core.Logger
import com.openbrain.ui.AdminActivity
import com.openbrain.ui.SyncLogItem
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val adminLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_FIRST_USER) {
            val action = result.data?.getStringExtra("action")
            if (action == "test_connection") {
                val url = result.data?.getStringExtra("supabase_url") ?: ""
                val key = result.data?.getStringExtra("supabase_api_key") ?: ""
                testConnection(url, key)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.init(applicationContext)
        Logger.d("MainActivity onCreate")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AmbientState.init(this)

        checkPermissions()
        requestBatteryOptimisationExemption()

        setupUI()
        observeState()
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val missingPermissions = permissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 100)
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

            adminLauncher.launch(intent)
        }
        binding.clearTranscriptBtn.setOnClickListener {
            AmbientState.clearTranscript()
        }
    }

    private fun observeState() {
        AmbientState.isActive.onEach { isActive ->
            binding.statusTv.text = if (isActive) "Listening" else "Sleeping"
            binding.toggleBtn.text = if (isActive) "Stop" else "Start"
        }.launchIn(lifecycleScope)

        AmbientState.transcript.onEach { text ->
            binding.transcriptTv.text = text
            // Auto scroll to bottom
            binding.transcriptTv.post {
                binding.transcriptTv.parent.let {
                    if (it is android.widget.ScrollView) {
                        it.fullScroll(android.view.View.FOCUS_DOWN)
                    }
                }
            }
        }.launchIn(lifecycleScope)
    }

    private fun testConnection(url: String, key: String) {
        lifecycleScope.launch {
            try {
                val client = com.openbrain.client.OpenBrainClient(url)
                val result = client.testConnection(key)
                if (result.isSuccess) {
                    android.widget.Toast.makeText(this@MainActivity, "Connection Successful!", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    android.widget.Toast.makeText(this@MainActivity, "Connection Failed: $error", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Logger.e("Manual connection test failed", e)
                android.widget.Toast.makeText(this@MainActivity, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}
