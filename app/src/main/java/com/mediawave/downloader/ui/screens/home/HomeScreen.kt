package com.mediawave.downloader.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediawave.downloader.R
import com.mediawave.downloader.data.model.ActiveDownload
import com.mediawave.downloader.data.model.CookieProfile
import com.mediawave.downloader.data.model.DownloadQuality
import com.mediawave.downloader.ui.theme.*
import com.mediawave.downloader.data.repository.DownloadRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onSettingsClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeDownloads by viewModel.activeDownloads.collectAsState()
    val cookies by viewModel.cookies.collectAsState(initial = emptyList())
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
            viewModel.dismissSnackbar()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow_alpha",
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
        ) {
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .offset((-50).dp, (-50).dp)
                    .alpha(glowAlpha * 0.15f)
                    .background(
                        brush = Brush.radialGradient(colors = listOf(Accent, Color.Transparent)),
                        shape = CircleShape,
                    ),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                HomeTopBar(
                    ytdlpVersion = uiState.ytdlpVersion,
                    onSettingsClick = onSettingsClick,
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Spacer(Modifier.height(8.dp))

                    YtdlpStatusBadge(version = uiState.ytdlpVersion)

                    UrlInputCard(
                        url = uiState.urlInput,
                        onUrlChange = viewModel::onUrlChange,
                        onPaste = viewModel::pasteFromClipboard,
                        onClear = viewModel::clearUrl,
                        onDone = {
                            keyboardController?.hide()
                            viewModel.quickDownload()
                        },
                    )

                    // Cookie status badge — shows which profile matches the URL
                    CookieStatusBadge(
                        url = uiState.urlInput,
                        cookies = cookies,
                    )

                    DownloadButton(
                        isDownloading = false, // Always allow clicking if URL is entered
                        onClick = {
                            keyboardController?.hide()
                            viewModel.quickDownload()
                        },
                    )

                    if (activeDownloads.isNotEmpty()) {
                        activeDownloads.values.forEach { download ->
                            ActiveDownloadCard(
                                download = download,
                                onCancel = { viewModel.cancelDownload(download.id) },
                            )
                        }
                    }

                    InfoRow()
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (uiState.showQualityDialog) {
        QualityPickerDialog(
            onQualitySelected = viewModel::startDownload,
            onDismiss = viewModel::dismissQualityDialog,
        )
    }
}

@Composable
private fun HomeTopBar(ytdlpVersion: String, onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "MediaWave",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "TikTok · Instagram · YouTube · 1800+",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.settings),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun YtdlpStatusBadge(version: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "⚡", fontSize = 14.sp)
            Text(
                text = stringResource(R.string.ytdlp_active),
                style = MaterialTheme.typography.bodySmall,
                color = Accent,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = version,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Checks whether any saved cookie profile matches the entered URL.
 * Matching logic: the cookie's stored site URL domain must appear in the download URL.
 * Active profiles are preferred over inactive ones.
 * The badge is only shown when the URL field is non-empty.
 */
@Composable
private fun CookieStatusBadge(
    url: String,
    cookies: List<CookieProfile>,
) {
    if (url.isBlank()) return

    val matchedCookie = remember(url, cookies) {
        // Use root domain (last two labels) so subdomains like "vt.tiktok.com"
        // match a profile stored as "www.tiktok.com" or "tiktok.com".
        fun rootDomain(raw: String) = DownloadRepository.rootDomainOf(raw)

        // Prefer an active profile that matches, fall back to any matching profile
        cookies.firstOrNull { it.isActive && rootDomain(it.url).let { r -> r.isNotEmpty() && url.contains(r, ignoreCase = true) } }
            ?: cookies.firstOrNull { rootDomain(it.url).let { r -> r.isNotEmpty() && url.contains(r, ignoreCase = true) } }
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Surface(
            color = if (matchedCookie != null)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (matchedCookie != null) Icons.Outlined.Cookie else Icons.Outlined.NoEncryption,
                    contentDescription = null,
                    tint = if (matchedCookie != null) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                if (matchedCookie != null) {
                    Column {
                        Text(
                            text = stringResource(R.string.cookie_active_for_url, matchedCookie.name),
                            style = MaterialTheme.typography.labelMedium,
                            color = Accent,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = matchedCookie.url,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.cookie_none_for_url),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UrlInputCard(
    url: String,
    onUrlChange: (String) -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(20.dp)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                placeholder = {
                    Text(
                        stringResource(R.string.paste_url),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { onDone() }),
                singleLine = true,
                trailingIcon = {
                    if (url.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPaste,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.paste).uppercase(), style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.clear).uppercase(), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun DownloadButton(isDownloading: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = true, // Always enabled
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Accent,
            contentColor = Color.White,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
    ) {
        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.download).uppercase(), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
private fun ActiveDownloadCard(
    download: ActiveDownload,
    onCancel: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = download.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${(download.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = Accent,
                )
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cancel),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            LinearProgressIndicator(
                progress = { download.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (download.speed.isNotBlank()) {
                    Text(
                        text = "🚀 ${download.speed}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (download.eta.isNotBlank()) {
                    Text(
                        text = "⏱ ${download.eta}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = download.quality.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentLight,
                )
            }
        }
    }
}

@Composable
private fun InfoRow() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.files_saved_to),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "🎬 ${stringResource(R.string.video_folder)}\n🎵 ${stringResource(R.string.audio_folder)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun QualityPickerDialog(
    onQualitySelected: (DownloadQuality) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.select_quality), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DownloadQuality.entries.forEach { quality ->
                    QualityOption(quality = quality, onClick = { onQualitySelected(quality) })
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
    )
}

@Composable
private fun QualityOption(quality: DownloadQuality, onClick: () -> Unit) {
    val icon = when (quality) {
        DownloadQuality.HD -> Icons.Outlined.Hd
        DownloadQuality.SD -> Icons.Outlined.Sd
        DownloadQuality.AUDIO_ONLY -> Icons.Outlined.AudioFile
    }
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(24.dp))
            Text(
                text = when (quality) {
                    DownloadQuality.HD -> stringResource(R.string.quality_hd)
                    DownloadQuality.SD -> stringResource(R.string.quality_sd)
                    DownloadQuality.AUDIO_ONLY -> stringResource(R.string.quality_audio)
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
