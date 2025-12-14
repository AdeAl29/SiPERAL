package com.ade.flipbook

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ade.flipbook.ui.theme.FlipBookTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.absoluteValue

// ---------------------------------------------------------
// DATA STRUKTUR
// ---------------------------------------------------------

// Data Bab Buku
data class Chapter(
    val title: String,
    val estimatedPage: Int
)

// Data Track Audio
data class AudioTrack(
    val name: String,
    val resId: Int
)

val RAW_CHAPTERS = listOf(
    Chapter("Bab 1: Makhluk Hidup", 0),
    Chapter("   A. Ciri-Ciri Makhluk Hidup", 0),
    Chapter("   B. Pengelompokan Tumbuhan dan Hewan", 6),
    Chapter("Bab 2: Hewan", 12),
    Chapter("   A. Pengelompokan Hewan Berdasarkan Tulang Belakangnya", 12),
    Chapter("   B. Perkembangbiakan Hewan", 24),
    Chapter("   C. Metamorfosis Hewan", 27),
    Chapter("Bab 3: Manusia", 30),
    Chapter("   A. Sistem Gerak Pada Manusia", 30),
    Chapter("   B. Organ Dalam Tubuh Manusia", 44),
    Chapter("   C. Alat-Alat Indra Manusia", 50),
    Chapter("   D. Sistem Peredaran Darah Manusia", 64),
    Chapter("Bab 4: Tumbuhan", 74),
    Chapter("   A. Bagian-Bagian Pada Tumbuhan", 74),
    Chapter("   B. Pengelompokan Tumbuhan", 83),
    Chapter("   C. Perkembangbiakan Tumbuhan", 85),
    Chapter("   D. Manfaat Tumbuhan", 94),
    Chapter("Bab 5: Makanan dan Kesehatan", 96),
    Chapter("Bab 6: Sistem Ekskresi", 106),
    Chapter("Bab 7: Sistem Pernapasan", 124),
    Chapter("Bab 8: Penyakit", 140),
    Chapter("Bab 9: Lingkungan", 150),
    Chapter("Bab 10: Hubungan Antarmakhluk", 166),
    Chapter("Bab 11: Adaptasi", 176),
    Chapter("Bab 12: Air", 188),
    Chapter("Bab 13: Peristiwa Alam", 202),
    Chapter("Bab 14: Udara", 214),
    Chapter("Bab 15: Tanah", 224),
    Chapter("Bab 16: Batuan", 234),
    Chapter("Bab 17: Sumber Daya Alam", 244),
    Chapter("Bab 18: Gaya", 256),
    Chapter("Bab 19: Pesawat Sederhana", 262),
    Chapter("Bab 20: Energi", 270),
    Chapter("Bab 21: Cahaya", 276),
    Chapter("Bab 22: Panas", 288),
    Chapter("Bab 23: Bunyi", 296),
    Chapter("Bab 24: Magnet", 302),
    Chapter("Bab 25: Listrik", 308),
    Chapter("Bab 26: Suhu", 318),
    Chapter("Bab 27: Tata Surya", 322),
    Chapter("Bab 28: Gerak Langit", 338)
)

// ---------------------------------------------------------
// 1. ACTIVITY UTAMA
// ---------------------------------------------------------
class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Force Landscape agar nyaman dibaca seperti buku
        this.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        tts = TextToSpeech(this, this)
        setContent {
            FlipBookTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    FlipbookApp(tts)
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("id", "ID"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
        }
    }

    override fun onDestroy() {
        if (tts != null) {
            tts?.stop()
            tts?.shutdown()
        }
        super.onDestroy()
    }
}

// ---------------------------------------------------------
// 2. STATE MANAGER
// ---------------------------------------------------------

enum class InteractionMode {
    NORMAL,
    ZOOM_IN,
    ZOOM_OUT
}

class PdfState {
    var pdfRenderer: PdfRenderer? by mutableStateOf(null)
    var totalPages by mutableIntStateOf(0)
    var totalSpreads by mutableIntStateOf(0)
    var currentSpreadIndex by mutableIntStateOf(0)
    var pdfFileName by mutableStateOf("Memuat...")

    // --- UI State: Zoom & Pan ---
    var scale by mutableFloatStateOf(1f)
    var offsetX by mutableFloatStateOf(0f)
    var offsetY by mutableFloatStateOf(0f)

    // --- Audio State ---
    var mediaPlayer: MediaPlayer? by mutableStateOf(null)
    var isAudioPlaying by mutableStateOf(false)
    var currentTrackName by mutableStateOf("") // Nama file yang sedang diputar
    var volume by mutableFloatStateOf(0.5f) // Default Volume 50%
    var isAudioSelectionOpen by mutableStateOf(false) // Popup pemilihan lagu

    // DAFTAR AUDIO (Pastikan file ada di res/raw)
    val audioTracks = listOf(
        AudioTrack("Penjelasan Bab 1", R.raw.bab_1),
        AudioTrack("Penjelasan Bab 2", R.raw.bab_2),
        AudioTrack("Penjelasan Bab 3", R.raw.bab_3),
        AudioTrack("Penjelasan Bab 4", R.raw.bab_4)
    )
    // -------------------

    var isSearchDialogOpen by mutableStateOf(false)
    var isSearchResultsOpen by mutableStateOf(false)
    var searchQuery by mutableStateOf("")

    var interactionMode by mutableStateOf(InteractionMode.NORMAL)

    val searchResults = mutableStateListOf<Chapter>()
    val mappedChapters = mutableStateListOf<Chapter>()
    private var fileDescriptor: ParcelFileDescriptor? = null

    // --- PDF LOGIC ---
    suspend fun renderPage(index: Int): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                pdfRenderer?.let { renderer ->
                    if (index < 0 || index >= renderer.pageCount) return@let null
                    val page = renderer.openPage(index)
                    val w = page.width * 2
                    val h = page.height * 2
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    return@let bitmap
                }
            } catch (e: Exception) { null }
        }
    }

    fun performLinearSearch(context: Context, query: String) {
        if (query.isBlank()) return
        searchResults.clear()
        val lowerQuery = query.lowercase()
        for (chapter in mappedChapters) {
            if (chapter.title.lowercase().contains(lowerQuery)) searchResults.add(chapter)
        }
        isSearchDialogOpen = false
        if (searchResults.isNotEmpty()) isSearchResultsOpen = true
        else Toast.makeText(context, "Materi tidak ditemukan.", Toast.LENGTH_SHORT).show()
    }

    fun openPdf(file: File) {
        try {
            closePdf()
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            fileDescriptor = fd
            val renderer = PdfRenderer(fd)
            pdfRenderer = renderer
            totalPages = renderer.pageCount
            totalSpreads = (totalPages + 1) / 2
            pdfFileName = file.name
            mappedChapters.clear()
            mappedChapters.addAll(RAW_CHAPTERS)
        } catch (e: Exception) { pdfFileName = "File Rusak" }
    }

    fun closePdf() {
        try {
            pdfRenderer?.close()
            fileDescriptor?.close()
            pdfRenderer = null
        } catch (e: Exception) {}
    }

    // --- AUDIO FUNCTIONS (UPDATED) ---

    // 1. Putar Track Spesifik (Bab 1, 2, 3, atau 4)
    fun playSpecificTrack(context: Context, track: AudioTrack) {
        try {
            releaseAudio() // Stop yang lama

            mediaPlayer = MediaPlayer.create(context, track.resId)
            mediaPlayer?.let { player ->
                player.setVolume(volume, volume) // Set volume sesuai settingan
                player.setOnCompletionListener {
                    isAudioPlaying = false
                }
                player.start()
                isAudioPlaying = true
                currentTrackName = track.name
            }
            isAudioSelectionOpen = false // Tutup dialog
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Gagal memutar audio. Pastikan file ada di res/raw", Toast.LENGTH_LONG).show()
        }
    }

    // 2. Play/Pause Toggle
    fun toggleAudio() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                isAudioPlaying = false
            } else {
                player.start()
                isAudioPlaying = true
            }
            return
        }
        // Jika belum ada lagu, buka menu pilih lagu
        isAudioSelectionOpen = true
    }

    // 3. Atur Volume
    fun adjustVolume(delta: Float) {
        volume = (volume + delta).coerceIn(0f, 1f) // Limit antara 0.0 - 1.0
        mediaPlayer?.setVolume(volume, volume)
    }

    // 4. Bersihkan Audio
    fun releaseAudio() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isAudioPlaying = false
        currentTrackName = ""
    }
}

// ---------------------------------------------------------
// 3. UI UTAMA
// ---------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FlipbookApp(tts: TextToSpeech?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { PdfState() }
    val pagerState = rememberPagerState(pageCount = { state.totalSpreads })

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { scope.launch { loadPdfFromUri(context, it, state) } }
    }

    // Load Default PDF
    LaunchedEffect(Unit) {
        if (state.pdfRenderer == null) loadPdfFromAssets(context, "rpal.pdf", state)
    }

    // Cleanup Audio saat layar ditutup
    DisposableEffect(Unit) {
        onDispose { state.releaseAudio() }
    }

    // Reset Zoom saat ganti halaman
    LaunchedEffect(pagerState.currentPage) {
        state.currentSpreadIndex = pagerState.currentPage
        state.scale = 1f
        state.offsetX = 0f
        state.offsetY = 0f
        state.interactionMode = InteractionMode.NORMAL
    }

    // Sinkronisasi tombol prev/next dengan Pager
    LaunchedEffect(state.currentSpreadIndex) {
        if (pagerState.currentPage != state.currentSpreadIndex && state.totalSpreads > 0) {
            pagerState.scrollToPage(state.currentSpreadIndex)
        }
    }

    Scaffold(
        bottomBar = {
            BottomControls(state,
                onSearchClick = { state.isSearchDialogOpen = true },
                onFileClick = { launcher.launch("application/pdf") }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Brush.radialGradient(
                    colors = listOf(Color(0xFF8D6E63), Color(0xFF3E2723)),
                    radius = 1200f
                )),
            contentAlignment = Alignment.Center
        ) {
            if (state.pdfRenderer != null) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = state.scale == 1f, // Disable swipe saat zoom
                    contentPadding = PaddingValues(0.dp),
                    pageSpacing = 0.dp
                ) { spreadIndex ->

                    // --- ANIMASI 3D BUKU ---
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex((state.totalSpreads - spreadIndex.toFloat()).coerceAtLeast(0f))
                            .graphicsLayer {
                                val pageOffset = (pagerState.currentPage - spreadIndex) + pagerState.currentPageOffsetFraction
                                translationX = pageOffset * size.width * -1
                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                                cameraDistance = 16 * density
                                rotationY = pageOffset * -180f
                                alpha = if (rotationY.absoluteValue > 90f) 0f else 1f
                            }
                    ) {
                        Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                            BookSpreadPage(spreadIndex, state)
                        }
                    }
                }
            } else {
                CircularProgressIndicator(color = Color.White)
            }

            // --- DIALOGS ---
            if (state.isSearchDialogOpen) {
                SearchInputDialog(state, { state.isSearchDialogOpen = false }, { state.performLinearSearch(context, it) })
            }
            if (state.isSearchResultsOpen) {
                SearchResultsDialog(state, { state.isSearchResultsOpen = false }, {
                    state.currentSpreadIndex = it / 2
                    state.isSearchResultsOpen = false
                })
            }
            if (state.isAudioSelectionOpen) {
                AudioSelectionDialog(state, context) { state.isAudioSelectionOpen = false }
            }
        }
    }
}

// ---------------------------------------------------------
// 4. UI COMPONENTS
// ---------------------------------------------------------

@Composable
fun BookSpreadPage(spreadIndex: Int, state: PdfState) {
    val leftPageIndex = spreadIndex * 2
    val rightPageIndex = spreadIndex * 2 + 1
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val dragModifier = if (state.scale > 1f) {
        Modifier.pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                val maxX = (containerSize.width * state.scale - containerSize.width) / 2
                val maxY = (containerSize.height * state.scale - containerSize.height) / 2
                state.offsetX = (state.offsetX + dragAmount.x).coerceIn(-maxX, maxX)
                state.offsetY = (state.offsetY + dragAmount.y).coerceIn(-maxY, maxY)
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .then(dragModifier)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        val currentScale = state.scale
                        var newScale = currentScale

                        when (state.interactionMode) {
                            InteractionMode.ZOOM_IN -> newScale = (currentScale + 0.75f).coerceAtMost(4f)
                            InteractionMode.ZOOM_OUT -> newScale = (currentScale - 0.75f).coerceAtLeast(1f)
                            else -> return@detectTapGestures
                        }

                        if (newScale <= 1f) {
                            state.scale = 1f
                            state.offsetX = 0f
                            state.offsetY = 0f
                            state.interactionMode = InteractionMode.NORMAL
                            return@detectTapGestures
                        }

                        // Zoom Logic Center
                        val zoomFactor = newScale / currentScale
                        if (newScale > currentScale) {
                            val centerX = containerSize.width / 2f
                            val centerY = containerSize.height / 2f
                            val dx = (centerX - tapOffset.x)
                            val dy = (centerY - tapOffset.y)
                            state.offsetX = (state.offsetX * zoomFactor) + (dx * (zoomFactor - 1))
                            state.offsetY = (state.offsetY * zoomFactor) + (dy * (zoomFactor - 1))
                        } else {
                            state.offsetX *= zoomFactor
                            state.offsetY *= zoomFactor
                        }

                        state.scale = newScale
                        val maxX = (containerSize.width * state.scale - containerSize.width) / 2
                        val maxY = (containerSize.height * state.scale - containerSize.height) / 2
                        state.offsetX = state.offsetX.coerceIn(-maxX, maxX)
                        state.offsetY = state.offsetY.coerceIn(-maxY, maxY)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .graphicsLayer(
                    scaleX = state.scale,
                    scaleY = state.scale,
                    translationX = state.offsetX,
                    translationY = state.offsetY,
                    shadowElevation = 30f
                ),
            shape = RoundedCornerShape(4.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 0.5.dp)) {
                    if (leftPageIndex < state.totalPages) {
                        PdfSinglePageRenderer(leftPageIndex, state)
                        Box(modifier = Modifier.align(Alignment.CenterEnd).width(24.dp).fillMaxHeight()
                            .background(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(0.15f)))))
                        Text("${leftPageIndex + 1}", modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Box(modifier = Modifier.width(6.dp).fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(Color(0xFF8D6E63), Color(0xFF5D4037), Color(0xFF8D6E63)))))
                Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 0.5.dp)) {
                    if (rightPageIndex < state.totalPages) {
                        PdfSinglePageRenderer(rightPageIndex, state)
                        Box(modifier = Modifier.align(Alignment.CenterStart).width(24.dp).fillMaxHeight()
                            .background(Brush.horizontalGradient(listOf(Color.Black.copy(0.15f), Color.Transparent))))
                        Text("${rightPageIndex + 1}", modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun PdfSinglePageRenderer(pageIndex: Int, state: PdfState) {
    val bitmapState = produceState<Bitmap?>(initialValue = null, key1 = pageIndex) {
        value = state.renderPage(pageIndex)
    }
    if (bitmapState.value != null) {
        Image(
            bitmap = bitmapState.value!!.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

// --- DIALOGS ---

@Composable
fun SearchInputDialog(state: PdfState, onDismiss: () -> Unit, onSearch: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cari Bab/Materi") },
        text = {
            Column {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { state.searchQuery = it },
                    label = { Text("Contoh: 'Manusia'") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { Button(onClick = { onSearch(state.searchQuery) }) { Text("Cari") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
fun SearchResultsDialog(state: PdfState, onDismiss: () -> Unit, onJumpToPage: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hasil Pencarian") },
        text = {
            Column(modifier = Modifier.heightIn(max = 300.dp)) {
                Text("Ditemukan ${state.searchResults.size} materi terkait:")
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(state.searchResults) { chapter ->
                        Card(
                            onClick = { onJumpToPage(chapter.estimatedPage) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Column(Modifier.padding(12.dp).fillMaxWidth()) {
                                Text(chapter.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("Halaman: ${chapter.estimatedPage + 1}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
fun AudioSelectionDialog(state: PdfState, context: Context, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih Suara Penjelasan") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.audioTracks) { track ->
                    Card(
                        onClick = { state.playSpecificTrack(context, track) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (state.currentTrackName == track.name)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Audiotrack, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(track.name, style = MaterialTheme.typography.bodyMedium)
                            if (state.currentTrackName == track.name && state.isAudioPlaying) {
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Default.VolumeUp, "Playing", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
fun BottomControls(state: PdfState, onSearchClick: () -> Unit, onFileClick: () -> Unit) {
    BottomAppBar(
        containerColor = Color(0xFF3E2723),
        contentColor = Color.White,
        modifier = Modifier.height(64.dp)
    ) {
        // --- SEARCH ---
        IconButton(onClick = onSearchClick) {
            Icon(Icons.Default.Search, "Cari Bab", tint = Color(0xFFFFD54F))
        }

        Spacer(Modifier.width(8.dp))

        // --- VOLUME CONTROLS (BARU) ---
        IconButton(onClick = { state.adjustVolume(-0.1f) }) {
            Icon(Icons.Default.VolumeDown, "Vol -", tint = Color.White)
        }

        // Indikator Volume Sederhana (Text)
        Text(
            text = "${(state.volume * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.width(32.dp).wrapContentWidth(Alignment.CenterHorizontally)
        )

        IconButton(onClick = { state.adjustVolume(0.1f) }) {
            Icon(Icons.Default.VolumeUp, "Vol +", tint = Color.White)
        }

        Spacer(Modifier.width(8.dp))

        // --- PLAY / AUDIO SELECT ---
        IconButton(onClick = { state.toggleAudio() }) {
            val icon = if (state.isAudioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow
            val tintColor = if (state.isAudioPlaying) Color.Green else Color.White

            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = "Audio", tint = tintColor)
            }
        }

        // Tombol Playlist (Ganti Bab Suara)
        IconButton(onClick = { state.isAudioSelectionOpen = true }) {
            Icon(Icons.Default.QueueMusic, "Ganti Track", tint = Color(0xFF80CBC4))
        }

        // --- ZOOM CONTROLS ---
        Spacer(Modifier.width(16.dp))
        IconButton(onClick = {
            state.interactionMode = if (state.interactionMode == InteractionMode.ZOOM_OUT) InteractionMode.NORMAL else InteractionMode.ZOOM_OUT
        }) {
            Icon(
                imageVector = Icons.Default.ZoomOut,
                contentDescription = "Mode Perkecil",
                tint = if (state.interactionMode == InteractionMode.ZOOM_OUT) Color.Yellow else Color.White
            )
        }

        IconButton(onClick = {
            state.interactionMode = if (state.interactionMode == InteractionMode.ZOOM_IN) InteractionMode.NORMAL else InteractionMode.ZOOM_IN
        }) {
            Icon(
                imageVector = Icons.Default.ZoomIn,
                contentDescription = "Mode Perbesar",
                tint = if (state.interactionMode == InteractionMode.ZOOM_IN) Color.Yellow else Color.White
            )
        }

        // --- NAVIGATION & INFO ---
        Spacer(Modifier.width(16.dp))
        IconButton(onClick = { if (state.currentSpreadIndex > 0) state.currentSpreadIndex-- }) {
            Icon(Icons.Default.ArrowBack, "Prev")
        }

        Spacer(Modifier.weight(1f))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(state.pdfFileName, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = Color.White.copy(0.7f))
            Text(
                "${state.currentSpreadIndex * 2 + 1}-${(state.currentSpreadIndex * 2 + 2).coerceAtMost(state.totalPages)} / ${state.totalPages}",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.weight(1f))
        IconButton(onClick = { if (state.currentSpreadIndex < state.totalSpreads - 1) state.currentSpreadIndex++ }) {
            Icon(Icons.Default.ArrowForward, "Next")
        }

        Spacer(Modifier.width(16.dp))
        IconButton(onClick = onFileClick) {
            Icon(Icons.Default.FolderOpen, "Buka File")
        }
    }
}

// ---------------------------------------------------------
// 5. HELPER LOAD FILES
// ---------------------------------------------------------

suspend fun loadPdfFromAssets(context: Context, fileName: String, state: PdfState) {
    withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, fileName)
            if (!file.exists()) {
                val assetManager = context.assets
                val list = assetManager.list("") ?: return@withContext
                if (!list.contains(fileName)) {
                    withContext(Dispatchers.Main) { state.pdfFileName = "File tidak ditemukan" }
                    return@withContext
                }
                val inputStream = assetManager.open(fileName)
                val outputStream = FileOutputStream(file)
                inputStream.copyTo(outputStream)
                inputStream.close()
                outputStream.close()
            }
            withContext(Dispatchers.Main) { state.openPdf(file) }
        } catch (e: Exception) { e.printStackTrace() }
    }
}

suspend fun loadPdfFromUri(context: Context, uri: Uri, state: PdfState) {
    withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val tempFile = File(context.cacheDir, "temp.pdf")
            val outputStream = FileOutputStream(tempFile)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            withContext(Dispatchers.Main) { state.openPdf(tempFile) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
