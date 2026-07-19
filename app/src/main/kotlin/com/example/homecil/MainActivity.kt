package com.example.homecil

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import androidx.appcompat.app.AppCompatActivity
import com.example.homecil.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.root.post {
            val width = binding.root.width
            val height = binding.root.height
            if (width > 0 && height > 0) {
                val density = resources.displayMetrics.density   // dp scale factor
                val mmToPx = resources.displayMetrics.densityDpi / 25.4f

                // Notebook geometry (in mm)
                val lineSpacingMm = 8f
                val marginMm = 25f

                val lineSpacingPx = (lineSpacingMm * mmToPx).toInt().coerceAtLeast(4)
                val marginPx = (marginMm * mmToPx).toInt()

                // Line thickness: 2dp converted to px, minimum 2px
                val lineThicknessDp = 2f
                val lineThicknessPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    lineThicknessDp,
                    resources.displayMetrics
                ).toInt().coerceAtLeast(2)

                val lineColor = Color.rgb(0, 0x66, 0xCC)
                val marginColor = Color.RED

                val bitmap = PaperRenderer.createNotebookPaper(
                    widthPx = width,
                    heightPx = height,
                    lineSpacingPx = lineSpacingPx,
                    marginLeftPx = marginPx,
                    lineThicknessPx = lineThicknessPx,
                    lineColor = lineColor,
                    marginColor = marginColor
                )
                binding.paperImage.setImageBitmap(bitmap)
            }
        }
    }
}