package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

// Pre-compiled regexes (hoisted to top-level so they are built only once instead of
// being recompiled on every block render / parse pass — a meaningful perf win on long notes).
private val LINK_REGEX = """(!?\[([^\]]*)\]\(([^)]+)\))""".toRegex()
private val BOLD_REGEX = """(\*\*([^*]+)\*\*)""".toRegex()
private val STANDALONE_IMAGE_REGEX = """^!\[([^\]]*)\]\(([^)]+)\)$""".toRegex()
private val EMBEDDED_IMAGE_REGEX = """(!\[([^\]]*)\]\(([^)]+)\))""".toRegex()

// Sealed class representing parsed markdown blocks
sealed class MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class BulletPoint(val content: String) : MarkdownBlock()
    data class Quote(val content: String) : MarkdownBlock()
    data class TextBlock(val content: String) : MarkdownBlock()
    data class InlineImage(val alt: String, val uri: String) : MarkdownBlock()
}

@Composable
fun MarkdownRenderer(
    text: String,
    onNoteLinkClick: (String) -> Unit,
    onWebLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onTipLinkClick: (String) -> Unit = {}
) {
    // Parse the raw markdown text into blocks
    val blocks = remember(text) { parseMarkdown(text) }
    var activeViewerImage by remember { mutableStateOf<Pair<String, String>?>(null) }
    val onImageLinkClick: (String, String) -> Unit = { uri, alt ->
        activeViewerImage = Pair(uri, alt)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Header -> {
                    MarkdownHeader(block)
                }
                is MarkdownBlock.BulletPoint -> {
                    MarkdownBulletPoint(block, onNoteLinkClick, onWebLinkClick, onImageLinkClick, onTipLinkClick)
                }
                is MarkdownBlock.Quote -> {
                    MarkdownQuote(block, onNoteLinkClick, onWebLinkClick, onImageLinkClick, onTipLinkClick)
                }
                is MarkdownBlock.TextBlock -> {
                    MarkdownTextBlock(block.content, onNoteLinkClick, onWebLinkClick, onImageLinkClick, onTipLinkClick)
                }
                is MarkdownBlock.InlineImage -> {
                    MarkdownInlineImage(block, onImageLinkClick)
                }
            }
        }
    }

    if (activeViewerImage != null) {
        FullScreenImageViewer(
            uri = activeViewerImage!!.first,
            alt = activeViewerImage!!.second,
            onDismiss = { activeViewerImage = null }
        )
    }
}

@Composable
fun MarkdownHeader(header: MarkdownBlock.Header) {
    val style = when (header.level) {
        1 -> MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.primary
        )
        2 -> MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp,
            color = MaterialTheme.colorScheme.secondary
        )
        else -> MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.tertiary
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.End // RTL layout by default for Persian
    ) {
        Text(
            text = header.text,
            style = style,
            textAlign = TextAlign.Right,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(if (header.level == 1) 0.4f else 0.2f)
                .height(if (header.level == 1) 3.dp else 1.5.dp)
                .background(
                    color = when (header.level) {
                        1 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.secondary
                    },
                    shape = RoundedCornerShape(2.dp)
                )
                .align(Alignment.End)
        )
    }
}

@Composable
fun MarkdownBulletPoint(
    bullet: MarkdownBlock.BulletPoint,
    onNoteLinkClick: (String) -> Unit,
    onWebLinkClick: (String) -> Unit,
    onImageLinkClick: (String, String) -> Unit,
    onTipLinkClick: (String) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top
    ) {
        // Text part
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            val colorScheme = MaterialTheme.colorScheme
            val annotated = remember(bullet.content, colorScheme) {
                buildRichAnnotatedString(
                    rawText = bullet.content,
                    colorScheme = colorScheme
                )
            }
            RichTextClickable(
                annotated = annotated,
                onNoteLinkClick = onNoteLinkClick,
                onWebLinkClick = onWebLinkClick,
                onImageLinkClick = onImageLinkClick,
                onTipLinkClick = onTipLinkClick
            )
        }
        
        // Custom bullet icon for RTL
        Box(
            modifier = Modifier
                .padding(top = 8.dp, start = 4.dp)
                .size(6.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(50)
                )
        )
    }
}

@Composable
fun MarkdownQuote(
    quote: MarkdownBlock.Quote,
    onNoteLinkClick: (String) -> Unit,
    onWebLinkClick: (String) -> Unit,
    onImageLinkClick: (String, String) -> Unit,
    onTipLinkClick: (String) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min) // Allows vertical fillMaxHeight on the accent strip to match text height
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f))
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            val colorScheme = MaterialTheme.colorScheme
            val annotated = remember(quote.content, colorScheme) {
                buildRichAnnotatedString(
                    rawText = quote.content,
                    colorScheme = colorScheme.copy(
                        onSurface = colorScheme.onSecondaryContainer // Adapts to light & dark themes
                    ),
                    isItalic = true
                )
            }
            RichTextClickable(
                annotated = annotated,
                onNoteLinkClick = onNoteLinkClick,
                onWebLinkClick = onWebLinkClick,
                onImageLinkClick = onImageLinkClick,
                onTipLinkClick = onTipLinkClick
            )
        }

        // Quote bar accent on the RIGHT of quote card (RTL alignment)
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(2.dp)
                )
        )
    }
}

@Composable
fun MarkdownTextBlock(
    content: String,
    onNoteLinkClick: (String) -> Unit,
    onWebLinkClick: (String) -> Unit,
    onImageLinkClick: (String, String) -> Unit,
    onTipLinkClick: (String) -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val annotated = remember(content, colorScheme) {
        buildRichAnnotatedString(content, colorScheme)
    }
    RichTextClickable(
        annotated = annotated,
        onNoteLinkClick = onNoteLinkClick,
        onWebLinkClick = onWebLinkClick,
        onImageLinkClick = onImageLinkClick,
        onTipLinkClick = onTipLinkClick
    )
}

@Composable
fun FullScreenImageViewer(
    uri: String,
    alt: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
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
            if (uri.startsWith("content://") || uri.startsWith("file://") || File(uri).exists()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = alt,
                    contentScale = ContentScale.Fit,
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize().padding(32.dp)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Image,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = alt.ifEmpty { "تصویر پیوست شده" },
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Top Bar
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(top = 40.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "بستن")
                    }

                    Text(
                        text = alt.ifEmpty { "تصویر ضمیمه شده" },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Right
                    )
                }
            }

            // Zoom helper tips
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "برای زوم دو انگشتی بکشید و تکان دهید",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun MarkdownInlineImage(
    imageBlock: MarkdownBlock.InlineImage,
    onImageLinkClick: (String, String) -> Unit
) {
    Box(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .wrapContentWidth(Alignment.End) // RTL alignment
    ) {
        Button(
            onClick = { onImageLinkClick(imageBlock.uri, imageBlock.alt) },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = imageBlock.alt.ifEmpty { "تصویر پیوست شده" },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Interactive custom annotated string clicking
@Composable
fun RichTextClickable(
    annotated: AnnotatedString,
    onNoteLinkClick: (String) -> Unit,
    onWebLinkClick: (String) -> Unit,
    onImageLinkClick: (String, String) -> Unit,
    onTipLinkClick: (String) -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val inlineContent = remember(colorScheme) {
      mapOf(
        "web_link" to InlineTextContent(
            Placeholder(
                width = 20.sp,
                height = 20.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
            )
        ) {
            Icon(
                imageVector = Icons.Default.Link,
                contentDescription = "Web Link",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp).padding(horizontal = 2.dp)
            )
        },
        "note_link" to InlineTextContent(
            Placeholder(
                width = 20.sp,
                height = 20.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
            )
        ) {
            Icon(
                imageVector = Icons.Default.Bookmark,
                contentDescription = "Note Link",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(14.dp).padding(horizontal = 2.dp)
            )
        },
        "tip_link" to InlineTextContent(
            Placeholder(
                width = 20.sp,
                height = 20.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
            )
        ) {
            Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = "Tip Link",
                tint = Color(0xFFF1C40F),
                modifier = Modifier.size(14.dp).padding(horizontal = 2.dp)
            )
        },
        "image_link" to InlineTextContent(
            Placeholder(
                width = 20.sp,
                height = 20.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
            )
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = "Image Link",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(14.dp).padding(horizontal = 2.dp)
            )
        }
      )
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotated,
        style = LocalTextStyle.current.copy(
            textAlign = TextAlign.Right,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onSurface
        ),
        inlineContent = inlineContent,
        onTextLayout = { textLayoutResult = it },
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(annotated) {
                detectTapGestures { pos ->
                    textLayoutResult?.let { layoutResult ->
                        val offset = layoutResult.getOffsetForPosition(pos)
                        val urlAnnotations = annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
                        urlAnnotations.firstOrNull()?.let { annotation ->
                            onWebLinkClick(annotation.item)
                        }

                        val noteAnnotations = annotated.getStringAnnotations(tag = "NOTE", start = offset, end = offset)
                        noteAnnotations.firstOrNull()?.let { annotation ->
                            onNoteLinkClick(annotation.item)
                        }

                        val tipAnnotations = annotated.getStringAnnotations(tag = "TIP", start = offset, end = offset)
                        tipAnnotations.firstOrNull()?.let { annotation ->
                            onTipLinkClick(annotation.item)
                        }

                        val imageAnnotations = annotated.getStringAnnotations(tag = "IMAGE", start = offset, end = offset)
                        imageAnnotations.firstOrNull()?.let { annotation ->
                            val parts = annotation.item.split("|")
                            val uri = parts.getOrNull(0) ?: ""
                            val label = parts.getOrNull(1) ?: ""
                            onImageLinkClick(uri, label)
                        }
                    }
                }
            }
    )
}

// Build annotated text by styling bold, regular and parsing Inline links e.g. [label](url)
fun buildRichAnnotatedString(
    rawText: String,
    colorScheme: ColorScheme,
    isItalic: Boolean = false
): AnnotatedString {
    return buildAnnotatedString {
        // Multi-stage parser matching both links and styles
        var index = 0

        // Find all links: web, note or images
        val matches = LINK_REGEX.findAll(rawText).toList()
        
        if (matches.isEmpty()) {
            // Apply standard text options with bold styling
            appendStyledText(rawText, colorScheme, isItalic)
        } else {
            for (match in matches) {
                // Append text before match
                if (match.range.first > index) {
                    appendStyledText(rawText.substring(index, match.range.first), colorScheme, isItalic)
                }
                
                val fullMatchString = match.value
                val isImage = fullMatchString.startsWith("!")
                val label = match.groupValues[2]
                val target = match.groupValues[3]
                
                if (isImage) {
                    pushStringAnnotation(tag = "IMAGE", annotation = "$target|$label")
                    pushStyle(
                        SpanStyle(
                            color = colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.Bold,
                            background = colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                        )
                    )
                    appendInlineContent("image_link", "[image]")
                    append(" $label ")
                    pop()
                    pop()
                } else {
                    val isNote = target.startsWith("note://")
                    val isTip = target.startsWith("tip://")
                    
                    if (isNote || isTip) {
                        val noteIdOrTitle = if (isNote) target.substring(7) else target.substring(6)
                        pushStringAnnotation(tag = if (isNote) "NOTE" else "TIP", annotation = noteIdOrTitle)
                        pushStyle(
                            SpanStyle(
                                color = if (isNote) colorScheme.onSecondaryContainer else Color(0xFF7D6608),
                                fontWeight = FontWeight.Bold,
                                background = if (isNote) colorScheme.secondaryContainer.copy(alpha = 0.7f) else Color(0xFFFEF9E7).copy(alpha = 0.7f)
                            )
                        )
                        appendInlineContent(if (isNote) "note_link" else "tip_link", if (isNote) "[note]" else "[tip]")
                        append(" $label ")
                        pop()
                        pop()
                    } else {
                        // Web URL Link
                        pushStringAnnotation(tag = "URL", annotation = target)
                        pushStyle(
                            SpanStyle(
                                color = colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                background = colorScheme.primaryContainer.copy(alpha = 0.7f)
                            )
                        )
                        appendInlineContent("web_link", "[link]")
                        append(" $label ")
                        pop()
                        pop()
                    }
                }
                index = match.range.last + 1
            }
            
            // Append any remaining text
            if (index < rawText.length) {
                appendStyledText(rawText.substring(index), colorScheme, isItalic)
            }
        }
    }
}

private fun AnnotatedString.Builder.appendStyledText(text: String, colorScheme: ColorScheme, isItalic: Boolean) {
    val matches = BOLD_REGEX.findAll(text).toList()
    
    var localIndex = 0
    if (matches.isEmpty()) {
        pushStyle(SpanStyle(
            fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal
        ))
        append(text)
        pop()
    } else {
        for (match in matches) {
            if (match.range.first > localIndex) {
                pushStyle(SpanStyle(
                    fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal
                ))
                append(text.substring(localIndex, match.range.first))
                pop()
            }
            
            val boldTextVal = match.groupValues[2]
            pushStyle(SpanStyle(
                fontWeight = FontWeight.Bold,
                fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                color = colorScheme.onSurface
            ))
            append(boldTextVal)
            pop()
            
            localIndex = match.range.last + 1
        }
        if (localIndex < text.length) {
            pushStyle(SpanStyle(
                fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal
            ))
            append(text.substring(localIndex))
            pop()
        }
    }
}

// Main high-performance line-by-line parser to identify headers, images, blockquotes, bullets
fun parseMarkdown(rawContent: String): List<MarkdownBlock> {
    val lines = rawContent.lines()
    val blocks = mutableListOf<MarkdownBlock>()

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue
        
        // Headers selector
        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length
            if (level in 1..3 && trimmed.length > level && trimmed[level] == ' ') {
                val headerText = trimmed.substring(level + 1).trim()
                blocks.add(MarkdownBlock.Header(level, headerText))
                continue
            }
        }
        
        // Bullet point selector
        if (trimmed.startsWith("* ") || trimmed.startsWith("- ")) {
            blocks.add(MarkdownBlock.BulletPoint(trimmed.substring(2).trim()))
            continue
        }
        
        // Blockquote Selector
        if (trimmed.startsWith("> ")) {
            blocks.add(MarkdownBlock.Quote(trimmed.substring(2).trim()))
            continue
        }
        
        // Inline standalone image selector: ![alt](uri)
        val imageMatch = STANDALONE_IMAGE_REGEX.matchEntire(trimmed)
        if (imageMatch != null) {
            val alt = imageMatch.groupValues[1]
            val uri = imageMatch.groupValues[2]
            blocks.add(MarkdownBlock.InlineImage(alt, uri))
            continue
        }
        
        // Standard paragraphs might also contain image inline inside lines
        // We look for any embedded standalone image block, otherwise standard paragraph
        val imagesInLine = EMBEDDED_IMAGE_REGEX.findAll(line).toList()
        
        if (imagesInLine.size == 1 && line.trim().startsWith("!") && line.trim().endsWith(")")) {
            // Treat line containing only an image as an actual visual image block!
            val match = imagesInLine.first()
            blocks.add(MarkdownBlock.InlineImage(match.groupValues[2], match.groupValues[3]))
        } else {
            // General text block contains links and bold items
            blocks.add(MarkdownBlock.TextBlock(line))
        }
    }
    
    return blocks
}
