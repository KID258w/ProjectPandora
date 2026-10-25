package com.projectpandora.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectpandora.app.R
import com.projectpandora.app.feature.account.BusinessUser
import com.projectpandora.app.feature.account.PasswordChangeRequest
import com.projectpandora.app.feature.account.ProfileUpdateRequest
import com.projectpandora.app.ui.BackButton
import com.projectpandora.app.ui.Note
import com.projectpandora.app.ui.PageColumn
import com.projectpandora.app.ui.PageTitle
import com.projectpandora.app.ui.PrimaryButton
import com.projectpandora.app.ui.theme.PandoraColors as C

@Composable
fun EditPersonalInfoScreen(
    user: BusinessUser,
    saving: Boolean,
    error: String?,
    onBack: () -> Unit,
    onErrorDismiss: () -> Unit,
    onSave: (ProfileUpdateRequest) -> Unit,
) {
    var name by remember(user) { mutableStateOf(user.name) }
    var phone by remember(user) { mutableStateOf(user.account) }
    // The API deliberately returns only a masked ID, so this input starts blank.
    // Do not use rememberSaveable for the full identity number.
    var idNumber by remember(user) { mutableStateOf("") }

    val changedName = name.trim().takeIf { it != user.name }
    val changedPhone = phone.trim().takeIf { it != user.account }
    val changedIdNumber = idNumber.trim().takeIf(String::isNotEmpty)
    val request = ProfileUpdateRequest(
        displayName = changedName,
        username = changedPhone,
        idNumber = changedIdNumber,
    )
    val hasChanges = request.displayName != null || request.username != null || request.idNumber != null

    PageColumn {
        BackButton(onBack)
        PageTitle("编辑个人资料")
        Text("姓名、手机号和身份证号可以分别修改。", fontSize = 14.sp, color = C.Muted)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it; onErrorDismiss() },
            modifier = Modifier.fillMaxWidth().testTag("profile_name"),
            enabled = !saving,
            label = { Text("姓名") },
            singleLine = true,
            isError = error?.contains("姓名") == true,
            shape = RoundedCornerShape(14.dp),
        )
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it; onErrorDismiss() },
            modifier = Modifier.fillMaxWidth().testTag("profile_phone"),
            enabled = !saving,
            label = { Text("手机号") },
            singleLine = true,
            isError = error?.contains("手机号") == true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(14.dp),
        )
        Text("身份证号 · 当前：${user.idNumberMasked ?: "未设置"}", fontSize = 13.sp, color = C.Ink)
        OutlinedTextField(
            value = idNumber,
            onValueChange = { idNumber = it; onErrorDismiss() },
            modifier = Modifier.fillMaxWidth().testTag("profile_id_number"),
            enabled = !saving,
            label = { Text("请输入完整身份证号") },
            singleLine = true,
            isError = error?.contains("身份证") == true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            shape = RoundedCornerShape(14.dp),
        )
        Note(
            "身份证号读取时只显示脱敏值；留空表示不修改。手机号更新成功后，下次登录请使用新号码。",
        )
        error?.let { Note(it, C.ErrorPale, C.Error) }
        PrimaryButton(
            if (saving) "正在保存…" else "保存个人资料",
            onClick = { onSave(request) },
            enabled = !saving && hasChanges,
            modifier = Modifier.testTag("save_personal_info"),
        )
    }
}

@Composable
fun ChangePasswordScreen(
    saving: Boolean,
    error: String?,
    onBack: () -> Unit,
    onErrorDismiss: () -> Unit,
    onChangePassword: (PasswordChangeRequest) -> Unit,
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newPasswordConfirm by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val currentPasswordError = error?.contains("当前密码") == true
    val confirmationError = error?.contains("不一致") == true || error?.contains("确认") == true
    val newPasswordError = error?.contains("新密码") == true && !confirmationError

    PageColumn {
        BackButton(onBack)
        PageTitle("修改密码")
        Text("请输入当前密码以验证身份。", fontSize = 14.sp, color = C.Muted)
        PasswordEntry(
            label = "当前密码",
            value = currentPassword,
            onValueChange = { currentPassword = it; localError = null; onErrorDismiss() },
            enabled = !saving,
            isError = currentPasswordError,
            tag = "current_password",
        )
        PasswordEntry(
            label = "新密码",
            value = newPassword,
            onValueChange = { newPassword = it; localError = null; onErrorDismiss() },
            enabled = !saving,
            isError = newPasswordError,
            tag = "new_password",
        )
        PasswordEntry(
            label = "确认新密码",
            value = newPasswordConfirm,
            onValueChange = { newPasswordConfirm = it; localError = null; onErrorDismiss() },
            enabled = !saving,
            isError = confirmationError,
            tag = "new_password_confirm",
        )
        Note("新密码需与确认密码一致；修改前会校验当前密码。")
        (localError ?: error)?.let { Note(it, C.ErrorPale, C.Error) }
        PrimaryButton(
            if (saving) "正在修改…" else "确认修改",
            onClick = {
                if (currentPassword.isBlank() || newPassword.isBlank() || newPasswordConfirm.isBlank()) {
                    localError = "请填写当前密码、新密码和确认密码。"
                } else {
                    onChangePassword(
                        PasswordChangeRequest(currentPassword, newPassword, newPasswordConfirm),
                    )
                }
            },
            enabled = !saving,
            modifier = Modifier.testTag("submit_password_change"),
        )
    }
}

@Composable
private fun PasswordEntry(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    isError: Boolean,
    tag: String,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        enabled = enabled,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }, enabled = enabled) {
                Icon(
                    painter = painterResource(R.drawable.password_eye),
                    contentDescription = if (visible) "隐藏$label" else "显示$label",
                    tint = C.Muted,
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
fun ProfileSavedScreen(user: BusinessUser, onReturn: () -> Unit) {
    SuccessScreen(
        title = "资料已保存",
        description = "个人资料更新成功。手机号修改后，下次登录请使用新号码。",
        onReturn = onReturn,
        action = "返回个人资料",
    ) {
        Column(
            Modifier.fillMaxWidth().border(1.dp, C.Line, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp),
        ) {
            listOf("姓名" to user.name, "手机号" to user.account).forEachIndexed { index, (label, value) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.width(90.dp), fontSize = 14.sp, color = C.Muted)
                    Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                if (index == 0) HorizontalDivider(color = C.Line)
            }
        }
    }
}

@Composable
private fun SuccessScreen(
    title: String,
    description: String,
    onReturn: () -> Unit,
    action: String,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    PageColumn {
        BackButton(onReturn)
        PageTitle(title)
        Spacer(Modifier.height(80.dp))
        Box(
            Modifier.align(Alignment.CenterHorizontally).size(72.dp)
                .background(Color(0xFF18864B), RoundedCornerShape(36.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("✓", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            title,
            Modifier.fillMaxWidth(),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = C.Ink,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            description,
            Modifier.fillMaxWidth(),
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = C.Muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        content()
        Spacer(Modifier.height(32.dp))
        PrimaryButton(action, onReturn, Modifier.testTag("self_service_return"))
    }
}
