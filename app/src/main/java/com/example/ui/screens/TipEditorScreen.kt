package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.Note
import com.example.data.models.Tip

// Helper function to safely copy selected image or pdf to internal storage to avoid permission expiration
private fun copyUriToInternalStorage(context: android.content.Context, uri: Uri, folderName: String, prefix: String): Uri? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val extension = if (folderName == "pdfs") "pdf" else {
            context.contentResolver.getType(uri)?.substringAfterLast("/") ?: "jpg"
        }
        val folder = java.io.File(context.filesDir, folderName)
        if (!folder.exists()) folder.mkdirs()
        
        val uniqueName = "${prefix}_${System.currentTimeMillis()}.${extension}"
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
fun TipEditorScreen(
    categoryId: Int,
    tipToEdit: Tip?,
    categoryNotes: List<Note>,
    categoryTips: List<Tip> = emptyList(),
    initialNoteId: Int?,
    onBack: () -> Unit,
    onSave: (id: Int, noteId: Int, title: String, content: String, pdfName: String?, pdfUri: String?, pdfPage: Int?) -> Unit
) {
    val context = LocalContext.current
    val isEditMode = tipToEdit != null

    // Determine initial selected note id
    var selectedNoteId by remember(tipToEdit, initialNoteId) {
        mutableStateOf(tipToEdit?.noteId ?: initialNoteId ?: categoryNotes.firstOrNull()?.id ?: 0)
    }

    // Input States
    var title by remember(tipToEdit) { mutableStateOf(tipToEdit?.title ?: "") }
    var pdfName by remember(tipToEdit) { mutableStateOf(tipToEdit?.pdfName ?: "") }
    var pdfUriString by remember(tipToEdit) { mutableStateOf(tipToEdit?.pdfUri ?: "") }
    var pdfPageVal by remember(tipToEdit) { mutableStateOf(tipToEdit?.pdfPage?.toString() ?: "") }

    // Text field state with selection tracker
    var textFieldValue by remember(tipToEdit) {
        mutableStateOf(
            TextFieldValue(
                text = tipToEdit?.content ?: "",
                selection = TextRange(0, tipToEdit?.content?.length ?: 0)
            )
        )
    }

    // Dropdown / Note selector state
    var expandedNoteDropdown by remember { mutableStateOf(false) }

    // Modal / Dialog States
    var showWebLinkDialog by remember { mutableStateOf(false) }
    var webLinkLabel by remember { mutableStateOf("") }
    var webLinkUrl by remember { mutableStateOf("https://") }

    var showNoteLinkDialog by remember { mutableStateOf(false) }
    var showTipLinkDialog by remember { mutableStateOf(false) }

    // Helper functions to insert text at current cursor location
    fun insertTextAtCursor(before: String, after: String = "") {
        val originalText = textFieldValue.text
        val selection = textFieldValue.selection
        val start = selection.start.coerceIn(0, originalText.length)
        val end = selection.end.coerceIn(0, originalText.length)

        val selectedText = originalText.substring(start, end)
        val inserted = before + selectedText + after
        val newText = originalText.replaceRange(start, end, inserted)

        val newSelectionStart = start + before.length
        val newSelectionEnd = newSelectionStart + selectedText.length

        textFieldValue = TextFieldValue(
            text = newText,
            selection = TextRange(newSelectionStart, newSelectionEnd)
        )
    }

    // Local image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val localUri = copyUriToInternalStorage(context, uri, "images", "img")
            if (localUri != null) {
                insertTextAtCursor("\n![تصویر محلی]($localUri)\n")
            } else {
                insertTextAtCursor("\n![تصویر محلی]($uri)\n")
            }
        }
    }

    // PDF attachment picker
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val localUri = copyUriToInternalStorage(context, uri, "pdfs", "pdf")
            if (localUri != null) {
                pdfUriString = localUri.toString()
            } else {
                pdfUriString = uri.toString()
            }
            // Pull real PDF file name
            val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "کتاب مرجع"
            pdfName = fileName.replace(".pdf", "").replace("%20", " ")
        }
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = if (isEditMode) "ویرایش نکته علمی" else "نکته علمی جدید",
                            modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                            textAlign = TextAlign.Right,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (title.isBlank() || selectedNoteId == 0) return@IconButton
                                val pageInt = pdfPageVal.toIntOrNull()
                                onSave(
                                    tipToEdit?.id ?: 0,
                                    selectedNoteId,
                                    title.trim(),
                                    textFieldValue.text,
                                    pdfName.trim().ifEmpty { null },
                                    pdfUriString.trim().ifEmpty { null },
                                    pageInt
                                )
                            },
                            enabled = title.isNotBlank() && selectedNoteId != 0
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "ذخیره نکته",
                                tint = if (title.isNotBlank() && selectedNoteId != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
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
    ) { innerPadding ->
        if (categoryNotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "یادداشتی وجود ندارد!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "نکات فقط برای یادداشت‌ها و فصل‌های از قبل ایجاد شده قابل ثبت هستند. لطفاً ابتدا یک یادداشت درسی ایجاد کنید.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onBack) {
                        Text("بازگشت")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Scrollable fields
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    // Title Input
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("عنوان نکته علمى (الزامی)", textAlign = TextAlign.Right) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Right),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    // Note Association Dropdown (اتصال به یادداشت)
                    val activeNote = categoryNotes.find { it.id == selectedNoteId }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "انتخاب یادداشت مبدأ (و فصل مربوطه):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp),
                            textAlign = TextAlign.Right
                        )
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .clickable { expandedNoteDropdown = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (activeNote != null) "${activeNote.title} (${activeNote.chapter})" else "خطا در انتخاب یادداشت",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Right
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = expandedNoteDropdown,
                            onDismissRequest = { expandedNoteDropdown = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            categoryNotes.forEach { note ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${note.title} (فصل: ${note.chapter})",
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Right
                                        )
                                    },
                                    onClick = {
                                        selectedNoteId = note.id
                                        expandedNoteDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // PDF Settings Expansion Panel
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "تنظیمات پیوند با کتاب مرجع (PDF)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Right
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // PDF Page number
                                OutlinedTextField(
                                    value = pdfPageVal,
                                    onValueChange = { pdfPageVal = it },
                                    label = { Text("صفحه", textAlign = TextAlign.Right) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                    modifier = Modifier.weight(0.3f),
                                    textStyle = androidx.compose.ui.text.TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                                    singleLine = true
                                )

                                // PDF Book name
                                OutlinedTextField(
                                    value = pdfName,
                                    onValueChange = { pdfName = it },
                                    label = { Text("نام فایل یا کتاب PDF مرجع", textAlign = TextAlign.Right) },
                                    modifier = Modifier.weight(0.7f),
                                    textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Right),
                                    singleLine = true
                                )
                            }

                            // Attach file button
                            OutlinedButton(
                                onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (pdfUriString.isNotEmpty()) "تغییر فایل PDF مرتبط" else "انتخاب فایل PDF واقعی از حافظه",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            // Display file uri status
                            if (pdfUriString.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "فایل پیوند داده شد",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.CloudDone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Content editor
                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        label = { Text("متن نکته علمی (پشتیبانی از Markdown)", textAlign = TextAlign.Right) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 260.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            textAlign = TextAlign.Right,
                            fontSize = 16.sp,
                            lineHeight = 24.sp
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                // Markdown assistance toolbar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "افزودن سریع به متن نکته (مکان‌نما)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp, end = 6.dp),
                        textAlign = TextAlign.Right
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Insert Web Link
                        InputAssistButton(
                            onClick = { showWebLinkDialog = true },
                            icon = Icons.Default.Link,
                            label = "لینک وب"
                        )

                        // Insert Note Link (link to notes in current category)
                        InputAssistButton(
                            onClick = { showNoteLinkDialog = true },
                            icon = Icons.Default.Notes,
                            label = "لینک یادداشت"
                        )

                        // Insert Tip Link (link to tips in current category)
                        InputAssistButton(
                            onClick = { showTipLinkDialog = true },
                            icon = Icons.Default.Lightbulb,
                            label = "لینک نکته"
                        )

                        // Add Image
                        InputAssistButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            icon = Icons.Default.Image,
                            label = "عکس محلی"
                        )

                        // Bold
                        InputAssistButton(
                            onClick = { insertTextAtCursor("**", "**") },
                            icon = Icons.Default.FormatBold,
                            label = "برجسته"
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    if (showWebLinkDialog) {
        AlertDialog(
            onDismissRequest = { showWebLinkDialog = false },
            title = { Text("افزودن لینک وبسایت", textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    OutlinedTextField(
                        value = webLinkLabel,
                        onValueChange = { webLinkLabel = it },
                        label = { Text("متن نمایشی لینک (مثلاً: گوگل)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Right)
                    )

                    OutlinedTextField(
                        value = webLinkUrl,
                        onValueChange = { webLinkUrl = it },
                        label = { Text("آدرس URL وبسایت") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Left)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val label = webLinkLabel.trim().ifEmpty { "مشاهده لینک" }
                        val url = webLinkUrl.trim()
                        insertTextAtCursor("[$label]($url)")
                        showWebLinkDialog = false
                        webLinkLabel = ""
                        webLinkUrl = "https://"
                    }
                ) {
                    Text("درج در متن")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWebLinkDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    if (showNoteLinkDialog) {
        AlertDialog(
            onDismissRequest = { showNoteLinkDialog = false },
            title = { Text("اتصال به یادداشت دیگر", textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "یک یادداشت از این دسته انتخاب کنید تا لینک آن درج شود:",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (categoryNotes.isEmpty()) {
                        Text(
                            text = "یادداشتی وجود ندارد تا لینک شود.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        val listScroll = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(listScroll),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            categoryNotes.forEach { otherNote ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            insertTextAtCursor("[${otherNote.title}](note://${otherNote.title})")
                                            showNoteLinkDialog = false
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Text(
                                                text = otherNote.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Right
                                            )
                                            Text(
                                                text = otherNote.chapter,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                textAlign = TextAlign.Right
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Icon(
                                            imageVector = Icons.Default.Bookmark,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showNoteLinkDialog = false }) {
                    Text("بستن")
                }
            }
        )
    }

    // Cross-Tip linking dialog
    if (showTipLinkDialog) {
        AlertDialog(
            onDismissRequest = { showTipLinkDialog = false },
            title = { Text("اتصال به نکته علمی دیگر", textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "یک نکته علمی انتخاب کنید تا لینک آن درج شود:",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val otherTips = categoryTips.filter { it.id != tipToEdit?.id }

                    if (otherTips.isEmpty()) {
                        Text(
                            text = "نکته علمی دیگری در این دسته وجود ندارد تا لینک شود.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        val listScroll = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(listScroll),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            otherTips.forEach { tip ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            insertTextAtCursor("[${tip.title}](tip://${tip.title})")
                                            showTipLinkDialog = false
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Text(
                                                text = tip.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Right
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Icon(
                                            imageVector = Icons.Default.Lightbulb,
                                            contentDescription = null,
                                            tint = Color(0xFFF1C40F)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTipLinkDialog = false }) {
                    Text("بستن")
                }
            }
        )
    }
}
