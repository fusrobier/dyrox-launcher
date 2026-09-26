package net.dyrox.launcher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.dyrox.launcher.core.accounts.SkinCache
import net.dyrox.launcher.ui.theme.DyroxColors
import net.dyrox.shared.account.AccountStatus
import net.dyrox.shared.account.AccountType
import net.dyrox.shared.account.StoredAccount
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Skin face (with hat layer) for Microsoft accounts; a lettered tile for offline accounts or while loading. */
@Composable
fun AccountAvatar(account: StoredAccount, skins: SkinCache, size: Dp = 40.dp) {
    var face by remember(account.skinUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(account.skinUrl) {
        val url = account.skinUrl ?: return@LaunchedEffect
        face = skins.skinPng(url)?.let { bytes ->
            runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(size * 0.22f)
    val image = face
    if (image != null && image.width >= 64) {
        Canvas(Modifier.size(size).clip(shape)) {
            val destination = IntSize(this.size.width.toInt(), this.size.height.toInt())
            // Face at (8,8) and hat overlay at (40,8), each 8×8 in the 64×64 skin; nearest-neighbour keeps pixels crisp.
            drawImage(image, IntOffset(8, 8), IntSize(8, 8), dstSize = destination, filterQuality = FilterQuality.None)
            drawImage(image, IntOffset(40, 8), IntSize(8, 8), dstSize = destination, filterQuality = FilterQuality.None)
        }
    } else {
        val tint = avatarColors[(account.uuid.hashCode() and Int.MAX_VALUE) % avatarColors.size]
        Box(Modifier.size(size).clip(shape).background(tint.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Text(account.username.take(1).uppercase(), color = tint, fontWeight = FontWeight.Black, fontSize = (size.value * 0.45f).sp)
        }
    }
}

private val avatarColors = listOf(DyroxColors.Accent, DyroxColors.Info, DyroxColors.Warning, Color(0xFFC084FC), Color(0xFFF472B6))

@Composable
fun AccountTypeTag(type: AccountType) {
    Tag(type.label, if (type == AccountType.MICROSOFT) DyroxColors.Accent else DyroxColors.TextSecondary)
}

/** The token-validity indicator: coloured dot plus a short label. */
@Composable
fun AccountStatusIndicator(status: AccountStatus, checking: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (checking) {
            CircularProgressIndicator(Modifier.size(10.dp), color = DyroxColors.Accent, strokeWidth = 1.5.dp)
            Text("Checking…", color = DyroxColors.TextSecondary, fontSize = 12.sp)
            return@Row
        }
        val (color, label) = when (status) {
            AccountStatus.Offline -> DyroxColors.TextMuted to "Offline · singleplayer & offline-mode servers only"
            is AccountStatus.Valid -> DyroxColors.Accent to "Signed in · token valid until ${TIME.format(Instant.ofEpochMilli(status.expiresAt))}"
            AccountStatus.RefreshNeeded -> DyroxColors.Warning to "Token expired · refreshes automatically on launch"
            AccountStatus.SignInRequired -> DyroxColors.Danger to "Sign-in required"
            is AccountStatus.Error -> DyroxColors.Danger to status.message
        }
        val dot by animateColorAsState(color)
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(label, color = DyroxColors.TextSecondary, fontSize = 12.sp, maxLines = 1)
    }
}

/** "just now", "5 min ago", "3 h ago", "2 days ago", or a date. */
fun relativeTime(epochMillis: Long?, now: Long = System.currentTimeMillis()): String {
    if (epochMillis == null) return "never"
    val minutes = (now - epochMillis) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)} days ago"
        else -> DATE.format(Instant.ofEpochMilli(epochMillis))
    }
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault())
