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
    private var currentX = 200f
    private var currentY = 400f
    private val textPaint = Paint().apply {
        textSize = 64f
        typeface = android.graphics.Typeface.DEFAULT
        isAntiAlias = true
    }
    private val charWidths = mutableListOf<Float>()  // to keep track of character advances
    private var previousTextLength = 0

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
        // Align writing baseline to notebook line if LINED paper
        currentY = binding.paperView.snapToLine(currentY)

        binding.invisibleInput.visibility = View.VISIBLE
        binding.invisibleInput.x = currentX
        binding.invisibleInput.y = currentY
        binding.invisibleInput.setText("")
        binding.invisibleInput.requestFocus()
        showKeyboard()

        charWidths.clear()
        previousTextLength = 0

        binding.invisibleInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: return
                val len = text.length

                if (len > previousTextLength) {
                    // New character(s) added
                    val newChar = text.last().toString()
                    val stroke = HandwritingRenderer.createInkStroke(newChar, currentX, currentY, textPaint)
                    if (stroke != null) {
                        binding.paperView.addInkStroke(stroke)
                        val advance = textPaint.measureText(newChar)
                        currentX += advance
                        charWidths.add(advance)
                        // Move invisible EditText to keep cursor aligned
                        binding.invisibleInput.x = currentX
                    }
                } else if (len < previousTextLength) {
                    // Backspace pressed – remove last character
                    if (charWidths.isNotEmpty()) {
                        val removedWidth = charWidths.removeLast()
                        currentX -= removedWidth
                        binding.invisibleInput.x = currentX
                        binding.paperView.removeLastStroke()
                    }
                }
                previousTextLength = len
            }
        })
    }

    private fun stopWriteMode() {
        hideKeyboard()
        binding.invisibleInput.visibility = View.GONE
        binding.invisibleInput.clearFocus()
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