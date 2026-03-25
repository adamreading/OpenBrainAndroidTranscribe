package com.openbrain.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.openbrain.ui.databinding.ActivityAdminBinding

class AdminActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAdminBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
    }

    private fun setupUI() {
        binding.testConnectionBtn.setOnClickListener {
            // TODO: Implement Supabase connection test
        }
    }
}
