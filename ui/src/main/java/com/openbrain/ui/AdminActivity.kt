package com.openbrain.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.openbrain.ui.databinding.ActivityAdminBinding

class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var syncLogAdapter: SyncLogAdapter

    companion object {
        const val PREFS_NAME = "openbrain_settings"
        const val KEY_SUPABASE_URL = "supabase_url"
        const val KEY_SUPABASE_KEY = "supabase_api_key"
        const val KEY_WHISPER_MODEL = "whisper_model"
        const val KEY_LLM_MODEL = "llm_model"
        const val KEY_WHISPER_THREADS = "whisper_threads"
        const val KEY_LLM_THREADS = "llm_threads"
        const val KEY_WAKE_WORD = "wake_word"
        const val KEY_SLEEP_WORD = "sleep_word"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

        // Load sync log from intent extras if provided
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
        binding.supabaseUrlEt.setText(prefs.getString(KEY_SUPABASE_URL, ""))
        binding.supabaseKeyEt.setText(prefs.getString(KEY_SUPABASE_KEY, ""))
        binding.llmModelEt.setText(prefs.getString(KEY_LLM_MODEL, "phi-3-mini-4k-instruct-q4.gguf"))
        binding.wakeWordEt.setText(prefs.getString(KEY_WAKE_WORD, "hey adam"))
        binding.sleepWordEt.setText(prefs.getString(KEY_SLEEP_WORD, "go to sleep"))

        val whisperModel = prefs.getString(KEY_WHISPER_MODEL, "tiny") ?: "tiny"
        val whisperIndex = when (whisperModel) {
            "base" -> 1
            "small" -> 2
            else -> 0
        }
        binding.whisperModelSpinner.setSelection(whisperIndex)

        val whisperThreads = prefs.getInt(KEY_WHISPER_THREADS, 4)
        binding.whisperThreadsSeekBar.progress = whisperThreads
        binding.whisperThreadsLabel.text = "$whisperThreads threads"

        val llmThreads = prefs.getInt(KEY_LLM_THREADS, 6)
        binding.llmThreadsSeekBar.progress = llmThreads
        binding.llmThreadsLabel.text = "$llmThreads threads"

        updateBatteryStatus()
    }

    private fun setupButtons() {
        binding.testConnectionBtn.setOnClickListener {
            val url = binding.supabaseUrlEt.text.toString().trim()
            val key = binding.supabaseKeyEt.text.toString().trim()
            if (url.isBlank() || key.isBlank()) {
                binding.connectionStatusTv.text = "Please enter URL and API key"
                return@setOnClickListener
            }
            binding.connectionStatusTv.text = "Testing..."
            // Connection test is triggered via result intent — the app module handles the actual HTTP call
            val resultIntent = Intent().apply {
                putExtra("action", "test_connection")
                putExtra(KEY_SUPABASE_URL, url)
                putExtra(KEY_SUPABASE_KEY, key)
            }
            setResult(RESULT_FIRST_USER, resultIntent)
            binding.connectionStatusTv.text = "Save settings and re-open to test (handled by app module)"
        }

        binding.batteryOptBtn.setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }

        binding.saveBtn.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveSettings() {
        prefs.edit().apply {
            putString(KEY_SUPABASE_URL, binding.supabaseUrlEt.text.toString().trim())
            putString(KEY_SUPABASE_KEY, binding.supabaseKeyEt.text.toString().trim())
            putString(KEY_WHISPER_MODEL, binding.whisperModelSpinner.selectedItem.toString())
            putString(KEY_LLM_MODEL, binding.llmModelEt.text.toString().trim())
            putInt(KEY_WHISPER_THREADS, maxOf(1, binding.whisperThreadsSeekBar.progress))
            putInt(KEY_LLM_THREADS, maxOf(1, binding.llmThreadsSeekBar.progress))
            putString(KEY_WAKE_WORD, binding.wakeWordEt.text.toString().trim())
            putString(KEY_SLEEP_WORD, binding.sleepWordEt.text.toString().trim())
            apply()
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
            "Battery optimisation: NOT exempt (tap button above)"
        }
    }
}
