package com.projectpandora.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectpandora.app.R
import com.projectpandora.app.feature.account.BusinessUser
import com.projectpandora.app.ui.*
import com.projectpandora.app.ui.theme.PandoraColors as C

@Composable
fun ProfileScreen(user: BusinessUser, onPersonalInfo: () -> Unit, onNotifications: () -> Unit,
    onHelp: () -> Unit, onLogout: () -> Unit) {
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    PageColumn {
        BrandHeader()
        PageTitle("我的")
        IdentityCard(user)
        PageTitle("个人设置", small = true)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsRow("个人资料", R.drawable.setting_profile, C.Orange, C.AmberPale, onClick = onPersonalInfo)
            SettingsRow("通知设置", R.drawable.setting_notification, C.Blue, C.BluePale, onClick = onNotifications)
            SettingsRow("帮助与反馈", R.drawable.setting_help, C.Violet, C.VioletPale, onClick = onHelp)
            SettingsRow("退出登录", R.drawable.setting_logout, C.Error, C.ErrorPale, warning = true) { confirmLogout = true }
        }
    }
    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false }, containerColor = Color.White,
        title = { Text("退出登录？", color = C.Error, fontWeight = FontWeight.Bold) },
        text = { Text("退出后需要重新输入账号和密码。", color = C.Muted) },
        confirmButton = { Button(onClick = { confirmLogout = false; onLogout() }, modifier = Modifier.testTag("confirm_logout"),
            colors = ButtonDefaults.buttonColors(containerColor = C.Error, contentColor = Color.White)) { Text("退出登录") } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("继续使用", color = C.Ink) } },
    )
}

@Composable
private fun SettingsRow(label: String, icon: Int, accent: Color, pale: Color,
    warning: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
        .background(if (warning) C.ErrorPale else Color.White, RoundedCornerShape(18.dp))
        .border(1.dp, if (warning) C.ErrorBorder else C.Line, RoundedCornerShape(18.dp))
        .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).background(pale, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, Modifier.size(24.dp), tint = accent)
        }
        Text(label, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium,
            color = if (warning) C.Error else C.Ink)
        Text("›", fontSize = 24.sp, color = if (warning) C.Error else C.Muted)
    }
}
