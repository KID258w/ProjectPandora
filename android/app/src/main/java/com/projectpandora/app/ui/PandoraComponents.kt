package com.projectpandora.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectpandora.app.R
import com.projectpandora.app.navigation.MainTab
import com.projectpandora.app.ui.theme.PandoraColors as C

@Composable
fun BrandHeader() {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(R.drawable.pandora_logo), null, Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)))
        Image(painterResource(R.drawable.pandora_wordmark), "潘多拉", Modifier.size(144.5.dp, 48.dp))
    }
}

@Composable
fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

@Composable
fun PageTitle(text: String, small: Boolean = false) {
    Text(text, fontSize = if (small) 24.sp else 32.sp, fontWeight = FontWeight.Bold,
        lineHeight = if (small) 36.sp else 48.sp)
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick, modifier.fillMaxWidth().height(52.dp), enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = C.Amber, contentColor = C.Ink)) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Note(text: String, background: Color = C.AmberPale, color: Color = C.Muted) {
    Text(text, Modifier.fillMaxWidth().background(background, RoundedCornerShape(16.dp)).padding(16.dp),
        color = color, fontSize = 12.sp, lineHeight = 20.sp)
}

@Composable
fun BackButton(onBack: () -> Unit) {
    TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(40.dp)) {
        Text("‹ 返回", color = C.Muted, fontSize = 14.sp)
    }
}

@Composable
fun PandoraBottomBar(selectedTab: MainTab, onSelect: (MainTab) -> Unit) {
    val icons = listOf(R.drawable.nav_home, R.drawable.nav_views, R.drawable.nav_logs,
        R.drawable.nav_ai, R.drawable.nav_profile)
    Row(Modifier.fillMaxWidth().border(0.5.dp, C.Line).background(Color.White).height(80.dp)) {
        MainTab.entries.forEachIndexed { index, tab ->
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val selected = selectedTab == tab
            Column(Modifier.weight(1f).fillMaxHeight().semantics { this.selected = selected }
                .clickable(interactionSource = interaction, indication = null, role = Role.Tab) { onSelect(tab) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                val shape = RoundedCornerShape(14.dp)
                Box(Modifier.size(56.dp, 36.dp).clip(shape)
                    .background(if (pressed) C.Amber else if (selected) C.AmberPale else Color.Transparent)
                    .border(if (pressed) 2.dp else 1.dp,
                        if (pressed) C.Pressed else if (selected) C.Orange else Color.Transparent, shape),
                    contentAlignment = Alignment.Center) {
                    Icon(painterResource(icons[index]), null, Modifier.size(24.dp),
                        tint = if (pressed) C.Pressed else if (selected) C.Selected else C.Orange)
                }
                Spacer(Modifier.height(4.dp))
                Text(tab.label, fontSize = 12.sp, color = if (selected) C.Selected else C.Muted,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
            }
        }
    }
}
