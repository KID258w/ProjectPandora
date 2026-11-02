package com.projectpandora.app.feature.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectpandora.app.ui.Note
import com.projectpandora.app.ui.theme.PandoraColors as C

@Composable
fun ViewsScreen(
    canViewOrganization: Boolean,
    onOpenTaskCenter: () -> Unit,
    onOpenOrganization: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("工作视图", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.Ink)
            OutlinedButton(onClick = onOpenTaskCenter, modifier = Modifier.testTag("task-center-entry")) {
                Text("任务中心")
            }
            if (canViewOrganization) {
                OutlinedButton(onClick = onOpenOrganization, modifier = Modifier.testTag("organization-relationships-entry")) {
                    Text("组织关系")
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("工作视图", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
            Text("正式视图默认进入月视图。", color = C.Muted, fontSize = 13.sp)
            Note("月、周、日工作视图正在完善中。")
        }
        Spacer(Modifier.weight(1f))
    }
}
