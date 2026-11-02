package com.projectpandora.app.feature.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectpandora.app.feature.account.AccountMessages
import com.projectpandora.app.feature.account.AccountRepository
import com.projectpandora.app.feature.account.BusinessUser
import com.projectpandora.app.ui.theme.PandoraColors as C
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ProfileAvatar(
    user: BusinessUser,
    repository: AccountRepository?,
    editable: Boolean,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var avatarBytes by remember(user.account) { mutableStateOf<ByteArray?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var isLoading by remember(repository, user.account) { mutableStateOf(repository != null) }
    var error by remember { mutableStateOf<String?>(null) }
    val canEdit = editable && repository != null

    LaunchedEffect(repository, user.account) {
        val accountRepository = repository ?: return@LaunchedEffect
        isLoading = true
        try {
            avatarBytes = accountRepository.loadProfileAvatar()
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            avatarBytes = null
            error = "头像加载失败，请稍后重试。"
        } finally {
            isLoading = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val accountRepository = repository
        if (uri != null && accountRepository != null) {
            scope.launch {
                isSaving = true
                error = null
                try {
                    val image = withContext(Dispatchers.IO) { prepareAvatar(context, uri) }
                    accountRepository.updateProfileAvatar(image, "image/jpeg")
                    avatarBytes = accountRepository.loadProfileAvatar() ?: image
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Exception) {
                    error = AccountMessages.PROFILE_AVATAR_FAILED
                } finally {
                    isSaving = false
                }
            }
        }
    }
    val avatar by produceState<ImageBitmap?>(initialValue = null, key1 = avatarBytes) {
        value = withContext(Dispatchers.IO) { avatarBytes?.let(::decodeAvatar) }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .then(if (canEdit) Modifier.clickable(enabled = !isSaving) { picker.launch("image/*") } else Modifier)
                .semantics { if (canEdit) contentDescription = "更换头像" }
                .testTag("profile_avatar"),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                if (avatar != null) {
                    Image(avatar!!, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                } else {
                    Text(user.name.take(1), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                }
                if (isSaving || isLoading) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = C.Blue, strokeWidth = 2.dp)
                }
            }
            if (canEdit && !isSaving) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(20.dp).border(1.dp, Color.White, CircleShape)
                        .background(C.Blue, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("＋", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (canEdit) Text("点击头像更换", fontSize = 11.sp, color = C.Muted)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 10.sp) }
    }
}

private fun prepareAvatar(context: Context, uri: Uri): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = context.contentResolver.openInputStream(uri) ?: throw IOException("Unable to read selected image")
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Selected file is not a supported image")

    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 1024) sampleSize *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw IOException("Unable to decode selected image")
    return bitmap.useBitmap { image ->
        val output = ByteArrayOutputStream()
        if (!image.compress(Bitmap.CompressFormat.JPEG, 85, output)) throw IOException("Unable to encode selected image")
        val bytes = output.toByteArray()
        if (bytes.size > MAX_AVATAR_BYTES) throw IOException("Compressed avatar exceeds maximum size")
        bytes
    }
}

private inline fun <T> Bitmap.useBitmap(block: (Bitmap) -> T): T = try {
    block(this)
} finally {
    recycle()
}

private fun decodeAvatar(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 512) sampleSize *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}

@Composable
internal fun ReadOnlyProfileAvatar(name: String, avatarBytes: ByteArray?, size: androidx.compose.ui.unit.Dp = 84.dp) {
    val avatar by produceState<ImageBitmap?>(initialValue = null, key1 = avatarBytes) {
        value = withContext(Dispatchers.IO) { avatarBytes?.let(::decodeAvatar) }
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(C.AmberPale).border(1.dp, C.Line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (avatar != null) {
            Image(avatar!!, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        } else {
            Text(name.take(1), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = C.Ink)
        }
    }
}

private const val MAX_AVATAR_BYTES = 5 * 1024 * 1024
