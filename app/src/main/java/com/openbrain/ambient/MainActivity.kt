package com.openbrain.ambient

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.openbrain.ambient.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissions()

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
            AmbientState.toggleActive()
        }
        binding.openAdminBtn.setOnClickListener {
            val intent = Intent(this, com.openbrain.ui.AdminActivity::class.java)
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
