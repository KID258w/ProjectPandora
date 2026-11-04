package com.projectpandora.app.feature.logs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.projectpandora.app.core.network.*
import com.projectpandora.app.feature.tags.TagChip
import com.projectpandora.app.feature.tags.TagColorSelector
import com.projectpandora.app.ui.*
import kotlinx.coroutines.launch

@Composable
fun TagManagementScreen(api: BusinessApi, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var error by remember { mutableStateOf("") }; var search by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }; var changing by remember { mutableStateOf<JsonObject?>(null) }
    var create by remember { mutableStateOf(false) }; var deleting by remember { mutableStateOf<JsonObject?>(null) }
    var name by remember { mutableStateOf("") }; var selectedColor by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    LaunchedEffect(revision) { try { rows = api.all("api/v1/app/tags/") } catch (e: Exception) { error = e.businessMessage() } }
    PageColumn {
        BackButton(onBack); PageTitle("标签管理", small = true); Text("删除后不可恢复，已有任务和日志关联将保留。")
        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("搜索标签") })
        PrimaryButton("新建标签", { name = ""; selectedColor = null; formError = ""; create = true })
        if (error.isNotEmpty()) Note(error)
        rows.filter { it.bool("is_active") && it.string("name").contains(search, true) }.forEach { tag ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TagChip(tag)
                }
                if (tag.bool("is_active")) {
                    TextButton(onClick = { name = tag.string("name"); selectedColor = tag.string("color"); formError = ""; changing = tag }) { Text("编辑") }
                    TextButton(onClick = { formError = ""; deleting = tag }) { Text("删除") }
                }
            }
        }
    }
    if (create || changing != null) {
        AlertDialog(onDismissRequest = { if (!busy) { create = false; changing = null; selectedColor = null } }, title = { Text(if (create) "新建标签" else "编辑标签") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("标签名称") }, singleLine = true)
            TagColorSelector(selectedColor) { selectedColor = it.hex }
            if (formError.isNotEmpty()) Text(formError, color = MaterialTheme.colorScheme.error)
        } }, confirmButton = { TextButton(enabled = !busy && name.isNotBlank() && selectedColor != null, onClick = {
            val color = selectedColor ?: return@TextButton
            if (name.isBlank()) { formError = "请输入标签名称。" } else scope.launch {
                busy = true; formError = ""
                try {
                    val body = json { addProperty("name", name.trim()); addProperty("color", color) }
                    val item = changing
                    if (item == null) api.post("api/v1/app/tags/", body)
                    else api.patch("api/v1/app/tags/${item.long("id")}/", body)
                    create = false; changing = null; selectedColor = null; error = ""; revision++
                }
                catch (e: Exception) { formError = e.businessMessage() } finally { busy = false }
            }
        }) { Text("保存") } }, dismissButton = { TextButton(enabled = !busy, onClick = { create = false; changing = null; selectedColor = null }) { Text("取消") } })
    }
    deleting?.let { tag -> AlertDialog(onDismissRequest = { if (!busy) deleting = null }, title = { Text("删除标签？") }, text = { Column { Text("删除后不可恢复，之后不能再关联；既有关联的任务和日志仍会显示该标签。") ; if (formError.isNotEmpty()) Text(formError, color = MaterialTheme.colorScheme.error) } }, confirmButton = { TextButton(enabled = !busy, onClick = { scope.launch { busy = true; formError = ""; try { api.delete("api/v1/app/tags/${tag.long("id")}/"); deleting = null; error = ""; revision++ } catch (e: Exception) { formError = e.businessMessage() } finally { busy = false } } }) { Text("删除") } }, dismissButton = { TextButton(enabled = !busy, onClick = { deleting = null }) { Text("取消") } }) }
}
