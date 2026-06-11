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
fun NoteEditorScreen(
    categoryId: Int,
    noteToEdit: Note?,
    categoryNotes: List<Note>,
    categoryTips: List<Tip> = emptyList(),
    onBack: () -> Unit,
    onSave: (id: Int, title: String, chapter: String, content: String, pdfName: String?, pdfUri: String?, pdfPage: Int?) -> Unit
) {
    val context = LocalContext.current
    val isEditMode = noteToEdit != null

    // Existing chapters of the category for suggestions
    val existingChapters = remember(categoryNotes) {
        categoryNotes.map { it.chapter }.distinct().filter { it.isNotBlank() }
    }

    // Input States - updated with `remember(noteToEdit)` keys for dynamic loading/editing
    var title by remember(noteToEdit) { mutableStateOf(noteToEdit?.title ?: "") }
    var chapter by remember(noteToEdit) { mutableStateOf(noteToEdit?.chapter ?: "") }
    var pdfName by remember(noteToEdit) { mutableStateOf(noteToEdit?.pdfName ?: "") }
    var pdfUriString by remember(noteToEdit) { mutableStateOf(noteToEdit?.pdfUri ?: "") }
    var pdfPageVal by remember(noteToEdit) { mutableStateOf(noteToEdit?.pdfPage?.toString() ?: "") }

    // Text field state with selection tracker
    var textFieldValue by remember(noteToEdit) {
        mutableStateOf(
            TextFieldValue(
                text = noteToEdit?.content ?: "",
                selection = TextRange(0, noteToEdit?.content?.length ?: 0)
            )
        )
    }

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
                            text = if (isEditMode) "ویرایش یادداشت" else "یادداشت جدید",
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
                                if (title.isBlank()) return@IconButton
                                val pageInt = pdfPageVal.toIntOrNull()
                                onSave(
                                    noteToEdit?.id ?: 0,
                                    title.trim(),
                                    chapter.trim().ifEmpty { "عمومی" },
                                    textFieldValue.text,
                                    pdfName.trim().ifEmpty { null },
                                    pdfUriString.trim().ifEmpty { null },
                                    pageInt
                                )
                            },
                            enabled = title.isNotBlank()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "ذخیره یادداشت",
                                tint = if (title.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Scrollable Fields Input Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Title Input (عنوان یادداشت)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان یادداشت (الزامی)", textAlign = TextAlign.Right) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Right),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                // Chapter Input (فصل یادداشت)
                OutlinedTextField(
                    value = chapter,
                    onValueChange = { chapter = it },
                    label = { Text("فصل / بخش (مثلاً: فصل اول)", textAlign = TextAlign.Right) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Right),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )

                if (existingChapters.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "انتخاب از فصل‌های سابق همین مبحث:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(bottom = 4.dp),
                            textAlign = TextAlign.Right
                        )
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            reverseLayout = true, // RTL flow
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(existingChapters.size) { index ->
                                val ch = existingChapters[index]
                                AssistChip(
                                    onClick = { chapter = ch },
                                    label = { Text(ch, style = MaterialTheme.typography.labelMedium) }
                                )
                            }
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
                                textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center),
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

                // Note Body content / Markdown
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    label = { Text("متن یادداشت (پشتیبانی از Markdown)", textAlign = TextAlign.Right) },
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

            // Interactive Editor Toolbar for insertion inside text (Markdown assistance)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = "افزودن سریع به متن یادداشت (مکان‌نما)",
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
                    // 1. Insert Web Link
                    InputAssistButton(
                        onClick = { showWebLinkDialog = true },
                        icon = Icons.Default.Link,
                        label = "لینک وب"
                    )

                    // 2. Insert Link to other Notes inside category
                    InputAssistButton(
                        onClick = { showNoteLinkDialog = true },
                        icon = Icons.Default.Notes,
                        label = "لینک یادداشت"
                    )

                    // 2.2 Insert Link to category Tips
                    InputAssistButton(
                        onClick = { showTipLinkDialog = true },
                        icon = Icons.Default.Lightbulb,
                        label = "لینک نکته"
                    )

                    // 3. Add Local Image from Gallery
                    InputAssistButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        icon = Icons.Default.Image,
                        label = "عکس محلی"
                    )

                    // 4. Bold Markdown helper
                    InputAssistButton(
                        onClick = { insertTextAtCursor("**", "**") },
                        icon = Icons.Default.FormatBold,
                        label = "برجسته"
                    )
                }
            }
        }
    }

    // DIALOGS & MODALS

    // Web link builder dialog
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

    // Cross-Note linking dialog (Lists existing notes inside this category)
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

                    if (categoryNotes.isEmpty() || (categoryNotes.size == 1 && categoryNotes[0].id == noteToEdit?.id)) {
                        Text(
                            text = "یادداشت دیگری در این دسته وجود ندارد تا لینک شود.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        // Scrollable Notes choice list
                        val listScroll = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(listScroll),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            categoryNotes.filter { it.id != noteToEdit?.id }.forEach { otherNote ->
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
            title = { Text("اتصال به نکته علمی", textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "یک نکته علمی از این دسته انتخاب کنید تا لینک آن درج شود:",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (categoryTips.isEmpty()) {
                        Text(
                            text = "نکته علمی در این مبحث ثبت نشده است تا لینک شود.",
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
                            categoryTips.forEach { tip ->
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

// Custom Icon + Label Action Assist buttons for Composer Toolbar
@Composable
fun InputAssistButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Card(
        onClick = onClick,
        modifier = Modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontSize = 10.sp
            )
        }
    }
}
