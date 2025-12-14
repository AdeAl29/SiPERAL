package com.ade.flipbook

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

// Model Data untuk satu halaman PDF
data class PdfPageData(
    val bitmap: Bitmap,
    val text: String
)

// ViewModel untuk menyimpan status aplikasi agar tidak hilang saat rotasi layar
class PdfViewModel : ViewModel() {
    // Status Halaman
    var totalPages by mutableIntStateOf(0)
    var currentPageIndex by mutableIntStateOf(0)
    var isLoading by mutableStateOf(false)
    var pdfFileName by mutableStateOf("Memuat...")

    // Penyimpanan Data Halaman (Cache)
    // Key: Nomor Halaman (Int), Value: Data (Gambar & Teks)
    val pageCache = mutableStateMapOf<Int, PdfPageData>()

    // Status Pencarian
    var searchQuery by mutableStateOf("")
    val searchResults = mutableStateListOf<Int>() // Menggunakan list state agar UI update otomatis
    var isSearchDialogOpen by mutableStateOf(false)

    // Status Zoom
    var scale by mutableFloatStateOf(1f)

    // Status Suara (TTS)
    var isSpeaking by mutableStateOf(false)
}