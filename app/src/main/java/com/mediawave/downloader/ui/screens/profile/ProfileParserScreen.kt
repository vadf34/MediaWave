package com.mediawave.downloader.ui.screens.profile

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import com.mediawave.downloader.R
import com.mediawave.downloader.data.model.DownloadQuality
import com.mediawave.downloader.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileParserScreen(
    viewModel: ProfileParserViewModel,
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showQualityDialog by remember { mutableStateOf(false) }
    var showAddSiteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.dismissSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.profile_parser_title),
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = TextSecondary)
                    }
                },
                actions = {
                    if (uiState.parserState == ProfileParserState.DONE) {
                        IconButton(onClick = viewModel::clearInput) {
                            Icon(Icons.Default.Refresh, null, tint = TextSecondary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            // ── Cookie status badge ───────────────────────────────────────────
            if (uiState.cookieChecked) {
                CookieStatusBadge(cookieName = uiState.activeCookieName)
                Spacer(Modifier.height(8.dp))
            }

            // ── Input card ────────────────────────────────────────────────────
            ProfileInputCard(
                input = uiState.inputText,
                onInputChange = viewModel::onInputChange,
                onParse = {
                    keyboard?.hide()
                    viewModel.parseProfile()
                },
                onClear = viewModel::clearInput,
                isLoading = uiState.parserState == ProfileParserState.PARSING,
                enabled = uiState.parserState != ProfileParserState.PARSING && !uiState.isDownloading,
            )

            Spacer(Modifier.height(12.dp))

            // ── States ────────────────────────────────────────────────────────
            when (uiState.parserState) {
                ProfileParserState.IDLE -> ProfileHelpCard()

                ProfileParserState.PARSING -> ParsingIndicator(platform = uiState.platformDetected)

                ProfileParserState.ERROR -> ErrorCard(message = uiState.errorMessage ?: "Помилка")

                ProfileParserState.DONE -> {
                    ProfileInfoHeader(
                        platform = uiState.platformDetected,
                        username = uiState.usernameDetected,
                        count = uiState.items.size,
                    )
                    Spacer(Modifier.height(8.dp))
                    MediaFilterTabs(filter = uiState.mediaTypeFilter, onFilterChange = viewModel::setFilter)
                    Spacer(Modifier.height(8.dp))
                    val filtered = uiState.items.filter { item ->
                        when (uiState.mediaTypeFilter) {
                            MediaTypeFilter.VIDEO -> item.isVideo
                            MediaTypeFilter.PHOTO -> !item.isVideo
                            MediaTypeFilter.ALL   -> true
                        }
                    }
                    val allSelected = filtered.isNotEmpty() && filtered.all { it.isSelected }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${filtered.count { it.isSelected }} вибрано з ${filtered.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        TextButton(
                            onClick = if (allSelected) viewModel::deselectAll else viewModel::selectAll,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                if (allSelected) stringResource(R.string.profile_deselect_all)
                                else stringResource(R.string.profile_select_all),
                                color = Accent,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        items(filtered, key = { it.id }) { item ->
                            MediaItemRow(item = item, onToggle = { viewModel.toggleItem(item.id) })
                        }
                    }
                    val selectedCount = uiState.items.count { it.isSelected }
                    AnimatedVisibility(
                        visible = selectedCount > 0 && !uiState.isDownloading,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) {
                        DownloadBottomBar(selectedCount = selectedCount, onDownload = { showQualityDialog = true })
                    }
                    AnimatedVisibility(visible = uiState.isDownloading) {
                        DownloadProgressBar(done = uiState.downloadedCount, total = uiState.totalToDownload)
                    }
                }
            }
        }
    }

    // ── Platform picker dialog ────────────────────────────────────────────────
    if (uiState.showPlatformPicker) {
        PlatformPickerDialog(
            platforms = viewModel.allPlatforms,
            onSelect = viewModel::onPlatformSelected,
            onAddSite = { showAddSiteDialog = true },
            onDismiss = viewModel::dismissPlatformPicker,
        )
    }

    // ── Add custom site dialog ────────────────────────────────────────────────
    if (showAddSiteDialog) {
        AddCustomSiteDialog(
            onConfirm = { name, domain ->
                viewModel.addCustomPlatform(name, domain)
                showAddSiteDialog = false
            },
            onDismiss = { showAddSiteDialog = false },
        )
    }

    if (showQualityDialog) {
        ProfileQualityDialog(
            onDismiss = { showQualityDialog = false },
            onSelect = { quality ->
                showQualityDialog = false
                viewModel.downloadSelected(quality)
            },
        )
    }
}

// ─── Cookie status badge ───────────────────────────────────────────────────────

@Composable
private fun CookieStatusBadge(cookieName: String?) {
    val isActive = cookieName != null
    val bgColor = if (isActive) SuccessColor.copy(alpha = 0.08f) else DarkSurface.copy(alpha = 0.5f)
    val borderColor = if (isActive) SuccessColor.copy(alpha = 0.3f) else DarkOutline
    val icon = if (isActive) Icons.Default.Security else Icons.Outlined.Lock
    val label = if (isActive) "🍪 Кука: $cookieName" else "Кука не використовується"
    val tintColor = if (isActive) SuccessColor else TextTertiary

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, null, tint = tintColor, modifier = Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = tintColor)
        }
    }
}


// ─── Platform picker ──────────────────────────────────────────────────────────

@Composable
private fun PlatformPickerDialog(
    platforms: List<SocialPlatform>,
    onSelect: (SocialPlatform) -> Unit,
    onAddSite: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DarkSurface,
            border = BorderStroke(1.dp, DarkOutline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Оберіть платформу",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
                Text(
                    "Введений нікнейм буде використано для пошуку",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )
                Spacer(Modifier.height(16.dp))

                // Grid of platforms — 2 columns
                val rows = platforms.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            row.forEach { platform ->
                                PlatformChip(
                                    platform = platform,
                                    onClick = { onSelect(platform) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            // Fill empty cell if odd number
                            if (row.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = DarkOutline)
                Spacer(Modifier.height(12.dp))

                // Add custom site button
                OutlinedButton(
                    onClick = onAddSite,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Accent.copy(alpha = 0.5f)),
                ) {
                    Icon(Icons.Default.Add, null, tint = Accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Додати сайт", color = Accent, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun PlatformChip(
    platform: SocialPlatform,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DarkSurfaceVariant,
        border = BorderStroke(1.dp, DarkOutline),
        onClick = onClick,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(platform.iconEmoji, fontSize = 18.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    platform.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    platform.domain,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ─── Add custom site dialog ───────────────────────────────────────────────────

@Composable
private fun AddCustomSiteDialog(
    onConfirm: (name: String, domain: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text("Додати сайт", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Назва сайту", color = TextTertiary) },
                    placeholder = { Text("напр. Dailymotion", color = TextTertiary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = DarkOutline,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Accent,
                    ),
                    shape = RoundedCornerShape(10.dp),
                )
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("Домен", color = TextTertiary) },
                    placeholder = { Text("dailymotion.com", color = TextTertiary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = DarkOutline,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Accent,
                    ),
                    shape = RoundedCornerShape(10.dp),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), domain.trim()) },
                enabled = name.isNotBlank() && domain.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = TextSecondary)
            }
        },
    )
}

// ─── Existing composables (unchanged) ─────────────────────────────────────────

@Composable
private fun ProfileInputCard(
    input: String,
    onInputChange: (String) -> Unit,
    onParse: () -> Unit,
    onClear: () -> Unit,
    isLoading: Boolean,
    enabled: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, DarkOutline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.profile_input_label),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "нікнейм, @нікнейм або посилання",
                        color = TextTertiary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onParse() }),
                trailingIcon = {
                    if (input.isNotBlank()) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Default.Close, null, tint = TextTertiary)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = DarkOutline,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Accent,
                ),
                shape = RoundedCornerShape(12.dp),
            )
            Button(
                onClick = onParse,
                enabled = enabled && input.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (isLoading) stringResource(R.string.profile_parsing)
                    else stringResource(R.string.profile_parse_btn),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ProfileHelpCard() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DarkSurface.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, DarkOutline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ManageAccounts, null, tint = Accent, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.profile_help_title), fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }
            listOf(
                "instagram.com/username",
                "tiktok.com/@username",
                "youtube.com/@channel",
                "@username  → оберіть соцмережу",
                "нікнейм   → оберіть соцмережу",
            ).forEach { example ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(4.dp).background(Accent, CircleShape))
                    Text(example, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.profile_help_desc),
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
            )
        }
    }
}

@Composable
private fun ParsingIndicator(platform: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = Accent, modifier = Modifier.size(48.dp))
            Text(
                stringResource(R.string.profile_parsing_desc, platform.ifBlank { "акаунт" }),
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ErrorColor.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, ErrorColor.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.ErrorOutline, null, tint = ErrorColor, modifier = Modifier.size(20.dp))
            Text(message, color = ErrorColor, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ProfileInfoHeader(platform: String, username: String, count: Int) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Accent.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(40.dp).background(Accent.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.AccountCircle, null, tint = Accent, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("@$username", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(platform, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Surface(shape = RoundedCornerShape(20.dp), color = Accent.copy(alpha = 0.15f)) {
                Text(
                    "$count медіа",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun MediaFilterTabs(filter: MediaTypeFilter, onFilterChange: (MediaTypeFilter) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(DarkSurface, RoundedCornerShape(12.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MediaTypeFilter.values().forEach { tab ->
            val selected = filter == tab
            val label = when (tab) {
                MediaTypeFilter.ALL   -> stringResource(R.string.profile_filter_all)
                MediaTypeFilter.VIDEO -> stringResource(R.string.profile_filter_video)
                MediaTypeFilter.PHOTO -> stringResource(R.string.profile_filter_photo)
            }
            val icon = when (tab) {
                MediaTypeFilter.ALL   -> Icons.Outlined.GridView
                MediaTypeFilter.VIDEO -> Icons.Outlined.PlayCircle
                MediaTypeFilter.PHOTO -> Icons.Outlined.Image
            }
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                color = if (selected) Accent else Color.Transparent,
                onClick = { onFilterChange(tab) },
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, modifier = Modifier.size(16.dp), tint = if (selected) Color.White else TextSecondary)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Color.White else TextSecondary,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaItemRow(item: ProfileMediaItem, onToggle: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (item.isSelected) Accent.copy(alpha = 0.06f) else DarkSurface,
        border = BorderStroke(1.dp, if (item.isSelected) Accent else DarkOutline),
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Checkbox(
                checked = item.isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = Accent, uncheckedColor = TextTertiary, checkmarkColor = Color.White),
                modifier = Modifier.size(20.dp),
            )
            Box(
                modifier = Modifier.size(52.dp).background(DarkSurfaceVariant, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (item.isVideo) Icons.Outlined.PlayCircle else Icons.Outlined.Image,
                    null,
                    tint = if (item.isVideo) Accent else AccentLight,
                    modifier = Modifier.size(28.dp),
                )
                if (item.duration.isNotBlank()) {
                    Box(
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                    ) {
                        Text(item.duration, style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = Color.White)
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.title.ifBlank { "Без назви" }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = TextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.uploadDate.isNotBlank()) Text(item.uploadDate, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    if (item.viewCount > 0) Text(formatViews(item.viewCount), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                }
            }
            when (item.downloadStatus) {
                ProfileItemDownloadStatus.DONE       -> Icon(Icons.Default.CheckCircle, null, tint = SuccessColor, modifier = Modifier.size(20.dp))
                ProfileItemDownloadStatus.FAILED     -> Icon(Icons.Default.ErrorOutline, null, tint = ErrorColor, modifier = Modifier.size(20.dp))
                ProfileItemDownloadStatus.DOWNLOADING -> CircularProgressIndicator(
                    progress = { item.downloadProgress },
                    modifier = Modifier.size(20.dp),
                    color = Accent, trackColor = DarkOutline, strokeWidth = 2.dp,
                )
                ProfileItemDownloadStatus.IDLE -> {}
            }
        }
    }
}

private fun formatViews(count: Long): String = when {
    count >= 1_000_000 -> "%.1fM перегл.".format(count / 1_000_000.0)
    count >= 1_000     -> "%.0fK перегл.".format(count / 1_000.0)
    else               -> "$count перегл."
}

@Composable
private fun DownloadBottomBar(selectedCount: Int, onDownload: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = DarkSurface,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(stringResource(R.string.profile_download_selected, selectedCount), fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(stringResource(R.string.profile_folder_note), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            Button(onClick = onDownload, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.download))
            }
        }
    }
}

@Composable
private fun DownloadProgressBar(done: Int, total: Int) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), color = DarkSurface) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.profile_downloading_progress), color = TextPrimary, fontWeight = FontWeight.Medium)
                Text("$done / $total", color = Accent, fontWeight = FontWeight.SemiBold)
            }
            LinearProgressIndicator(
                progress = { if (total > 0) done.toFloat() / total else 0f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Accent, trackColor = DarkOutline,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileQualityDialog(onDismiss: () -> Unit, onSelect: (DownloadQuality) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(20.dp),
        title = { Text(stringResource(R.string.select_quality), color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DownloadQuality.values().forEach { quality ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurfaceVariant,
                        border = BorderStroke(1.dp, DarkOutline),
                        onClick = { onSelect(quality) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val (icon, color) = when (quality) {
                                DownloadQuality.HD         -> Icons.Outlined.HighQuality to Accent
                                DownloadQuality.SD         -> Icons.Outlined.Hd to AccentLight
                                DownloadQuality.AUDIO_ONLY -> Icons.Outlined.MusicNote to SuccessColor
                            }
                            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
                            Text(quality.label, color = TextPrimary, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = TextSecondary) }
        },
    )
}
