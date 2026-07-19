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

        // Get screen size and create paper texture
        binding.root.post {
            val width = binding.root.width
            val height = binding.root.height
            if (width > 0 && height > 0) {
                val paperBitmap = PaperRenderer.createPaperBitmap(width, height)
                binding.paperImage.setImageBitmap(paperBitmap)
            }
        }
    }
}