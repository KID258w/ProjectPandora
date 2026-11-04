package com.projectpandora.app.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonObject
import com.projectpandora.app.core.network.BusinessApi
import com.projectpandora.app.core.network.array
import com.projectpandora.app.core.network.businessMessage
import com.projectpandora.app.core.network.data
import com.projectpandora.app.core.network.long
import com.projectpandora.app.core.network.string
import com.projectpandora.app.ui.BackButton
import com.projectpandora.app.ui.Note
import com.projectpandora.app.ui.PageColumn
import com.projectpandora.app.ui.PageTitle
import com.projectpandora.app.ui.theme.PandoraColors as C
import kotlinx.coroutines.launch

private const val COMPANY_ITEMS_PATH = "api/v1/app/company-items/"

@Composable
fun CompanyItemsScreen(api: BusinessApi, onBack: () -> Unit, reloadKey: Int = 0) {
    var items by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var loadingDetailIds by remember { mutableStateOf(setOf<Long>()) }
    var reload by remember { mutableIntStateOf(0) }
    val details = remember { mutableStateMapOf<Long, JsonObject>() }
    val detailErrors = remember { mutableStateMapOf<Long, String>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadKey, reload) {
        loading = true
        error = ""
        try {
            items = api.get(COMPANY_ITEMS_PATH, mapOf("page_size" to "10")).data().array("results").take(10)
            expandedId = null
        } catch (failure: Exception) {
            error = failure.businessMessage()
        } finally {
            loading = false
        }
    }

    fun loadDetail(itemId: Long) {
        scope.launch {
            loadingDetailIds = loadingDetailIds + itemId
            detailErrors.remove(itemId)
            try {
                details[itemId] = api.get("$COMPANY_ITEMS_PATH$itemId/").data()
            } catch (failure: Exception) {
                detailErrors[itemId] = failure.businessMessage()
            } finally {
                loadingDetailIds = loadingDetailIds - itemId
            }
        }
    }

    fun toggle(itemId: Long) {
        if (expandedId == itemId) {
            expandedId = null
        } else {
            expandedId = itemId
            if (!details.containsKey(itemId) && itemId !in loadingDetailIds) loadDetail(itemId)
        }
    }

    PageColumn {
        BackButton(onBack)
        PageTitle("公司重要事项", small = true)
        Text("按公司发布顺序查看事项。点按列表项，在当前页面展开正文。", fontSize = 13.sp, lineHeight = 20.sp, color = C.Muted)

        if (loading && items.isEmpty()) {
            CircularProgressIndicator()
        }
        if (error.isNotEmpty()) {
            Note(error)
            TextButton(onClick = { reload++ }) { Text("重新加载") }
        }
        if (!loading && error.isEmpty() && items.isEmpty()) {
            Note("暂无已发布的公司重要事项。", background = Color(0xFFF5F7FA))
        }

        items.forEachIndexed { index, item ->
            val itemId = item.long("id")
            val expanded = expandedId == itemId
            val detail = details[itemId]
            Card(
                modifier = Modifier.fillMaxWidth().clickable { toggle(itemId) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, if (expanded) C.Orange.copy(alpha = 0.35f) else C.Line),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${index + 1}.", color = C.Orange, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.string("title"), color = C.Ink, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
                            Text(publishedDate(item.string("published_at")), color = C.Muted, fontSize = 11.sp)
                        }
                        Text(if (expanded) "收起⌃" else "展开⌄", color = C.Blue, fontSize = 12.sp)
                    }
                    Text(
                        item.string("summary"),
                        color = C.Muted,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (expanded) {
                        HorizontalDivider(color = C.Line)
                        when {
                            itemId in loadingDetailIds -> CircularProgressIndicator()
                            detailErrors[itemId] != null -> {
                                Note(detailErrors[itemId] ?: "正文加载失败。", background = Color(0xFFFFF4F2), color = C.Error)
                                TextButton(onClick = { loadDetail(itemId) }) { Text("重试") }
                            }
                            detail != null -> Text(
                                detail.string("body"),
                                color = C.Ink,
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

private fun publishedDate(value: String): String {
    val date = value.take(10)
    if (date.length != 10) return "发布时间待定"
    return "发布于 ${date.substring(0, 4)}年${date.substring(5, 7)}月${date.substring(8, 10)}日"
}
