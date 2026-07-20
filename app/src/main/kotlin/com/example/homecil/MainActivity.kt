package com.example.homecil

import android.graphics.Paint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import com.example.homecil.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var writeMode = false
    private var lineStartX = 200f
    private var lineBaselineY = 400f
    private val textPaint = Paint().apply {
        textSize = 64f
        typeface = android.graphics.Typeface.DEFAULT
        isAntiAlias = true
    }

    private val lineStrokes = mutableListOf<InkStroke>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Paper style toggle
        binding.fabTogglePaper.setOnClickListener {
            binding.paperView.paperStyle = when (binding.paperView.paperStyle) {
                PaperStyle.REALISTIC -> PaperStyle.PLAIN
                PaperStyle.PLAIN -> PaperStyle.LINED
                PaperStyle.LINED -> PaperStyle.REALISTIC
            }
        }

        // Write mode toggle
        binding.fabWriteMode.setOnClickListener {
            writeMode = !writeMode
            if (writeMode) {
                startWriteMode()
            } else {
                stopWriteMode()
            }
        }
    }

    private fun startWriteMode() {
        lineBaselineY = binding.paperView.snapToLine(lineBaselineY)

        binding.invisibleInput.visibility = View.VISIBLE
        binding.invisibleInput.x = lineStartX
        binding.invisibleInput.y = lineBaselineY
        binding.invisibleInput.setText("")
        binding.invisibleInput.requestFocus()
        showKeyboard()

        // Clear previous line
        binding.paperView.clearAllInk()
        lineStrokes.clear()

        binding.invisibleInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                updateLine(text)
            }
        })
    }

    private fun updateLine(text: String) {
        // Remove all current line strokes from the paper view
        binding.paperView.clearAllInk()
        lineStrokes.clear()

        if (text.isEmpty()) return

        // Create new strokes for the whole text using the new cluster‑based method
        val strokes = HandwritingRenderer.createClusterStrokes(
            text, lineStartX, lineBaselineY, textPaint
        )
        lineStrokes.addAll(strokes)

        // Stamp each stroke onto the paper
        for (stroke in lineStrokes) {
            binding.paperView.addInkStroke(stroke)
        }
    }

    private fun stopWriteMode() {
        hideKeyboard()
        binding.invisibleInput.visibility = View.GONE
        binding.invisibleInput.clearFocus()
        // Ink remains on paper
    }

    private fun showKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.invisibleInput, 0)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.invisibleInput.windowToken, 0)
    }
}