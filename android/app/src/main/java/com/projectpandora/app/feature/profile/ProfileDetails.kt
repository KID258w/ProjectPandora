package com.projectpandora.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectpandora.app.feature.account.BusinessUser
import com.projectpandora.app.feature.account.AccountPreferences
import com.projectpandora.app.ui.*
import com.projectpandora.app.ui.theme.PandoraColors as C

@Composable
fun IdentityCard(user: BusinessUser) {
    Row(Modifier.fillMaxWidth().background(C.AmberPale, RoundedCornerShape(24.dp)).padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp).background(Color.White, RoundedCornerShape(32.dp)), contentAlignment = Alignment.Center) {
            Text(user.name.take(1), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(user.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp)
            Text(user.role, fontSize = 14.sp, color = C.Muted)
            Text(user.organization, fontSize = 12.sp, color = C.Muted)
        }
    }
}

@Composable
fun PersonalInfoScreen(user: BusinessUser, onBack: () -> Unit) {
    PageColumn {
        BackButton(onBack)
        PageTitle("个人资料")
        IdentityCard(user)
        val rows = listOf("姓名" to user.name, "业务身份" to user.role, "部门" to (user.department ?: "—"),
            "团队" to (user.team ?: "—"), "直属上级" to (user.supervisor ?: "—"))
        Column(Modifier.fillMaxWidth().border(1.dp, C.Line, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp)) {
            rows.forEachIndexed { index, (label, value) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.width(90.dp), fontSize = 14.sp, color = C.Muted)
                    Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                if (index != rows.lastIndex) HorizontalDivider(color = C.Line)
            }
        }
        Note("组织信息由管理员维护，如需变更请联系管理员。")
    }
}

@Composable
fun NotificationScreen(store: AccountPreferences, onBack: () -> Unit) {
    val settings = listOf(Triple("task", "任务提醒", "新任务分配、截止时间提醒"),
        Triple("approval", "审批提醒", "任务审批结果和待审批事项"),
        Triple("logs", "日志提醒", "每日工作日志填写提醒"))
    PageColumn {
        BackButton(onBack)
        PageTitle("通知设置")
        Text("工作消息提醒", fontSize = 14.sp, color = C.Muted)
        settings.forEach { (key, title, description) ->
            var enabled by remember { mutableStateOf(store.notification(key)) }
            Row(Modifier.fillMaxWidth().heightIn(min = 100.dp).border(1.dp, C.Line, RoundedCornerShape(18.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(description, fontSize = 12.sp, color = C.Muted)
                }
                Switch(enabled, { enabled = it; store.setNotification(key, it) }, Modifier.testTag("notification_$key"),
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = C.Amber,
                        checkedBorderColor = C.Amber))
            }
        }
        Note("系统通知权限需要在 Android 设置中开启。")
    }
}

@Composable
fun HelpScreen(onBack: () -> Unit) {
    PageColumn {
        BackButton(onBack)
        PageTitle("帮助与反馈")
        PageTitle("需要帮助？", small = true)
        Column(Modifier.fillMaxWidth().background(C.AmberPale, RoundedCornerShape(20.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("联系本单位管理员", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("账号登录、组织信息变更和使用问题，请向本单位管理员反馈。", fontSize = 14.sp, lineHeight = 24.sp, color = C.Muted)
        }
        Column(Modifier.fillMaxWidth().border(1.dp, C.Line, RoundedCornerShape(18.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("常见问题", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("无法登录：检查账号、密码及账号状态。\n资料有误：联系管理员更新组织信息。", fontSize = 14.sp, lineHeight = 24.sp, color = C.Muted)
        }
        Text("PANDORA · 智能掌上工作系统", fontSize = 12.sp, color = C.Muted)
    }
}
