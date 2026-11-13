package com.projectpandora.app.feature.organization

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
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
import com.projectpandora.app.feature.profile.ReadOnlyProfileAvatar
import com.projectpandora.app.ui.BackButton
import com.projectpandora.app.ui.Note
import com.projectpandora.app.ui.PageColumn
import com.projectpandora.app.ui.PageTitle
import com.projectpandora.app.ui.theme.PandoraColors as C
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
fun OrganizationRelationshipsScreen(
    api: BusinessApi,
    onBack: () -> Unit,
    onOpenMember: (Long) -> Unit,
) {
    var keyword by remember { mutableStateOf("") }
    var tree by remember { mutableStateOf<JsonObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(keyword, retry) {
        loading = true
        error = ""
        tree = null
        try {
            delay(if (keyword.isBlank()) 0 else 250)
            tree = api.get(ROOT + "organization/tree/", mapOf("keyword" to keyword.trim())).data()
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            error = failure.businessMessage()
        } finally {
            loading = false
        }
    }

    PageColumn {
        BackButton(onBack)
        PageTitle("组织关系", small = true)
        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it },
            modifier = Modifier.fillMaxWidth().testTag("organization-search"),
            label = { Text("搜索姓名或职位") },
            singleLine = true,
        )
        if (loading && tree == null) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        if (error.isNotBlank()) {
            Note(error)
            androidx.compose.material3.TextButton(onClick = { retry++ }) { Text("重试") }
        }
        val roots = tree?.array("roots").orEmpty()
        if (!loading && error.isBlank() && roots.isEmpty()) {
            Note(if (keyword.isBlank()) "当前没有可显示的组织关系。" else "没有找到匹配的姓名或职位。")
        }
        roots.forEach { node ->
            OrganizationTreeNode(
                api = api,
                node = node,
                depth = 0,
                expanded = expanded,
                showOrganizationDetails = keyword.isNotBlank(),
                onToggle = { key -> expanded = if (key in expanded) expanded - key else expanded + key },
                onOpenMember = onOpenMember,
            )
        }
        if (loading && tree != null) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).size(22.dp))
        Spacer(Modifier.size(4.dp))
    }
}

@Composable
private fun OrganizationTreeNode(
    api: BusinessApi,
    node: JsonObject,
    depth: Int,
    expanded: Set<String>,
    showOrganizationDetails: Boolean,
    onToggle: (String) -> Unit,
    onOpenMember: (Long) -> Unit,
) {
    val type = node.string("node_type")
    val children = node.array("children")
    val key = nodeKey(node)
    val isExpanded = key in expanded
    val isPerson = type == "person"
    val title = if (isPerson) node.string("display_name") else node.string("name")
    val role = node.string("business_role")
    val unitType = when (type) {
        "department" -> "部门"
        "team" -> "团队"
        else -> ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 14).dp)
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, C.Line, RoundedCornerShape(14.dp))
            .then(
                if (isPerson) Modifier.clickable { onOpenMember(node.long("id")) }
                else if (children.isNotEmpty()) Modifier.clickable { onToggle(key) }
                else Modifier,
            )
            .padding(start = 14.dp, top = 9.dp, bottom = 9.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (isPerson) OrganizationTreeMemberAvatar(api, node.long("id"), title)
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (!isPerson) Text(unitType, color = C.Muted, fontSize = 11.sp)
            else if (showOrganizationDetails && role != "founder") {
                val organization = listOf(node.string("department_name"), node.string("team_name"))
                    .filter(String::isNotBlank)
                    .joinToString(" · ")
                if (organization.isNotBlank()) {
                    Text(organization, color = C.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (isPerson) {
            Text(roleLabel(role), color = C.Blue, fontSize = 12.sp)
        }
        if (children.isNotEmpty()) {
            IconButton(onClick = { onToggle(key) }, modifier = Modifier.size(36.dp)) {
                Text(if (isExpanded) "⌄" else "›", color = C.Muted, fontSize = 21.sp)
            }
        }
    }
    if (isExpanded) {
        children.forEach { child ->
            OrganizationTreeNode(api, child, depth + 1, expanded, showOrganizationDetails, onToggle, onOpenMember)
        }
    }
}

private fun nodeKey(node: JsonObject): String = "${node.string("node_type")}:${node.long("id")}"

@Composable
private fun OrganizationTreeMemberAvatar(api: BusinessApi, memberId: Long, name: String) {
    var avatarBytes by remember(memberId) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(api, memberId) {
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
    ReadOnlyProfileAvatar(name, avatarBytes, size = 38.dp)
}
