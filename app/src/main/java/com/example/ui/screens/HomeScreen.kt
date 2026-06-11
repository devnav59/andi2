package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.Category
import com.example.data.models.Note
import com.example.ui.components.BackupRestoreDialog
import com.example.ui.components.NoteSearchDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    categories: List<Category>,
    notesList: List<Note>,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onCategoryClick: (Category, Boolean) -> Unit,
    onNoteClick: (Note) -> Unit,
    onAddCategory: (name: String, description: String, colorHex: String) -> Unit,
    onDeleteCategory: (Category) -> Unit,
    onFastBackup: (android.content.Context, (Boolean) -> Unit) -> Unit = { _, _ -> },
    onFastRestore: (android.content.Context, (Boolean) -> Unit) -> Unit = { _, _ -> },
    onGetBackupJson: suspend () -> String = { "" },
    onImportBackupJson: (String, (Boolean) -> Unit) -> Unit = { _, _ -> }
) {
    var showAddCategorySheet by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var selectedCategoryForMode by remember { mutableStateOf<Category?>(null) }

    // State properties for category creation sheet
    var newCategoryName by remember { mutableStateOf("") }
    var newCategoryDesc by remember { mutableStateOf("") }
    
    // Modern palettes to choose for category boxes
    val colorPalettes = listOf(
        "#FF1A73E8", // Blue
        "#FF34A853", // Green
        "#FFEA4335", // Red
        "#FFF9AB00", // Gold Yellow
        "#FF8E24AA", // Purple
        "#FF00ACC1", // Teal Cyan
        "#FFE040FB", // Magenta Pink
        "#FF546E7A"  // Slate Blue
    )
    var selectedColorHex by remember { mutableStateOf(colorPalettes.first()) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clickable { showSearchDialog = true },
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = "جستجوی یادداشت‌ها...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Right
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "جستجو",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showBackupDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Backup,
                                contentDescription = "پشتیبان‌گیری",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onToggleTheme) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "تغییر تم",
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
            ExtendedFloatingActionButton(
                onClick = { showAddCategorySheet = true },
                icon = { Icon(Icons.Default.Add, contentDescription = "افزودن دسته جدید") },
                text = { Text("افزودن موضوع", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (categories.isEmpty()) {
                // Empty state layout with visual call to action
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Empty folder",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        modifier = Modifier.size(96.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "هنوز هیچ موضوع درسی اضافه نکرده‌اید!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "برای شروع، یک موضوع درسی بسازید تا بتوانید یادداشت‌های درسی خود را با شماره صفحه فایل‌های PDF سازماندهی کنید.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { showAddCategorySheet = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ساخت اولین موضوع درسی")
                    }
                }
            } else {
                // Asymmetric grid of books/cards
                LazyVerticalGrid(
                    columns = GridCells.Fixed(1), // Beautiful thick lists or cards
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(categories, key = { it.id }) { category ->
                        val noteCount = remember(notesList, category.id) {
                            notesList.count { it.categoryId == category.id }
                        }
                        
                        CategoryBoxCard(
                            category = category,
                            noteCount = noteCount,
                            onClick = { selectedCategoryForMode = category },
                            onDelete = { onDeleteCategory(category) }
                        )
                    }
                }
            }
        }
    }

    // Modal sheet to construct categories
    if (showAddCategorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddCategorySheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "موضوع درسی جدید",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Right
                )

                Text(
                    text = "مثلاً: معادلات دیفرانسیل، برنامه‌نویسی موبایل، متون انگلیسی",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )

                // Name Input
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("عنوان موضوع (الزامی)", textAlign = TextAlign.Right) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Right),
                    singleLine = true
                )

                // Description Input
                OutlinedTextField(
                    value = newCategoryDesc,
                    onValueChange = { newCategoryDesc = it },
                    label = { Text("توضیحات کوتاه", textAlign = TextAlign.Right) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Right),
                    maxLines = 2
                )

                // Pick accented color for card representation
                Text(
                    text = "انتخاب رنگ تم موضوع:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    colorPalettes.forEach { hex ->
                        val parsedColor = Color(android.graphics.Color.parseColor(hex))
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(50))
                                .background(parsedColor)
                                .clickable { selectedColorHex = hex }
                                .padding(2.dp)
                        ) {
                            if (selectedColorHex == hex) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.White.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "انتخاب شده",
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Submit button
                Button(
                    onClick = {
                        if (newCategoryName.isNotBlank()) {
                            onAddCategory(newCategoryName.trim(), newCategoryDesc.trim(), selectedColorHex)
                            newCategoryName = ""
                            newCategoryDesc = ""
                            showAddCategorySheet = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = newCategoryName.isNotBlank()
                ) {
                    Text("درج موضوع جدید", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    if (showSearchDialog) {
        NoteSearchDialog(
            allNotes = notesList,
            onNoteClick = onNoteClick,
            onDismiss = { showSearchDialog = false }
        )
    }

    if (showBackupDialog) {
        BackupRestoreDialog(
            onDismiss = { showBackupDialog = false },
            onFastBackup = onFastBackup,
            onFastRestore = onFastRestore,
            onGetBackupJson = onGetBackupJson,
            onImportBackupJson = onImportBackupJson
        )
    }

    if (selectedCategoryForMode != null) {
        AlertDialog(
            onDismissRequest = { selectedCategoryForMode = null },
            title = {
                Text(
                    text = "انتخاب شیوه مرور مبحث",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "لطفاً شیوه مرور مبحث «${selectedCategoryForMode!!.name}» را مشخص کنید:",
                        textAlign = TextAlign.Right,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    // Option 1: Study Notes
                    Card(
                        onClick = {
                            val cat = selectedCategoryForMode!!
                            selectedCategoryForMode = null
                            onCategoryClick(cat, false) // false means Notes mode
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "مرور یادداشت‌های درسی",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "مشاهده مباحث طبقه‌بندی شده و فصول کتاب درسی",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    textAlign = TextAlign.Right
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    // Option 2: Scientific Tips
                    Card(
                        onClick = {
                            val cat = selectedCategoryForMode!!
                            selectedCategoryForMode = null
                            onCategoryClick(cat, true) // true means Tips mode
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "مرور نکات علمی و خلاصه",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color(0xFFD4AC0D)
                                )
                                Text(
                                    text = "مشاهده نکات کلیدی، فرمول‌ها و گزیده‌های مهم درسی",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    textAlign = TextAlign.Right
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFFEF9E7)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = Color(0xFFD4AC0D)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedCategoryForMode = null }) {
                    Text("انصراف")
                }
            }
        )
    }
}

// Gorgeous colored custom card simulating premium study container
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryBoxCard(
    category: Category,
    noteCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val themeColor = remember(category.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(category.colorHex))
        } catch (e: Exception) {
            Color(0xFF1A73E8)
        }
    }

    var showDeleteAlert by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = themeColor.copy(alpha = 0.18f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showDeleteAlert = true }
                )
                .background(themeColor.copy(alpha = 0.04f)) // extremely light theme hue background inside
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            // Options Action
            IconButton(onClick = { showDeleteAlert = true }) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "حذف موضوع",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Text Metadata block (RTL alignment)
            Column(
                modifier = Modifier,
                horizontalAlignment = Alignment.End
            ) {
                // Color Label Pill representing category ID index
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(themeColor)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$noteCount یادداشت ثبت شده",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = themeColor,
                    textAlign = TextAlign.Right
                )

                if (category.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = category.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Right,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Book accent visual element (Lollipop style block)
            Box(
                modifier = Modifier
                    .size(width = 12.dp, height = 64.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(themeColor, themeColor.copy(alpha = 0.5f))
                        )
                    )
            )
        }
    }

    if (showDeleteAlert) {
        AlertDialog(
            onDismissRequest = { showDeleteAlert = false },
            title = { Text("حذف موضوع علمی؟", textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
            text = {
                Text(
                    text = "آیا مطمئن هستید که می‌خواهید موضوع «${category.name}» را به همراه تمام یادداشت‌های درسی آن حذف کنید؟ این عمل غیرقابل بازگشت است.",
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteAlert = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف کامل")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAlert = false }) {
                    Text("انصراف")
                }
            }
        )
    }
}
