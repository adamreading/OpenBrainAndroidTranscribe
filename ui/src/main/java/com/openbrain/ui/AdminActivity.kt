package com.openbrain.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.preferences.core.edit
import com.openbrain.core.AppSettings
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.openbrain.ui.databinding.ActivityAdminBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding
    private lateinit var syncLogAdapter: SyncLogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinners()
        setupSeekBars()
        setupSyncLog()
        loadSettings()
        setupButtons()
    }


    private fun setupSpinners() {
        val whisperModels = arrayOf("tiny", "base", "small")
        binding.whisperModelSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, whisperModels
        )
    }

    private fun setupSeekBars() {
        binding.whisperThreadsSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val threads = maxOf(1, progress)
                binding.whisperThreadsLabel.text = "$threads threads"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.llmThreadsSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val threads = maxOf(1, progress)
                binding.llmThreadsLabel.text = "$threads threads"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupSyncLog() {
        syncLogAdapter = SyncLogAdapter()
        binding.syncLogRv.layoutManager = LinearLayoutManager(this)
        binding.syncLogRv.adapter = syncLogAdapter

        val timestamps = intent.getLongArrayExtra("sync_timestamps") ?: longArrayOf()
        val statuses = intent.getStringArrayExtra("sync_statuses") ?: arrayOf()
        val messages = intent.getStringArrayExtra("sync_messages") ?: arrayOf()

        val items = timestamps.indices.map { i ->
            SyncLogItem(
                timestamp = timestamps[i],
                status = statuses.getOrElse(i) { "" },
                message = messages.getOrElse(i) { "" }
            )
        }
        syncLogAdapter.submitList(items)
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            val data = AppSettings.getInstance(applicationContext).data.first()
            binding.supabaseUrlEt.setText(data[AppSettings.SUPABASE_URL] ?: "")
            binding.supabaseKeyEt.setText(data[AppSettings.SUPABASE_API_KEY] ?: "")
            binding.llmModelEt.setText(data[AppSettings.LLM_MODEL] ?: "Llama-3.2-1B-Instruct-Q4_K_M.gguf")
            binding.wakeWordEt.setText(data[AppSettings.WAKE_WORD] ?: "hey adam")
            binding.sleepWordEt.setText(data[AppSettings.SLEEP_WORD] ?: "go to sleep")

            val whisperModel = data[AppSettings.WHISPER_MODEL] ?: "tiny"
            val whisperIndex = when (whisperModel) {
                "base" -> 1
                "small" -> 2
                else -> 0
            }
            binding.whisperModelSpinner.setSelection(whisperIndex)

            val whisperThreads = data[AppSettings.WHISPER_THREADS] ?: 4
            binding.whisperThreadsSeekBar.progress = whisperThreads
            binding.whisperThreadsLabel.text = "$whisperThreads threads"

            val llmThreads = data[AppSettings.LLM_THREADS] ?: 6
            binding.llmThreadsSeekBar.progress = llmThreads
            binding.llmThreadsLabel.text = "$llmThreads threads"
        }
        updateBatteryStatus()
    }

    private fun setupButtons() {
        binding.testConnectionBtn.setOnClickListener {
            saveSettings() // Save first as requested
            val url = binding.supabaseUrlEt.text.toString().trim()
            val key = binding.supabaseKeyEt.text.toString().trim()
            if (url.isBlank()) {
                binding.connectionStatusTv.text = "Please enter URL"
                return@setOnClickListener
            }
            binding.connectionStatusTv.text = "Testing via Main..."
            val resultIntent = Intent().apply {
                putExtra("action", "test_connection")
                putExtra("supabase_url", url)
                putExtra("supabase_api_key", key)
            }
            setResult(RESULT_FIRST_USER, resultIntent)
            finish()
        }

        binding.batteryOptBtn.setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }

        binding.saveBtn.setOnClickListener {
            saveSettings()
        }
    }

    private fun saveSettings() {
        lifecycleScope.launch {
            AppSettings.getInstance(applicationContext).edit { prefs ->
                prefs[AppSettings.SUPABASE_URL] = binding.supabaseUrlEt.text.toString().trim()
                prefs[AppSettings.SUPABASE_API_KEY] = binding.supabaseKeyEt.text.toString().trim()
                prefs[AppSettings.WHISPER_MODEL] = binding.whisperModelSpinner.selectedItem.toString()
                prefs[AppSettings.LLM_MODEL] = binding.llmModelEt.text.toString().trim()
                prefs[AppSettings.WHISPER_THREADS] = maxOf(1, binding.whisperThreadsSeekBar.progress)
                prefs[AppSettings.LLM_THREADS] = maxOf(1, binding.llmThreadsSeekBar.progress)
                prefs[AppSettings.WAKE_WORD] = binding.wakeWordEt.text.toString().trim()
                prefs[AppSettings.SLEEP_WORD] = binding.sleepWordEt.text.toString().trim()
            }
            Toast.makeText(this@AdminActivity, "Settings saved", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateBatteryStatus()
    }

    private fun updateBatteryStatus() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val exempt = pm.isIgnoringBatteryOptimizations(packageName)
        binding.batteryStatusTv.text = if (exempt) {
            "Battery optimisation: EXEMPT"
        } else {
            "Battery optimisation: NOT exempt"
        }
    }
}
