package com.projectpandora.app.feature.account

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectpandora.app.R
import com.projectpandora.app.ui.*
import com.projectpandora.app.ui.theme.PandoraColors as C

@Composable
fun LaunchScreen() {
    Column(Modifier.fillMaxSize().background(C.Amber).testTag("launch"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(painterResource(R.drawable.pandora_logo), "潘多拉标志", Modifier.size(241.dp))
        Spacer(Modifier.height(32.dp))
        Image(painterResource(R.drawable.pandora_wordmark), "潘多拉", Modifier.size(289.dp, 96.dp))
    }
}

@Composable
fun WelcomeScreen(onEnter: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pageHeight = maxHeight
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .heightIn(min = pageHeight).padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height((pageHeight * 0.12f).coerceAtLeast(36.dp)))
            Image(painterResource(R.drawable.pandora_logo), "潘多拉标志", Modifier.size(241.dp))
            Spacer(Modifier.height(54.dp))
            Image(painterResource(R.drawable.pandora_wordmark), "潘多拉", Modifier.size(289.dp, 96.dp))
            Spacer(Modifier.height(90.dp))
            PrimaryButton("进入潘多拉", onEnter, Modifier.widthIn(max = 278.dp).height(58.dp))
            Spacer(Modifier.height(30.dp))
            Text("PANDORA · 智能掌上工作系统", fontSize = 12.sp, color = C.Muted)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun LoginScreen(model: AccountViewModel, requiresLocalNetworkPermission: Boolean = false) {
    var account by rememberSaveable { mutableStateOf("") }
    // Password must not enter saved state, preferences, logs or navigation arguments.
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var pendingLogin by remember { mutableStateOf<Pair<String, String>?>(null) }
    var localNetworkPermissionDenied by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val credentials = pendingLogin
        pendingLogin = null
        if (granted) {
            localNetworkPermissionDenied = false
            credentials?.let { (pendingAccount, pendingPassword) -> model.login(pendingAccount, pendingPassword) }
        } else {
            localNetworkPermissionDenied = true
        }
    }
    val submit = {
        focus.clearFocus()
        when {
            model.loading -> Unit
            account.isBlank() || password.isEmpty() -> model.login(account, password)
            requiresLocalNetworkPermission && ContextCompat.checkSelfPermission(
                context,
                LocalNetworkPermission.NAME,
            ) != PackageManager.PERMISSION_GRANTED -> {
                pendingLogin = account to password
                localNetworkPermissionLauncher.launch(LocalNetworkPermission.NAME)
            }
            else -> {
                localNetworkPermissionDenied = false
                model.login(account, password)
            }
        }
    }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BrandHeader()
        Spacer(Modifier.height(24.dp))
        PageTitle("欢迎回来")
        Text("使用管理员为你录入的手机号和密码登录", fontSize = 14.sp, color = C.Muted)
        model.error?.let { Note(it, C.ErrorPale, C.Error) }
        if (localNetworkPermissionDenied) {
            Note("需要允许本地网络访问，才能连接当前服务器。请在系统设置中授权后重试。", C.ErrorPale, C.Error)
        }
        if (model.canRetrySessionValidation) {
            TextButton(
                onClick = model::retrySessionValidation,
                enabled = !model.loading,
                modifier = Modifier.testTag("retry_session_validation"),
            ) {
                Text("重试验证", color = C.Orange)
            }
        }
        OutlinedTextField(account, { account = it; model.clearError(); localNetworkPermissionDenied = false }, Modifier.fillMaxWidth().testTag("account"),
            label = { Text("手机号") }, placeholder = { Text("请输入手机号") }, singleLine = true, enabled = !model.loading,
            isError = model.error == "请输入手机号", shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next))
        OutlinedTextField(password, { password = it; model.clearError(); localNetworkPermissionDenied = false }, Modifier.fillMaxWidth().testTag("password"),
            label = { Text("密码") }, singleLine = true, enabled = !model.loading,
            isError = model.error == "请输入密码", shape = RoundedCornerShape(12.dp),
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }, enabled = !model.loading) {
                    Icon(painterResource(R.drawable.password_eye), if (visible) "隐藏密码" else "显示密码", tint = C.Muted)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }))
        Text("密码可通过右侧显示按钮查看", fontSize = 12.sp, color = C.Muted)
        PrimaryButton(if (model.loading) "正在登录…" else "登录", submit, enabled = !model.loading)
        if (model.loading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("login_loading"), color = C.Orange)
        Text("手机号由管理员统一录入。登录遇到问题？\n请联系本单位管理员。", fontSize = 12.sp, lineHeight = 20.sp, color = C.Muted)
        Spacer(Modifier.height(76.dp))
        Text("让每一项工作，都有清晰的进展。", fontSize = 12.sp, color = C.Muted)
    }
}
