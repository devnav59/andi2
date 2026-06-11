package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.Category
import com.example.data.models.Note
import com.example.data.models.Tip
import com.example.ui.components.MarkdownRenderer
import com.example.ui.components.NoteSearchDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesDisplayScreen(
    category: Category,
    allNotes: List<Note>,
    allTips: List<Tip>,
    globalNotes: List<Note> = emptyList(),
    currentNote: Note?,
    isLoaded: Boolean,
    onBack: () -> Unit,
    onNoteSelected: (Note) -> Unit,
    onAddNoteClick: () -> Unit,
    onEditNoteClick: (Note) -> Unit,
    onDeleteNoteClick: (Note) -> Unit,
    onAddTipClick: (noteId: Int?) -> Unit,
    onEditTipClick: (Tip) -> Unit,
    onDeleteTipClick: (Tip) -> Unit,
    onOpenPdfViewer: (pdfName: String, pdfUri: String?, page: Int) -> Unit,
    initialTipsMode: Boolean = false
) {
    val context = LocalContext.current
    var showIndexSheet by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var activeLinkedNote by remember { mutableStateOf<Note?>(null) }

    // Deletion confirmation local states
    var noteToDelete by remember { mutableStateOf<Note?>(null) }
    var tipToDelete by remember { mutableStateOf<Tip?>(null) }

    // Tip management local states
    var isViewingTip by remember(initialTipsMode) { mutableStateOf(initialTipsMode) }
    var selectedTip by remember { mutableStateOf<Tip?>(if (initialTipsMode) allTips.firstOrNull() else null) }
    var showTipsSheet by remember { mutableStateOf(false) }
    val expandedChapters = remember { mutableStateMapOf<String, Boolean>() }
    val expandedNotes = remember { mutableStateMapOf<Int, Boolean>() }

    // Synchronize selectedTip existence
    LaunchedEffect(allTips) {
        if (selectedTip != null && allTips.none { it.id == selectedTip!!.id }) {
            selectedTip = null
            isViewingTip = false
        }
    }

    LaunchedEffect(initialTipsMode, allTips) {
        if (initialTipsMode && selectedTip == null && allTips.isNotEmpty()) {
            selectedTip = allTips.first()
        }
    }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Single-Screen local history stack for note link jumps
    var noteHistory by remember { mutableStateOf(emptyList<Note>()) }

    val handleBackAction = {
        if (isViewingTip) {
            isViewingTip = false
        } else if (noteHistory.isNotEmpty()) {
            val previous = noteHistory.last()
            noteHistory = noteHistory.dropLast(1)
            onNoteSelected(previous)
        } else {
            onBack()
        }
    }

    // Receptacle back-handler for hardware back key press
    BackHandler(enabled = noteHistory.isNotEmpty() || isViewingTip) {
        handleBackAction()
    }

    val currentIndex = remember(allNotes, currentNote) {
        if (currentNote != null) allNotes.indexOfFirst { it.id == currentNote.id } else -1
    }
    val prevNote = remember(allNotes, currentIndex) {
        if (currentIndex > 0) allNotes[currentIndex - 1] else null
    }
    val nextNote = remember(allNotes, currentIndex) {
        if (currentIndex >= 0 && currentIndex < allNotes.size - 1) allNotes[currentIndex + 1] else null
    }

    // Group notes by Chapter for Table of Contents index
    val notesByChapter = remember(allNotes) {
        allNotes.groupBy { it.chapter }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = handleBackAction) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                        }
                    },
                    actions = {
                        // 1. Edit Button (Context aware: Note or Tip)
                        if (isViewingTip && selectedTip != null) {
                            IconButton(onClick = { onEditTipClick(selectedTip!!) }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "ویرایش نکته",
                                    tint = Color(0xFFF1C40F)
                                )
                            }
                        } else if (!isViewingTip && currentNote != null) {
                            IconButton(onClick = { onEditNoteClick(currentNote) }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "ویرایش یادداشت",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // 2. Add/Create Note Button
                        IconButton(onClick = onAddNoteClick) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "یادداشت جدید",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // 3. Create Tip Button (Only enabled if notes exist)
                        IconButton(
                            onClick = { onAddTipClick(currentNote?.id) },
                            enabled = allNotes.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Default.NoteAdd,
                                contentDescription = "ایجاد نکته جدید",
                                tint = if (allNotes.isNotEmpty()) Color(0xFFF1C40F) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        }

                        // 4. Tips List Collapsible index sheet button
                        val hasTips = allTips.isNotEmpty()
                        IconButton(onClick = { showTipsSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.FormatListBulleted,
                                contentDescription = "لیست نکات",
                                tint = if (hasTips) Color(0xFFF1C40F) else MaterialTheme.colorScheme.primary
                            )
                        }

                        // 6. Advanced Search Button
                        IconButton(onClick = { showSearchDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "جستجو",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // 7. Notes TOC list index button
                        IconButton(onClick = { showIndexSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = "پوشه فصل‌ها",
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
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = currentNote != null && scrollState.value > 200,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            scrollState.animateScrollTo(0)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(bottom = 16.dp, end = 16.dp)
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "برگشت به بالا")
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
            if (!isLoaded) {
                // Initial load pending from database
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (allNotes.isEmpty()) {
                // Empty state for notes
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Book,
                        contentDescription = "No Notes",
                        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "یادداشتی درج نشده است",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "در این دسته‌بندی هنوز یادداشتی ثبت نشده است. همین حالا دکمه افزودن یادداشت را بزنید تا اولین جزوه درسی مجهز به Markdown خود را بسازید.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onAddNoteClick) {
                        Icon(Icons.Default.AddCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("درج اولین یادداشت علمی")
                    }
                }
            } else if (currentNote == null) {
                // Notes exist, but none is selected yet loader
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val activeNote = currentNote
                val activeTip = selectedTip
                
                Box(modifier = Modifier.fillMaxSize()) {
                    if (isViewingTip && activeTip == null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = "No Tips",
                                tint = Color(0xFFD4AC0D).copy(alpha = 0.4f),
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "نکته علمی ثبت نشده است",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "هنوز هیچ نکته علمی یا خلاصه کلیدی برای این مبحث ثبت نکرده‌اید. همین حالا دکمه افزودن نکته را بزنید تا اولین نکته مجهز به فرمول یا مرجع خود را ثبت کنید.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { onAddTipClick(currentNote?.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AC0D))
                            ) {
                                Icon(Icons.Default.AddCircle, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("درج اولین نکته علمی", color = Color.White)
                            }
                        }
                    } else if (isViewingTip && activeTip != null) {
                        // Active single Tip layout (exactly like note layout!)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        // Metadata Header (Tip Title and Connected Note/Chapter path)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            // Action tools row
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(onClick = { tipToDelete = activeTip }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteForever,
                                        contentDescription = "حذف نکته",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            val parentNote = allNotes.find { it.id == activeTip.noteId }
                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "فصل: ${parentNote?.chapter ?: "نامشخص"} / یادداشت: ${parentNote?.title ?: "نامشخص"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Right
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        text = activeTip.title,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Right
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = Color(0xFFF1C40F),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        // Connected PDF Attachment reference Badge for Tip
                        if (activeTip.pdfName != null) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp)
                                    .clickable {
                                        onOpenPdfViewer(
                                            activeTip.pdfName,
                                            activeTip.pdfUri,
                                            activeTip.pdfPage ?: 1
                                        )
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = activeTip.pdfName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Right
                                        )
                                        Text(
                                            text = if (activeTip.pdfPage != null) "مطابق با صفحه ${activeTip.pdfPage} کتاب مرجع" else "اتصال مستقیم به جزوه فیزیکی",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            textAlign = TextAlign.Right
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.width(16.dp))
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PictureAsPdf,
                                            contentDescription = "PDF Book symbol",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        VerticalDivider(
                            modifier = Modifier.height(1.dp).fillMaxWidth().padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // Markdown content for active Tip
                        MarkdownRenderer(
                            text = activeTip.content,
                            onNoteLinkClick = { reference ->
                                val targetNote = globalNotes.find { 
                                    it.title.trim().lowercase() == reference.trim().lowercase() || 
                                    it.id.toString() == reference.trim()
                                } ?: globalNotes.find { it.title.contains(reference, ignoreCase = true) }
                                
                                if (targetNote != null) {
                                    activeLinkedNote = targetNote
                                }
                            },
                            onWebLinkClick = { url ->
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            onTipLinkClick = { reference ->
                                val targetTip = allTips.find {
                                    it.title.trim().lowercase() == reference.trim().lowercase()
                                } ?: allTips.find { it.title.contains(reference, ignoreCase = true) }
                                
                                if (targetTip != null) {
                                    selectedTip = targetTip
                                    isViewingTip = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(100.dp))
                    }
                } else if (activeNote != null) {
                    // Active single note layout
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        // Note Metadata Header Box (Title and Chapter)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            // Action tools row
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(onClick = { noteToDelete = activeNote }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteForever,
                                        contentDescription = "حذف جزوه",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = activeNote.chapter,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Right
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = activeNote.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Right
                                )
                            }
                        }

                        // Books / PDF Attachment Reference Badge (Top of Note)
                        if (activeNote.pdfName != null) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp)
                                    .clickable {
                                        onOpenPdfViewer(
                                            activeNote.pdfName,
                                            activeNote.pdfUri,
                                            activeNote.pdfPage ?: 1
                                        )
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = activeNote.pdfName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Right
                                        )
                                        Text(
                                            text = if (activeNote.pdfPage != null) "مطابق با صفحه ${activeNote.pdfPage} کتاب مرجع" else "اتصال مستقیم به جزوه فیزیکی",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            textAlign = TextAlign.Right
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.width(16.dp))
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PictureAsPdf,
                                            contentDescription = "PDF Book symbol",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        VerticalDivider(
                            modifier = Modifier.height(1.dp).fillMaxWidth().padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // Markdown Renderer container (scrolls smoothly inside parent content scroll)
                        MarkdownRenderer(
                            text = activeNote.content,
                            onNoteLinkClick = { reference ->
                                // Look up another note by its specified title or primary key ID across all categories
                                val targetNote = globalNotes.find { 
                                    it.title.trim().lowercase() == reference.trim().lowercase() || 
                                    it.id.toString() == reference.trim()
                                } ?: globalNotes.find { it.title.contains(reference, ignoreCase = true) }
                                
                                if (targetNote != null) {
                                    activeLinkedNote = targetNote
                                }
                            },
                            onWebLinkClick = { url ->
                                // Open external browser smoothly via Intent SAF flow
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            onTipLinkClick = { reference ->
                                val targetTip = allTips.find {
                                    it.title.trim().lowercase() == reference.trim().lowercase()
                                } ?: allTips.find { it.title.contains(reference, ignoreCase = true) }
                                
                                if (targetTip != null) {
                                    selectedTip = targetTip
                                    isViewingTip = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(100.dp)) // Floating button safe margin
                    }
                }
                }
            }
        }
    }

    if (!isViewingTip && currentNote != null && allNotes.size > 1) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 16.dp, start = 16.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Next Note Button (یادداشت بعدی) - Goes forward in index, points left in RTL layout
            IconButton(
                onClick = {
                    noteHistory = emptyList() // Clear history so normal flipping does not clutter back stack
                    nextNote?.let { onNoteSelected(it) }
                },
                enabled = nextNote != null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (nextNote != null) MaterialTheme.colorScheme.secondaryContainer 
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack, // Point left ⬅️ for next note
                    contentDescription = "یادداشت بعدی",
                    tint = if (nextNote != null) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Note Index Counter (e.g. 1/3)
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${currentIndex + 1}/${allNotes.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            // Previous Note Button (یادداشت قبلی) - Goes backward in index, points right in RTL layout
            IconButton(
                onClick = {
                    noteHistory = emptyList() // Clear history so normal flipping does not clutter back stack
                    prevNote?.let { onNoteSelected(it) }
                },
                enabled = prevNote != null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (prevNote != null) MaterialTheme.colorScheme.secondaryContainer 
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward, // Point right ➡️ for previous note
                    contentDescription = "یادداشت قبلی",
                    tint = if (prevNote != null) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    val currentTipIndex = remember(allTips, selectedTip) {
        if (selectedTip != null) allTips.indexOfFirst { it.id == selectedTip!!.id } else -1
    }
    val prevTip = remember(allTips, currentTipIndex) {
        if (currentTipIndex > 0) allTips[currentTipIndex - 1] else null
    }
    val nextTip = remember(allTips, currentTipIndex) {
        if (currentTipIndex >= 0 && currentTipIndex < allTips.size - 1) allTips[currentTipIndex + 1] else null
    }

    if (isViewingTip && selectedTip != null && allTips.size > 1) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 16.dp, start = 16.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Next Tip Button (نکته بعدی)
            IconButton(
                onClick = {
                    nextTip?.let { selectedTip = it }
                },
                enabled = nextTip != null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (nextTip != null) Color(0xFFFEF9E7)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack, // Point left ⬅️ for next tip
                    contentDescription = "نکته بعدی",
                    tint = if (nextTip != null) Color(0xFFD4AC0D) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Tip Index Counter
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .background(
                        color = Color(0xFFFEF9E7).copy(alpha = 0.8f),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${currentTipIndex + 1}/${allTips.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD4AC0D)
                )
            }

            // Previous Tip Button (نکته قبلی)
            IconButton(
                onClick = {
                    prevTip?.let { selectedTip = it }
                },
                enabled = prevTip != null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (prevTip != null) Color(0xFFFEF9E7)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward, // Point right ➡️ for previous tip
                    contentDescription = "نکته قبلی",
                    tint = if (prevTip != null) Color(0xFFD4AC0D) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

    // Modern index selection Table Of Contents (TOC) Bottom sheet
    if (showIndexSheet) {
        ModalBottomSheet(
            onDismissRequest = { showIndexSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 11.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "فهرست یادداشت‌های درسی",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (notesByChapter.isEmpty()) {
                    Text(
                        text = "هیچ جزوه‌ای در این مبحث ثبت نشده است",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                } else {
                    // Scrollable TOC chapters list
                    val indexScroll = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(indexScroll),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        notesByChapter.forEach { (chapterName, notes) ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.End
                            ) {
                                // Chapter Header Banner style
                                Text(
                                    text = chapterName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                // List of entries underneath
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    notes.forEach { note ->
                                        val isCurrent = currentNote?.id == note.id
                                        
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) 
                                                    else Color.Transparent
                                                )
                                                .clickable {
                                                    noteHistory = emptyList() // Reset link-jump history since we deliberately selected a new note from the index list
                                                    isViewingTip = false
                                                    onNoteSelected(note)
                                                    showIndexSheet = false
                                                }
                                                .padding(horizontal = 16.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                if (note.pdfName != null) {
                                                    Icon(
                                                        imageVector = Icons.Default.PictureAsPdf,
                                                        contentDescription = null,
                                                        tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                                Text(
                                                    text = note.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.Right
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Icon(
                                                imageVector = if (isCurrent) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                                contentDescription = null,
                                                tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showSearchDialog) {
        NoteSearchDialog(
            allNotes = globalNotes,
            onNoteClick = { note ->
                isViewingTip = false
                onNoteSelected(note)
                showSearchDialog = false
            },
            onDismiss = { showSearchDialog = false }
        )
    }

    if (activeLinkedNote != null) {
        ModalBottomSheet(
            onDismissRequest = { activeLinkedNote = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .padding(16.dp)
            ) {
                // Header of Modal
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { activeLinkedNote = null }) {
                        Icon(Icons.Default.Close, contentDescription = "بستن")
                    }
                    Text(
                        text = activeLinkedNote!!.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Right
                    )
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))
                
                // Content of Linked Note
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    MarkdownRenderer(
                        text = activeLinkedNote!!.content,
                        onNoteLinkClick = { ref ->
                            // Support deep nested link or update active session
                            val nextTarget = globalNotes.find { 
                                it.title.trim().lowercase() == ref.trim().lowercase() || 
                                it.id.toString() == ref.trim()
                            } ?: globalNotes.find { it.title.contains(ref, ignoreCase = true) }
                            if (nextTarget != null) {
                                activeLinkedNote = nextTarget
                            }
                        },
                        onWebLinkClick = { url ->
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // ignore
                            }
                        },
                        onTipLinkClick = { reference ->
                            val targetTip = allTips.find {
                                it.title.trim().lowercase() == reference.trim().lowercase()
                            } ?: allTips.find { it.title.contains(reference, ignoreCase = true) }
                            
                            if (targetTip != null) {
                                selectedTip = targetTip
                                isViewingTip = true
                                activeLinkedNote = null
                            }
                        }
                    )
                }
            }
        }
    }

    if (showTipsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTipsSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { 
                            showTipsSheet = false
                            onAddTipClick(currentNote?.id)
                        },
                        enabled = allNotes.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = "ثبت نکته جدید",
                            tint = if (allNotes.isNotEmpty()) Color(0xFFF1C40F) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    }
                    Text(
                        text = "فهرست نکات علمی مبحث",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFF1C40F)
                    )
                }

                if (allNotes.isEmpty()) {
                    Text(
                        text = "ابتدا باید یک یادداشت درسی ثبت کنید تا بتوانید برای آن نکات بنویسید.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        textAlign = TextAlign.Center
                    )
                } else {
                    // Scrollable Tips hierarchy chapters -> notes -> tips
                    val tipsScroll = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(tipsScroll),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        notesByChapter.forEach { (chapterName, notes) ->
                            val isChapterExpanded = expandedChapters[chapterName] ?: true
                            
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.End
                            ) {
                                // Chapter Header Banner (Collapsible!)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .clickable {
                                            expandedChapters[chapterName] = !isChapterExpanded
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isChapterExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = chapterName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Right
                                    )
                                }

                                if (isChapterExpanded) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(end = 12.dp), // Indent under chapter
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        notes.forEach { note ->
                                            val isNoteExpanded = expandedNotes[note.id] ?: false
                                            val tipsInNote = allTips.filter { it.noteId == note.id }
                                            
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                // Note title (Collapsible!)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                                                        .clickable {
                                                            expandedNotes[note.id] = !isNoteExpanded
                                                        }
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "(${tipsInNote.size} نکته)",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.secondary,
                                                            modifier = Modifier.padding(end = 4.dp)
                                                        )
                                                        Icon(
                                                            imageVector = if (isNoteExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = note.title,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            textAlign = TextAlign.Right
                                                        )
                                                        Icon(
                                                            imageVector = Icons.Default.Assignment,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }

                                                if (isNoteExpanded) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(end = 16.dp), // Indent tips under note
                                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        if (tipsInNote.isEmpty()) {
                                                            Text(
                                                                text = "نکته‌ای برای این یادداشت ثبت نشده است.",
                                                                style = MaterialTheme.typography.labelMedium,
                                                                color = MaterialTheme.colorScheme.outline,
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(vertical = 6.dp),
                                                                textAlign = TextAlign.Right
                                                            )
                                                        } else {
                                                            tipsInNote.forEach { tip ->
                                                                val isCurrentTip = selectedTip?.id == tip.id && isViewingTip
                                                                
                                                                Row(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .clip(RoundedCornerShape(6.dp))
                                                                        .background(
                                                                            if (isCurrentTip) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                                            else Color.Transparent
                                                                        )
                                                                        .clickable {
                                                                            selectedTip = tip
                                                                            isViewingTip = true
                                                                            showTipsSheet = false
                                                                        }
                                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                                    horizontalArrangement = Arrangement.End,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text(
                                                                        text = tip.title,
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        fontWeight = if (isCurrentTip) FontWeight.Bold else FontWeight.Normal,
                                                                        color = if (isCurrentTip) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                                        textAlign = TextAlign.Right,
                                                                        modifier = Modifier.weight(1f)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(8.dp))
                                                                    Icon(
                                                                        imageVector = Icons.Default.Lightbulb,
                                                                        contentDescription = null,
                                                                        tint = if (isCurrentTip) Color(0xFFF1C40F) else MaterialTheme.colorScheme.outline,
                                                                        modifier = Modifier.size(14.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Delete Note Confirmation AlertDialog
    if (noteToDelete != null) {
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = {
                Text(
                    text = "تایید حذف یادداشت درسی",
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = "آیا مطمئن هستید که می‌خواهید یادداشت درسی «${noteToDelete!!.title}» را حذف کنید؟ این عمل غیرقابل بازگشت است و تمام نکات علمی متصل به آن نیز حذف خواهند شد.",
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val note = noteToDelete!!
                        noteToDelete = null
                        onDeleteNoteClick(note)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف شود", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) {
                    Text("انصراف")
                }
            }
        )
    }

    // Delete Tip Confirmation AlertDialog
    if (tipToDelete != null) {
        AlertDialog(
            onDismissRequest = { tipToDelete = null },
            title = {
                Text(
                    text = "تایید حذف نکته علمی",
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = "آیا مطمئن هستید که می‌خواهید نکته علمی «${tipToDelete!!.title}» را حذف کنید؟ این عمل قابل بازیابی نیست.",
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val tip = tipToDelete!!
                        tipToDelete = null
                        onDeleteTipClick(tip)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف شود", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { tipToDelete = null }) {
                    Text("انصراف")
                }
            }
        )
    }
}
