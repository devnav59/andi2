package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.models.Note

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteSearchDialog(
    allNotes: List<Note>,
    onNoteClick: (Note) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    
    // Dynamically filter results in memory
    val titleResults = remember(query, allNotes) {
        if (query.trim().isEmpty()) emptyList()
        else allNotes.filter { it.title.contains(query, ignoreCase = true) }
    }
    
    val contentResults = remember(query, allNotes) {
        if (query.trim().isEmpty()) emptyList()
        else allNotes.filter { 
            it.content.contains(query, ignoreCase = true) && 
            !it.title.contains(query, ignoreCase = true) // Avoid repeating titles
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top Search Bar Panel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close, 
                            contentDescription = "بستن جستجو",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    
                    Text(
                        text = "جستجوی پیشرفته یادداشت‌ها",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Right
                    )
                }

                // Search INPUT Textfield
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { 
                        Text(
                            text = "عنوان یا متن یادداشت را جستجو کنید...", 
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
                            style = MaterialTheme.typography.bodyMedium
                        ) 
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search, 
                            contentDescription = "آیکون جستجو",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close, 
                                    contentDescription = "پاک کردن متن"
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        textAlign = TextAlign.Right
                    )
                )

                if (query.trim().isEmpty()) {
                    // Search instructions block
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "کلمه کلیدی مورد نظر خود را بنویسید",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else if (titleResults.isEmpty() && contentResults.isEmpty()) {
                    // No match found state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "هیچ نتیجه‌ای متناسب با عبارت جستجو شده یافت نشد.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Results displaying lists
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Region 1: Match in note titles
                        if (titleResults.isNotEmpty()) {
                            item {
                                Text(
                                    text = "کادر عنوان‌ها (${titleResults.size} مورد)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp),
                                    textAlign = TextAlign.Right
                                )
                            }
                            
                            items(titleResults) { note ->
                                SearchResultCard(
                                    note = note,
                                    subtitle = "فصل ${note.chapter}",
                                    highlightQuery = query,
                                    onClick = {
                                        onNoteClick(note)
                                        onDismiss()
                                    }
                                )
                            }
                        }

                        // Region 2: Match inside Note Text Content
                        if (contentResults.isNotEmpty()) {
                            item {
                                Text(
                                    text = "کادر نتایج جست‌وجو از متن (${contentResults.size} مورد)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp, bottom = 6.dp),
                                    textAlign = TextAlign.Right
                                )
                            }
                            
                            items(contentResults) { note ->
                                val excerpt = remember(note.content, query) {
                                    getExcerpt(note.content, query)
                                }
                                SearchResultCard(
                                    note = note,
                                    subtitle = excerpt,
                                    highlightQuery = query,
                                    onClick = {
                                        onNoteClick(note)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultCard(
    note: Note,
    subtitle: String,
    highlightQuery: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Right,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun getExcerpt(content: String, query: String): String {
    val index = content.indexOf(query, ignoreCase = true)
    if (index == -1) return if (content.length > 60) content.take(60) + "..." else content
    val start = maxOf(0, index - 30)
    val end = minOf(content.length, index + query.length + 35)
    var excerpt = content.substring(start, end)
    if (start > 0) excerpt = "..." + excerpt
    if (end < content.length) excerpt = excerpt + "..."
    return excerpt
}
