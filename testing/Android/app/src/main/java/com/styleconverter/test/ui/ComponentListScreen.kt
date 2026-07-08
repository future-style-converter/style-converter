package com.styleconverter.test.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import com.styleconverter.test.style.core.ir.IRComponent
import com.styleconverter.test.style.core.ir.IRDocument
import com.styleconverter.test.style.core.renderer.ComponentRenderer
import kotlinx.serialization.json.Json

// ── Color tokens (matching web) ──────────────────────────────────────────────
private val BgColor = Color(0xFF1A1A2E)
private val CardBg = Color(0x08FFFFFF)
private val CardHeaderBg = Color(0x0DFFFFFF)
private val CardFooterBg = Color(0x33000000)
private val BorderColor = Color(0x1AFFFFFF)
private val StatsBarBg = Color(0x0DFFFFFF)
private val PaginationBg = Color(0x08FFFFFF)
private val ButtonBg = Color(0x1AFFFFFF)
private val ButtonBorder = Color(0x33FFFFFF)
private val PropsPanelBg = Color(0x4D000000)
private val SearchBg = Color(0x0DFFFFFF)
private val SearchBorder = Color(0x1AFFFFFF)
private val HeaderBg = Color(0x4D000000)

private val TextWhite = Color.White
private val TextSubtitle = Color(0xFF888888)
private val TextIndex = Color(0xFF666666)
private val TextId = Color(0xFF666666)
private val TextPropCount = Color(0xFF666666)
private val TextStats = Color(0xFFAAAAAA)
private val TextPageInfo = Color(0xFF888888)
private val TextExpandBtn = Color(0xFF888888)
private val TextPropsCode = Color(0xFFAAAAAA)

private const val PAGE_SIZE = 50

/**
 * Flatten nested components for display (matches web flattenComponents).
 */
private fun flattenComponents(components: List<IRComponent>): List<IRComponent> {
    val result = mutableListOf<IRComponent>()
    fun traverse(component: IRComponent) {
        result.add(component)
        component.children?.forEach { traverse(it) }
    }
    components.forEach { traverse(it) }
    return result
}

/**
 * Main screen displaying all components from the IR document.
 * Layout and styling matches the Web testing environment exactly.
 */
@Composable
fun ComponentListScreen() {
    val context = LocalContext.current
    var document by remember { mutableStateOf<IRDocument?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showNested by remember { mutableStateOf(true) }
    var page by remember { mutableStateOf(0) }

    // Load JSON on first composition
    LaunchedEffect(Unit) {
        try {
            val jsonString = context.assets.open("tmpOutput.json")
                .bufferedReader()
                .use { it.readText() }
            val json = Json { ignoreUnknownKeys = true }
            document = json.decodeFromString<IRDocument>(jsonString)
        } catch (e: Exception) {
            error = "Failed to load IR: ${e.message}"
            e.printStackTrace()
        }
    }

    // Reset page when search or nested toggle changes
    LaunchedEffect(searchQuery, showNested) {
        page = 0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        // ── Header ───────────────────────────────────────────────────────
        Header(
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it }
        )

        when {
            error != null -> ErrorDisplay(error!!)
            document == null -> LoadingIndicator()
            else -> {
                val allComponents = remember(document, searchQuery, showNested) {
                    val components = if (showNested) {
                        flattenComponents(document!!.components)
                    } else {
                        document!!.components
                    }
                    if (searchQuery.isBlank()) components
                    else components.filter {
                        it.name.contains(searchQuery, ignoreCase = true) ||
                        it.id.contains(searchQuery, ignoreCase = true)
                    }
                }

                val totalPages = ((allComponents.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
                val pageComponents = allComponents.drop(page * PAGE_SIZE).take(PAGE_SIZE)

                GalleryContent(
                    topLevelCount = document!!.components.size,
                    totalCount = allComponents.size,
                    showNested = showNested,
                    onShowNestedChange = { showNested = it },
                    page = page,
                    totalPages = totalPages,
                    allCount = allComponents.size,
                    onPageChange = { page = it.coerceIn(0, (totalPages - 1).coerceAtLeast(0)) },
                    components = pageComponents,
                    pageOffset = page * PAGE_SIZE
                )
            }
        }
    }
}

@Composable
private fun Header(
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeaderBg)
            .drawBottomBorder(1.dp, BorderColor)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Title row — baseline-aligned like web's alignItems:baseline
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Style Converter",
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextWhite,
                modifier = Modifier.alignByBaseline()
            )
            Text(
                text = "Android Testing",
                fontSize = 14.sp,
                lineHeight = 17.sp,
                color = TextSubtitle,
                modifier = Modifier.alignByBaseline()
            )
        }

        // Search input
        BasicTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            singleLine = true,
            textStyle = TextStyle(
                color = TextWhite,
                fontSize = 14.sp
            ),
            cursorBrush = SolidColor(TextWhite),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SearchBg, RoundedCornerShape(6.dp))
                        .border(1.dp, SearchBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search components...",
                            color = TextSubtitle,
                            fontSize = 14.sp,
                            lineHeight = 17.sp
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun GalleryContent(
    topLevelCount: Int,
    totalCount: Int,
    showNested: Boolean,
    onShowNestedChange: (Boolean) -> Unit,
    page: Int,
    totalPages: Int,
    allCount: Int,
    onPageChange: (Int) -> Unit,
    components: List<IRComponent>,
    pageOffset: Int
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Stats bar
        item {
            StatsBar(
                topLevelCount = topLevelCount,
                totalCount = totalCount,
                showNested = showNested,
                onShowNestedChange = onShowNestedChange
            )
        }

        // Pagination
        item {
            PaginationBar(
                page = page,
                totalPages = totalPages,
                allCount = allCount,
                onPageChange = onPageChange
            )
        }

        // Component cards
        itemsIndexed(components, key = { _, c -> c.id }) { index, component ->
            ComponentCard(
                component = component,
                index = pageOffset + index
            )
        }
    }
}

@Composable
private fun StatsBar(
    topLevelCount: Int,
    totalCount: Int,
    showNested: Boolean,
    onShowNestedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(StatsBarBg, RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$topLevelCount") }
                    append(" top-level")
                },
                fontSize = 14.sp,
                lineHeight = 17.sp,
                color = TextStats
            )
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$totalCount") }
                    append(" ${if (showNested) "total" else "filtered"}")
                },
                fontSize = 14.sp,
                lineHeight = 17.sp,
                color = TextStats
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.clickable { onShowNestedChange(!showNested) }
        ) {
            // Custom checkbox matching web's native HTML checkbox (small square)
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(
                        if (showNested) Color(0xFF3B82F6) else Color.Transparent,
                        RoundedCornerShape(2.dp)
                    )
                    .border(
                        1.dp,
                        if (showNested) Color(0xFF3B82F6) else TextStats,
                        RoundedCornerShape(2.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (showNested) {
                    Text(
                        text = "✓",
                        fontSize = 10.sp,
                        lineHeight = 10.sp,
                        color = Color.White
                    )
                }
            }
            Text(
                text = "Include nested",
                fontSize = 14.sp,
                lineHeight = 17.sp,
                color = TextStats
            )
        }
    }
}

@Composable
private fun PaginationBar(
    page: Int,
    totalPages: Int,
    allCount: Int,
    onPageChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PaginationBg, RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PaginationButton("First", enabled = page > 0) { onPageChange(0) }
        PaginationButton("Prev", enabled = page > 0) { onPageChange(page - 1) }

        val start = page * PAGE_SIZE + 1
        val end = minOf((page + 1) * PAGE_SIZE, allCount)
        Text(
            text = "Page ${page + 1} of $totalPages ($start-$end of $allCount)",
            fontSize = 13.sp,
            lineHeight = 16.sp,
            color = TextPageInfo,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
        )

        PaginationButton("Next", enabled = page < totalPages - 1) { onPageChange(page + 1) }
        PaginationButton("Last", enabled = page < totalPages - 1) { onPageChange(totalPages - 1) }
    }
}

@Composable
private fun PaginationButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier = Modifier
            .background(ButtonBg, RoundedCornerShape(4.dp))
            .border(1.dp, ButtonBorder, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            color = TextStats.copy(alpha = alpha)
        )
    }
}

@Composable
private fun ComponentCard(component: IRComponent, index: Int) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg)
            .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
    ) {
        // ── Card Header ──────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardHeaderBg)
                .drawBottomBorder(1.dp, BorderColor)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "#${index + 1}",
                fontSize = 12.sp,
                lineHeight = 14.sp,
                color = TextIndex,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = component.name,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextWhite,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = component.id,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                color = TextId,
                fontFamily = FontFamily.Monospace
            )
        }

        // ── Card Content (rendered component) ────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp)
                .clipToBounds()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            ComponentRenderer.RenderComponent(component)
        }

        // ── Card Footer ──────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawTopBorder(1.dp, BorderColor)
                .background(CardFooterBg)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val childInfo = if (component.children != null && component.children!!.isNotEmpty()) {
                ", ${component.children!!.size} children"
            } else ""
            Text(
                text = "${component.properties.size} props$childInfo",
                fontSize = 12.sp,
                lineHeight = 14.sp,
                color = TextPropCount
            )
            Box(
                modifier = Modifier
                    .border(1.dp, ButtonBorder, RoundedCornerShape(4.dp))
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isExpanded) "Hide Props" else "Show Props",
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    color = TextExpandBtn
                )
            }
        }

        // ── Props Panel (expandable) ─────────────────────────────────────
        if (isExpanded) {
            val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
            val propsText = try {
                component.properties.joinToString(",\n") { prop ->
                    "  ${json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), kotlinx.serialization.json.JsonObject(mapOf(
                        "type" to kotlinx.serialization.json.JsonPrimitive(prop.type),
                        "data" to prop.data
                    )))}"
                }.let { "[\n$it\n]" }
            } catch (_: Exception) {
                component.properties.joinToString("\n") { "${it.type}: ${it.data}" }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawTopBorder(1.dp, BorderColor)
                    .background(PropsPanelBg)
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = propsText,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    color = TextPropsCode,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun ErrorDisplay(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Error Loading Document",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFF87171)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            fontSize = 14.sp,
            color = Color(0xFFF87171)
        )
    }
}

@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFF60A5FA))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Loading IR document...", color = TextSubtitle)
        }
    }
}

// ── Border drawing modifiers ─────────────────────────────────────────────────

private fun Modifier.drawBottomBorder(width: androidx.compose.ui.unit.Dp, color: Color): Modifier =
    this.then(
        Modifier.drawWithContent {
            drawContent()
            drawLine(
                color = color,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = width.toPx()
            )
        }
    )

private fun Modifier.drawTopBorder(width: androidx.compose.ui.unit.Dp, color: Color): Modifier =
    this.then(
        Modifier.drawWithContent {
            drawContent()
            drawLine(
                color = color,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = width.toPx()
            )
        }
    )
