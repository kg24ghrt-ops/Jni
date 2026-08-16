package com.example.homecil

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.content.ContentValues
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.homecil.databinding.ActivityPaperGeneratorBinding
import kotlin.random.Random

class PaperGeneratorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPaperGeneratorBinding
    private val viewModel: PaperViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaperGeneratorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.btnGenerate.setOnClickListener {
            val params = PaperParams(
                grain = binding.sliderGrain.value,
                water = binding.sliderWaterStains.value.toInt(),
                seed = Random.nextInt() // Ensure deterministic but varied seeds
            )
            viewModel.generate(params)
        }

        binding.btnExport.setOnClickListener {
            viewModel.bitmapLiveData.value?.let { exportToGallery(it) }
        }
    }

    private fun observeViewModel() {
        viewModel.bitmapLiveData.observe(this) { bitmap ->
            binding.previewImage.setImageBitmap(bitmap)
            // Reset matrix to fit screen when a new image is generated
            binding.previewImage.apply {
                // Add simple reset logic here if needed
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnGenerate.isEnabled = !isLoading
        }
    }

    private fun exportToGallery(bitmap: Bitmap) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Paper_${System.currentTimeMillis()}.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/HomecilPaper")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(it, contentValues, null, null)
            
            Toast.makeText(this, "Saved to Pictures/HomecilPaper", Toast.LENGTH_SHORT).show()
        }
    }
}