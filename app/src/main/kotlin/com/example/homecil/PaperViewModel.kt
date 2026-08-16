package com.example.homecil

import android.graphics.Bitmap
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

data class PaperParams(
    val width: Int = 1024, val height: Int = 1024, val seed: Int = 12345,
    val grain: Float = 0.5f, val fiber: Float = 0.5f, val water: Int = 2,
    val aging: Float = 0.3f, val direction: Float = 1.5f, val roughness: Float = 0.5f
)

class PaperViewModel : ViewModel() {
    private var engineHandle: Long = 0
    val bitmapLiveData = MutableLiveData<Bitmap?>()
    val isLoading = MutableLiveData(false)

    init {
        engineHandle = PaperEngine.createHeadless(1024, 1024)
    }

    fun generate(params: PaperParams) {
        viewModelScope.launch(Dispatchers.IO) {
            isLoading.postValue(true)
            val result = PaperEngine.generate(
                engineHandle, params.width, params.height, params.seed,
                params.grain, params.fiber, params.water, params.aging, params.direction, params.roughness
            )

            if (result == 0) {
                val bufferSize = params.width * params.height * 4
                val buffer = ByteBuffer.allocateDirect(bufferSize)
                if (PaperEngine.readPixels(engineHandle, buffer, bufferSize) == 0) {
                    val bitmap = Bitmap.createBitmap(params.width, params.height, Bitmap.Config.ARGB_8888)
                    buffer.rewind()
                    bitmap.copyPixelsFromBuffer(buffer)
                    bitmapLiveData.postValue(bitmap)
                }
            }
            isLoading.postValue(false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (engineHandle != 0L) PaperEngine.destroy(engineHandle)
    }
}