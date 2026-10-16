package com.projectpandora.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectpandora.app.feature.account.BusinessUser
import com.projectpandora.app.ui.*
import com.projectpandora.app.ui.theme.PandoraColors as C
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(user: BusinessUser, onViews: () -> Unit, onLogs: () -> Unit) {
    val date = SimpleDateFormat("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE).format(Date())
    PageColumn {
        BrandHeader()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PageTitle("你好，${user.name}", small = true)
            Text("${user.organization}  $date", fontSize = 12.sp, color = C.Muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard("公司重要事项", listOf("产品阶段评审", "客户服务升级", "系统稳定性提升"), C.AmberPale, C.Orange, Modifier.weight(1f), onViews)
            SummaryCard("公司十大重要任务", listOf("产品阶段评审", "客户需求复盘", "季度目标拆解"), C.BluePale, C.Blue, Modifier.weight(1f), onViews)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard("个人重点工作", listOf("完成需求评审", "整理测试计划", "确认项目风险"), C.TealPale, C.Teal, Modifier.weight(1f), onViews)
            SummaryCard("最近日志", listOf("完成产品需求评审", "整理项目进展", "同步团队工作"), C.VioletPale, C.Violet, Modifier.weight(1f), onLogs, "填写今日日志 ›")
        }
        Note("今天也把重要的工作，稳稳向前推进。")
    }
}

@Composable
private fun SummaryCard(title: String, rows: List<String>, pale: Color, accent: Color,
    modifier: Modifier, onClick: () -> Unit, action: String = "查看全部 ›") {
    Column(modifier.heightIn(min = 200.dp).background(pale, RoundedCornerShape(20.dp))
        .border(1.dp, accent.copy(alpha = 0.13f), RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = accent)
        rows.forEach { Text("• $it", fontSize = 12.sp, lineHeight = 18.sp, color = C.Ink) }
        Spacer(Modifier.weight(1f))
        Text(action, fontSize = 12.sp, color = accent)
    }
}
