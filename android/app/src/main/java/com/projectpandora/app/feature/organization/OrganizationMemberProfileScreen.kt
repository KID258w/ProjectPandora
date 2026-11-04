package com.projectpandora.app.feature.organization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonObject
import com.projectpandora.app.core.network.BusinessApi
import com.projectpandora.app.core.network.bool
import com.projectpandora.app.core.network.businessMessage
import com.projectpandora.app.core.network.data
import com.projectpandora.app.core.network.string
import com.projectpandora.app.feature.profile.ReadOnlyProfileAvatar
import com.projectpandora.app.ui.BackButton
import com.projectpandora.app.ui.Note
import com.projectpandora.app.ui.PageColumn
import com.projectpandora.app.ui.PageTitle
import com.projectpandora.app.ui.theme.PandoraColors as C
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ROOT = "api/v1/app/"

private fun roleLabel(role: String): String = when (role) {
    "founder" -> "创始人"
    "department_manager" -> "部门经理"
    "team_leader" -> "团队长"
    "employee" -> "员工"
    else -> "职位未知"
}

@Composable
fun OrganizationMemberProfileScreen(
    api: BusinessApi,
    memberId: Long,
    onBack: () -> Unit,
    onOpenTasks: (Long) -> Unit,
    onOpenLogs: (Long) -> Unit,
) {
    var profile by remember(memberId) { mutableStateOf<JsonObject?>(null) }
    var avatarBytes by remember(memberId) { mutableStateOf<ByteArray?>(null) }
    var loading by remember(memberId) { mutableStateOf(true) }
    var error by remember(memberId) { mutableStateOf("") }
    var retry by remember(memberId) { mutableIntStateOf(0) }

    LaunchedEffect(memberId, retry) {
        loading = true
        error = ""
        try {
            profile = api.get(ROOT + "organization/members/$memberId/").data()
        } catch (failure: Exception) {
            error = failure.businessMessage()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(memberId) {
        try {
            val response = api.getBody(ROOT + "organization/members/$memberId/avatar/")
            avatarBytes = if (response.isSuccessful) {
                withContext(Dispatchers.IO) { response.body()?.use { it.bytes() } }
            } else {
                response.errorBody()?.close()
                null
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            avatarBytes = null
        }
    }

    PageColumn {
        BackButton(onBack)
        PageTitle("成员资料", small = true)
        if (loading) androidx.compose.material3.CircularProgressIndicator()
        if (error.isNotBlank()) {
            Note(error)
            androidx.compose.material3.TextButton(onClick = { retry++ }) { Text("重试") }
        }
        profile?.let { data ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReadOnlyProfileAvatar(data.string("display_name"), avatarBytes)
                Text(data.string("display_name"), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.Ink)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("手机号", fontSize = 12.sp, color = C.Muted)
                Text(data.string("phone"), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = C.Ink)
            }
            MemberInfoRows(
                listOf(
                    "职位" to roleLabel(data.string("business_role")),
                    "部门" to data.string("department_name").ifBlank { "—" },
                    "团队" to data.string("team_name").ifBlank { "—" },
                ),
            )
            if (!data.bool("is_self") && data.bool("can_view_tasks")) {
                OutlinedButton(
                    onClick = { onOpenTasks(memberId) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = C.Ink),
                ) { Text("查看任务", fontSize = 15.sp) }
            }
            if (!data.bool("is_self") && data.bool("can_view_logs")) {
                OutlinedButton(
                    onClick = { onOpenLogs(memberId) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = C.Ink),
                ) { Text("查看日志", fontSize = 15.sp) }
            }
        }
    }
}

@Composable
private fun MemberInfoRows(rows: List<Pair<String, String>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, C.Line),
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            rows.forEachIndexed { index, (label, value) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.width(90.dp), fontSize = 14.sp, color = C.Muted)
                    Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = C.Ink)
                }
                if (index != rows.lastIndex) androidx.compose.material3.HorizontalDivider(color = C.Line)
            }
        }
    }
}
