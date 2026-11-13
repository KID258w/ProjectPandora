package com.projectpandora.app.feature.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonObject
import com.projectpandora.app.core.network.string

internal data class TagColorOption(val name: String, val hex: String)

internal val TagColorPalette = listOf(
    TagColorOption("蓝", "#C7DDFF"),
    TagColorOption("天青", "#BDEAF2"),
    TagColorOption("青绿", "#BFEBDD"),
    TagColorOption("草绿", "#CFE6B4"),
    TagColorOption("黄绿", "#E7ECB0"),
    TagColorOption("柠檬黄", "#FFE59E"),
    TagColorOption("杏橙", "#FFD0A3"),
    TagColorOption("珊瑚", "#FFBFB3"),
    TagColorOption("玫瑰粉", "#F1C1D8"),
    TagColorOption("薰衣草紫", "#D7C8F2"),
)

private val TagTextColor = Color(0xFF263238)
private val UnknownTagColor = Color(0xFFE2E8F0)

internal fun tagColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(UnknownTagColor)

@Composable
internal fun TagChip(tag: JsonObject) {
    Surface(
        color = tagColor(tag.string("color")),
        contentColor = TagTextColor,
        shape = RoundedCornerShape(percent = 50),
    ) {
        Text(
            text = tag.string("name"),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagChipList(tags: List<JsonObject>, emptyText: String? = null) {
    if (tags.isEmpty()) {
        emptyText?.let { Text(it, color = Color(0xFF737C86), fontSize = 12.sp) }
        return
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { TagChip(it) }
    }
}

@Composable
internal fun TagColorSelector(selectedColor: String?, onSelect: (TagColorOption) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("选择标签颜色", color = TagTextColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        TagColorPalette.chunked(5).forEach { rowColors ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                rowColors.forEach { option ->
                    val selected = selectedColor == option.hex
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(tagColor(option.hex))
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) TagTextColor else Color(0x66737C86),
                                shape = CircleShape,
                            )
                            .semantics { contentDescription = "${option.name}色，${option.hex}${if (selected) "，已选" else ""}" }
                            .clickable { onSelect(option) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Text("✓", color = TagTextColor, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        val selected = TagColorPalette.firstOrNull { it.hex == selectedColor }
        Text(
            text = selected?.let { "已选：${it.name} ${it.hex}" } ?: "请选择一种颜色",
            color = if (selected == null) Color(0xFF737C86) else TagTextColor,
            fontSize = 12.sp,
        )
    }
}
