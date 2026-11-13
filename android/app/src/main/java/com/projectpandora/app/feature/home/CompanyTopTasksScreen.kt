package com.projectpandora.app.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

private const val COMPANY_TOP_TASKS_PATH = "api/v1/app/home/company-top-tasks/"

@Composable
fun CompanyTopTasksScreen(
    api: BusinessApi,
    onBack: () -> Unit,
    onTask: (Long) -> Unit,
    reloadKey: Int = 0,
) {
    var tasks by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey, reload) {
        loading = true
        error = ""
        try {
            tasks = api.get(COMPANY_TOP_TASKS_PATH, mapOf("page_size" to "10"))
                .data().array("results").take(10)
        } catch (failure: Exception) {
            error = failure.businessMessage()
        } finally {
            loading = false
        }
    }

    PageColumn {
        BackButton(onBack)
        PageTitle("任务看版", small = true)
        Text("按任务来源和截止时间排序，最多显示 10 项。", fontSize = 13.sp, color = C.Muted)
        if (loading) CircularProgressIndicator()
        if (error.isNotEmpty()) {
            Note(error)
            TextButton(onClick = { reload++ }) { Text("重试") }
        } else if (!loading && tasks.isEmpty()) {
            Note("暂无符合条件的任务")
        }
        tasks.forEach { task ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onTask(task.long("id")) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = C.BluePale),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        task.string("title"),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${taskStatus(task)} · 截止 ${dashboardDate(task.string("due_at"))}", fontSize = 12.sp, color = C.Muted)
                    }
                }
            }
        }
    }
}

private fun taskStatus(task: JsonObject): String =
    if (task.string("completion_status") == "completed") "已完成" else "未完成"

private fun dashboardDate(value: String): String = value.take(10).replace('-', '/')
