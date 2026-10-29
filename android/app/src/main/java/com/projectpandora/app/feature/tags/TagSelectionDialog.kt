package com.projectpandora.app.feature.tags

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.projectpandora.app.core.network.long
import com.projectpandora.app.core.network.string

@Composable
internal fun TagSelectionDialog(
    activeTags: List<JsonObject>,
    selectedTags: List<JsonObject>,
    onDismiss: () -> Unit,
    onConfirm: (List<JsonObject>) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var draftSelection by remember { mutableStateOf(selectedTags) }
    val choices = (selectedTags + activeTags).distinctBy { it.long("id") }
    val filteredChoices = choices.filter { it.string("name").contains(search, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择标签") },
        text = {
            Column {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("搜索标签名称") },
                )
                Text("已选 ${draftSelection.size} 个", Modifier.padding(top = 8.dp, bottom = 4.dp))
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    if (filteredChoices.isEmpty()) {
                        Text("没有找到符合条件的标签。", Modifier.padding(vertical = 12.dp))
                    }
                    filteredChoices.forEach { tag ->
                        val checked = draftSelection.any { it.long("id") == tag.long("id") }
                        TextButton(
                            onClick = {
                                draftSelection = if (checked) {
                                    draftSelection.filterNot { it.long("id") == tag.long("id") }
                                } else {
                                    draftSelection + tag
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = checked, onCheckedChange = null)
                                Text(tag.string("name"), Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draftSelection) }) { Text("完成") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
