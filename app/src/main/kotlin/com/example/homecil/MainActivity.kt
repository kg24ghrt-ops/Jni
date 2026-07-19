package com.example.homecil

import android.graphics.Paint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
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
    private val charWidths = mutableListOf<Float>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Paper style toggle (existing)
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
        binding.invisibleInput.visibility = View.VISIBLE
        // Position the EditText at the writing start
        binding.invisibleInput.x = currentX
        binding.invisibleInput.y = currentY
        binding.invisibleInput.setText("")
        binding.invisibleInput.requestFocus()
        showKeyboard()

        // Track text changes
        binding.invisibleInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: return
                if (text.length > charWidths.size) {
                    // New character added
                    val newChar = text.last().toString()
                    val advance = HandwritingRenderer.stampCharacter(
                        binding.paperView.paperBitmap!!,
                        newChar,
                        currentX,
                        currentY,
                        textPaint
                    )
                    currentX += advance
                    charWidths.add(advance)
                    // Move the EditText so the cursor follows the end
                    binding.invisibleInput.x = currentX
                    // Keep text in EditText so cursor is at end, but make it invisible
                } else if (text.length < charWidths.size) {
                    // Backspace – simplistic: reset everything (you can improve later)
                    resetWriting()
                }
            }
        })
    }

    private fun stopWriteMode() {
        hideKeyboard()
        binding.invisibleInput.visibility = View.GONE
        binding.invisibleInput.clearFocus()
        // Remove text watcher? We'll just ignore if not in write mode.
    }

    private fun resetWriting() {
        // Reset writing position and clear ink? Not implemented here.
        // For now just keep the ink on paper.
        charWidths.clear()
        currentX = 200f
        binding.invisibleInput.setText("")
    }

    private fun showKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(binding.invisibleInput, 0)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.invisibleInput.windowToken, 0)
    }
}