package com.mediawave.downloader.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediawave.downloader.R
import com.mediawave.downloader.data.repository.UserPreferences
import com.mediawave.downloader.ui.screens.home.HomeViewModel
import com.mediawave.downloader.ui.theme.*

@Composable
fun SettingsScreen(
    viewModel: HomeViewModel,
    onNavigateBack: () -> Unit,
    onCookiesClick: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val themeFlow by viewModel.settings.themeFlow.collectAsState(initial = 0)
    val autoPaste by viewModel.settings.autoPasteFlow.collectAsState(initial = true)
    val askQuality by viewModel.settings.askQualityFlow.collectAsState(initial = true)
    val defaultQuality by viewModel.settings.qualityFlow.collectAsState(initial = "HD")
    val currentLang by viewModel.settings.languageFlow.collectAsState(initial = "system")

    val activity = LocalContext.current as? android.app.Activity
    var showLangDialog by remember { mutableStateOf(false) }

    if (showLangDialog) {
        LanguageDialog(
            currentLang = currentLang,
            onDismiss = { showLangDialog = false },
            onSelect = { code ->
                viewModel.setLanguage(code)
                showLangDialog = false
                activity?.recreate()
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Default Quality Section
            SettingsSection(title = stringResource(R.string.default_quality).uppercase()) {
                val qualities = listOf(
                    "HD"    to stringResource(R.string.quality_hd),
                    "SD"    to stringResource(R.string.quality_sd),
                    "AUDIO" to stringResource(R.string.quality_audio),
                )
                qualities.forEach { (key, label) ->
                    RadioSettingItem(
                        label = label,
                        selected = defaultQuality == key,
                        onClick = { viewModel.setDefaultQuality(key) },
                    )
                }
            }

            // Theme Section
            SettingsSection(title = stringResource(R.string.appearance).uppercase()) {
                val themes = listOf(
                    0 to stringResource(R.string.theme_system),
                    1 to stringResource(R.string.theme_light),
                    2 to stringResource(R.string.theme_dark),
                )
                themes.forEach { (value, label) ->
                    RadioSettingItem(
                        label = label,
                        selected = themeFlow == value,
                        onClick = { viewModel.setTheme(value) },
                    )
                }
            }

            // General Section
            SettingsSection(title = stringResource(R.string.general).uppercase()) {
                SwitchSettingItem(
                    label = stringResource(R.string.auto_paste),
                    description = stringResource(R.string.auto_paste_desc),
                    checked = autoPaste,
                    onCheckedChange = viewModel::setAutoPaste,
                )
                SwitchSettingItem(
                    label = stringResource(R.string.ask_quality),
                    description = stringResource(R.string.ask_quality_desc),
                    checked = askQuality,
                    onCheckedChange = viewModel::setAskQuality,
                )

                // Language Picker
                val langDisplay = UserPreferences.SUPPORTED_LANGUAGES[currentLang]
                    ?: UserPreferences.SUPPORTED_LANGUAGES["system"]!!
                ActionSettingItem(
                    icon = Icons.Outlined.Language,
                    label = stringResource(R.string.language),
                    description = langDisplay,
                    onClick = { showLangDialog = true },
                )
            }

            // Tools Section
            SettingsSection(title = stringResource(R.string.tools).uppercase()) {
                ActionSettingItem(
                    icon = Icons.Outlined.Cookie,
                    label = stringResource(R.string.cookie_profiles),
                    description = stringResource(R.string.cookie_manage_desc),
                    onClick = onCookiesClick,
                )
                ActionSettingItem(
                    icon = Icons.Outlined.Update,
                    label = stringResource(R.string.update_ytdlp),
                    description = stringResource(R.string.ytdlp_current_version, uiState.ytdlpVersion),
                    trailingContent = {
                        if (uiState.isUpdatingYtdlp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    },
                    onClick = { if (!uiState.isUpdatingYtdlp) viewModel.updateYtdlp() },
                )
            }

            // About Section
            SettingsSection(title = stringResource(R.string.about).uppercase()) {
                InfoSettingItem(
                    icon = Icons.Outlined.Info,
                    label = "MediaWave",
                    value = "v1.0.0",
                )
                InfoSettingItem(
                    icon = Icons.Outlined.Language,
                    label = stringResource(R.string.supported_sites),
                    value = "1800+",
                )
                InfoSettingItem(
                    icon = Icons.Outlined.Code,
                    label = stringResource(R.string.ytdlp_version),
                    value = uiState.ytdlpVersion,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LanguageDialog(
    currentLang: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_language)) },
        text = {
            Column {
                UserPreferences.SUPPORTED_LANGUAGES.forEach { (code, name) ->
                    Surface(
                        onClick = { onSelect(code) },
                        color = Color.Transparent,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(
                                selected = currentLang == code,
                                onClick = { onSelect(code) },
                                colors = RadioButtonDefaults.colors(selectedColor = Accent),
                            )
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = Accent,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            letterSpacing = 1.sp,
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun RadioSettingItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = Accent),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SwitchSettingItem(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        color = Color.Transparent,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Accent,
                ),
            )
        }
    }
}

@Composable
private fun ActionSettingItem(
    icon: ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailingContent?.invoke() ?: Icon(
                Icons.Filled.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun InfoSettingItem(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
