package com.projectpandora.app.feature.logs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.google.gson.JsonObject
import com.projectpandora.app.core.network.*
import com.projectpandora.app.ui.*
import kotlinx.coroutines.launch

@Composable
fun TagManagementScreen(api: BusinessApi, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var error by remember { mutableStateOf("") }; var search by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }; var changing by remember { mutableStateOf<JsonObject?>(null) }
    var create by remember { mutableStateOf(false) }; var deactivate by remember { mutableStateOf<JsonObject?>(null) }
    LaunchedEffect(revision) { try { rows = api.all("api/v1/app/tags/", mapOf("include_inactive" to "true")) } catch (e: Exception) { error = e.businessMessage() } }
    fun run(action: suspend () -> Unit) { scope.launch { try { action(); error = ""; revision++ } catch (e: Exception) { error = e.businessMessage() } } }
    PageColumn {
        BackButton(onBack); PageTitle("标签管理", small = true); Text("停用后不可恢复，已有日志关联将保留。")
        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("搜索标签") })
        PrimaryButton("新建标签", { create = true })
        if (error.isNotEmpty()) Note(error)
        rows.filter { it.string("name").contains(search, true) }.forEach { tag ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tag.string("name") + if (tag.bool("is_active")) "" else "（已停用）", Modifier.weight(1f))
                if (tag.bool("is_active")) { TextButton(onClick = { changing = tag }) { Text("重命名") }; TextButton(onClick = { deactivate = tag }) { Text("停用") } }
            }
        }
    }
    if (create || changing != null) {
        var name by remember(create, changing) { mutableStateOf(changing?.string("name") ?: "") }
        AlertDialog(onDismissRequest = { create = false; changing = null }, title = { Text(if (create) "新建标签" else "重命名") }, text = { OutlinedTextField(name, { name = it }, label = { Text("标签名称") }) }, confirmButton = { TextButton(onClick = {
            if (name.isNotBlank()) { val item = changing; run { if (item == null) api.post("api/v1/app/tags/", json { addProperty("name", name.trim()) }) else api.patch("api/v1/app/tags/${item.long("id")}/", json { addProperty("name", name.trim()) }) }; create = false; changing = null }
        }) { Text("保存") } }, dismissButton = { TextButton(onClick = { create = false; changing = null }) { Text("取消") } })
    }
    deactivate?.let { tag -> AlertDialog(onDismissRequest = { deactivate = null }, title = { Text("停用标签？") }, text = { Text("停用不可恢复，之后不能再关联；已有日志关联会保留。") }, confirmButton = { TextButton(onClick = { run { api.delete("api/v1/app/tags/${tag.long("id")}/") }; deactivate = null }) { Text("停用") } }, dismissButton = { TextButton(onClick = { deactivate = null }) { Text("取消") } }) }
}
