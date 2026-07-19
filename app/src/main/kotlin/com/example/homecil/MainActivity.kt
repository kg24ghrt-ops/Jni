package com.example.homecil

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.homecil.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.fabTogglePaper.setOnClickListener {
            // Cycle through REALISTIC -> PLAIN -> LINED -> REALISTIC
            binding.paperView.paperStyle = when (binding.paperView.paperStyle) {
                PaperStyle.REALISTIC -> PaperStyle.PLAIN
                PaperStyle.PLAIN -> PaperStyle.LINED
                PaperStyle.LINED -> PaperStyle.REALISTIC
            }
        }
    }
}