package com.mediawave.downloader.ui.screens.cookies

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediawave.downloader.data.model.CookieProfile
import com.mediawave.downloader.ui.screens.home.HomeViewModel
import com.mediawave.downloader.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookiesScreen(
    viewModel: HomeViewModel,
    onNavigateBack: () -> Unit,
) {
    val cookies by viewModel.cookies.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }

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
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Cookie Profiles",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Login to download private content",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.size(44.dp),
                containerColor = Accent,
                contentColor = Color.White,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add cookie", modifier = Modifier.size(20.dp))
            }
        }

        // Info card about cookies
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.Info, null, tint = Accent, modifier = Modifier.size(20.dp))
                Text(
                    text = "Cookies let yt-dlp access your accounts to download members-only or private content. Export cookies in Netscape format from your browser.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (cookies.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = "🍪", fontSize = 64.sp)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "No cookie profiles",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Add a profile to download private content",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Cookie Profile")
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(cookies, key = { it.id }) { profile ->
                    CookieItem(
                        profile = profile,
                        onToggle = { enabled -> viewModel.toggleCookie(profile.id, enabled) },
                        onDelete = { viewModel.deleteCookie(profile) },
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (showAddDialog) {
        AddCookieDialog(
            onAdd = { name, url, content ->
                viewModel.addCookie(name, url, content)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun CookieItem(
    profile: CookieProfile,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Status dot
            Surface(
                color = if (profile.isActive) SuccessColor else MaterialTheme.colorScheme.outline,
                shape = androidx.compose.foundation.shape.CircleShape,
                modifier = Modifier.size(10.dp),
            ) {}

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = profile.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (profile.isActive) {
                    Text(
                        text = "● Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = SuccessColor,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // Independent toggle — does NOT affect other profiles
            Switch(
                checked = profile.isActive,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Accent,
                ),
            )

            // Delete
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = ErrorColor,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCookieDialog(
    onAdd: (name: String, url: String, content: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Cookie Profile", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    placeholder = { Text("e.g. YouTube, Instagram") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                    ),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Site URL") },
                    placeholder = { Text("https://youtube.com") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                    ),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Cookie Content (Netscape format)") },
                    placeholder = { Text("# Netscape HTTP Cookie File\n...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                    ),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(name, url, content) },
                enabled = name.isNotBlank() && content.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
    )
}
