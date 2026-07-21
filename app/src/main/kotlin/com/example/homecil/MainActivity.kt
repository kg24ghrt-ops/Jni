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
    private var mathMode = false              // <-- new
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

        // Write mode toggle (text)
        binding.fabWriteMode.setOnClickListener {
            mathMode = false
            writeMode = !writeMode
            if (writeMode) startWriteMode() else stopWriteMode()
        }

        // Math mode toggle
        binding.fabMathMode.setOnClickListener {
            mathMode = !mathMode
            if (mathMode) {
                writeMode = false
                stopWriteMode()
                startMathMode()
            } else {
                stopMathMode()
            }
        }
    }

    private fun startWriteMode() { /* ... same as before ... */ }
    private fun stopWriteMode() { /* ... same as before ... */ }

    // ----- Math mode -----
    private fun startMathMode() {
        lineBaselineY = binding.paperView.snapToLine(lineBaselineY)

        binding.invisibleInput.visibility = View.VISIBLE
        binding.invisibleInput.x = lineStartX
        binding.invisibleInput.y = lineBaselineY
        binding.invisibleInput.setText("")
        binding.invisibleInput.requestFocus()
        showKeyboard()

        binding.paperView.clearAllInk()
        lineStrokes.clear()

        binding.invisibleInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val latex = s?.toString() ?: ""
                updateMath(latex)
            }
        })
    }

    private fun updateMath(latex: String) {
        binding.paperView.clearAllInk()
        lineStrokes.clear()
        if (latex.isEmpty()) return

        val stroke = MathRenderer.createMathStroke(
            latex, lineStartX, lineBaselineY,
            textPaint.textSize,
            seed = latex.hashCode()   // different each time
        )
        if (stroke != null) {
            lineStrokes.add(stroke)
            binding.paperView.addInkStroke(stroke)
        }
    }

    private fun stopMathMode() {
        hideKeyboard()
        binding.invisibleInput.visibility = View.GONE
        binding.invisibleInput.clearFocus()
    }

    // ... (existing methods for text writing, keyboard, etc.)
}