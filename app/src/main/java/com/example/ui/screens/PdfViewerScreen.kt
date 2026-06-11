package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

// Helper function to safely copy selected PDF to internal storage to avoid permission expiration
private fun copyUriToInternalStorage(context: android.content.Context, uri: Uri, folderName: String, prefix: String): Uri? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val folder = java.io.File(context.filesDir, folderName)
        if (!folder.exists()) folder.mkdirs()
        
        val uniqueName = "${prefix}_${System.currentTimeMillis()}.pdf"
        val destFile = java.io.File(folder, uniqueName)
        
        java.io.FileOutputStream(destFile).use { outputStream ->
            inputStream.use { input ->
                input.copyTo(outputStream)
            }
        }
        Uri.fromFile(destFile)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    pdfName: String,
    pdfUriString: String?,
    pageNumber: Int,
    onBack: () -> Unit,
    onBindPdfUri: (String) -> Unit
) {
    val context = LocalContext.current
    var pdfBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var totalPages by remember { mutableStateOf(1) }
    var pageNum by remember { mutableStateOf(pageNumber) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }

    // Gesture Zoom/Move states
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    // SAF File Picker launcher to attach a real PDF file
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val localUri = copyUriToInternalStorage(context, uri, "pdfs", "pdf")
            if (localUri != null) {
                onBindPdfUri(localUri.toString())
            } else {
                onBindPdfUri(uri.toString())
            }
        }
    }

    // Load PDF Page asynchronously
    LaunchedEffect(pdfUriString, pageNum) {
        if (pdfUriString.isNullOrEmpty()) {
            pdfBitmap = null
            return@LaunchedEffect
        }
        
        isLoading = true
        loadError = null
        pdfBitmap = null

        withContext(Dispatchers.IO) {
            try {
                val pdfUri = Uri.parse(pdfUriString)
                val parcelFD = if (pdfUri.scheme == "file" || pdfUriString.startsWith("/")) {
                    val file = if (pdfUri.scheme == "file") File(pdfUri.path ?: "") else File(pdfUriString)
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                } else {
                    context.contentResolver.openFileDescriptor(pdfUri, "r")
                }
                if (parcelFD != null) {
                    val renderer = PdfRenderer(parcelFD)
                    totalPages = renderer.pageCount
                    
                    val targetPage = (pageNum - 1).coerceIn(0, totalPages - 1)
                    val pdfPage = renderer.openPage(targetPage)

                    // Render page at a resolution matched to the actual screen width, with a hard
                    // upper bound. This keeps the bitmap sharp while avoiding huge allocations /
                    // out-of-memory crashes and slow rendering on dense displays.
                    val screenWidthPx = context.resources.displayMetrics.widthPixels
                    val maxWidth = (screenWidthPx * 2).coerceAtMost(2480) // ~2x for crisp zoom, capped
                    val aspect = pdfPage.height.toFloat() / pdfPage.width.toFloat()
                    val width = pdfPage.width.coerceAtLeast(1).let { pw ->
                        minOf(maxWidth, (pw * 2)).coerceAtLeast(1)
                    }
                    val height = (width * aspect).toInt().coerceAtLeast(1)

                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    
                    pdfPage.close()
                    renderer.close()
                    parcelFD.close()
                    
                    withContext(Dispatchers.Main) {
                        pdfBitmap = bitmap
                        isLoading = false
                    }
                } else {
                    throw Exception("شکست در باز کردن توصیف‌گر فایل")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    loadError = "خطا در بارگذاری PDF: " + (e.localizedMessage ?: "فرمت نامعتبر")
                    isLoading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            if (!isFullscreen) {
                Column {
                    TopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth().padding(end = 12.dp)) {
                                Text(
                                    text = pdfName.ifEmpty { "نمایشگر PDF" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Right
                                )
                                Text(
                                    text = "صفحه $pageNum" + (if (pdfBitmap != null) " از $totalPages" else ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "بازگشت"
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { isFullscreen = true }) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "نمایش تمام‌صفحه",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = {
                                filePickerLauncher.launch(arrayOf("application/pdf"))
                            }) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = "انتخاب فایل PDF",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (!isFullscreen) {
                // Quick navigation and Action bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (pageNum > 1) pageNum-- },
                            enabled = pageNum > 1,
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "قبل")
                        }
                        Text(
                            text = "$pageNum",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        IconButton(
                            onClick = { pageNum++ },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "بعد")
                        }
                    }

                    // File Association Indicator
                    Text(
                        text = if (pdfUriString != null) "مستند بارگذاری شد" else "حالت شبیه‌ساز کتاب",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (pdfUriString != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(if (isFullscreen) 0.dp else 16.dp)
                    .clip(RoundedCornerShape(if (isFullscreen) 0.dp else 12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("در حال بارگذاری صفحه کتاب...", style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (pdfBitmap != null) {
                    // Display actual rendered PDF page bitmap supporting Zoom gestures
                    Image(
                        bitmap = pdfBitmap!!.asImageBitmap(),
                        contentDescription = "PDF Page image view",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                    )
                } else {
                    // Simulated Book Page rendering (Fallback when PDF is not custom loaded or invalid)
                    SimulatedPdfPage(
                        pdfName = pdfName,
                        pageNum = pageNum,
                        errorMsg = loadError,
                        onChooseFile = { filePickerLauncher.launch(arrayOf("application/pdf")) }
                    )
                }

                // Floating close button when in fullscreen
                if (isFullscreen) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .statusBarsPadding()
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                isFullscreen = false
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FullscreenExit,
                                contentDescription = "خروج از تمام‌صفحه",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Reset zoom badge
                if (scale > 1f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "تنظیم مجدد بزرگنمایی",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

// Simulated Academic content page matching material design guidelines
@Composable
fun SimulatedPdfPage(
    pdfName: String,
    pageNum: Int,
    errorMsg: String?,
    onChooseFile: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.End
    ) {
        if (errorMsg != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Right
                    )
                }
            }
        }

        // Simulating highly styled academic textbook container
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "کتاب مرجع",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = pdfName.ifEmpty { "سند نامشخص" },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "فصل مرجع یادداشت‌ها: بخش تحلیل محتوا",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Right,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Generating beautiful visual paragraphs with mock highlighters
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "یکی از روندهای اساسی در یادگیری توأم با استخراج خلاصه، بازخوانی هدفمند کتاب بر پایه شماره صفحات است. یادداشت شما در این نرم‌افزار به صورت کامل با صفحه جاری تراز شده است تا هنگام بازخوانی، منبع اصلی را گم نکنید.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Right,
                lineHeight = 24.sp
            )

            // Highlighter Simulation for page reference matches
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF176).copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .padding(6.dp)
            ) {
                Text(
                    text = "نکته کلیدی درس: متغیرهای مرجع همواره باید قبل از استفاده به دیتابیس محلی با ساختار رابطه ای متصل شوند. این امر سرعت خواندن را تا پنج برابر افزایش می‌دهد.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Right,
                    lineHeight = 24.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text(
                text = "برای دسترسی چندرسانه‌ای، این ابزار به شما این توانایی را می‌دهد که نسخه فیزیکی کتاب PDF خود را نیز در این صفحه بارگذاری و به یادداشت فید کنید تا صفحات کاملاً تطبیق داده و نمایش داده شوند.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Right,
                lineHeight = 24.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Action button to load real PDF
        FilledTonalButton(
            onClick = onChooseFile,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("بارگذاری PDF واقعی برای این کتاب", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "شماره صفحه مرجع: $pageNum",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}
