package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ConflictResolutionChoice
import com.example.model.SyncConflictItem
import com.example.model.SyncMode
import com.example.model.SyncUiState
import com.example.ui.theme.ArchivalSurface
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.ErrorRoseInk
import com.example.ui.theme.ErrorTerracotta
import com.example.ui.theme.ErrorTerracottaSoft
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.IvoryInk
import com.example.ui.theme.MutedInk
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SecondaryInk
import com.example.ui.theme.WarmBrass
import com.example.ui.theme.WarmBrassSoft
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WatchedMoss
import com.example.ui.theme.WatchedMossSoft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConnectivitySyncBanner(
    syncState: SyncUiState,
    onSyncNow: () -> Unit,
    onOpenSyncCenter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shouldShow = !syncState.isOnline ||
        syncState.isSyncing ||
        syncState.conflicts.isNotEmpty() ||
        syncState.authExpired ||
        (syncState.isSignedIn && syncState.pendingCount > 0)

    AnimatedVisibility(visible = shouldShow, modifier = modifier) {
        val (bgColor, borderColor, accentColor, icon, headline, actionLabel) = when {
            syncState.conflicts.isNotEmpty() -> BannerStyle(
                bg = ErrorTerracottaSoft,
                border = ErrorTerracotta,
                accent = ErrorRoseInk,
                icon = Icons.Outlined.WarningAmber,
                headline = "${syncState.conflicts.size} sync conflict(s) need your review",
                actionLabel = "Resolve",
            )
            syncState.authExpired -> BannerStyle(
                bg = ErrorTerracottaSoft,
                border = ErrorTerracotta,
                accent = ErrorRoseInk,
                icon = Icons.Outlined.Key,
                headline = "Cloud session expired · ${syncState.pendingCount} change(s) safe locally",
                actionLabel = "Sign In",
            )
            syncState.isSyncing -> BannerStyle(
                bg = ElevatedSurface,
                border = WarmBrass,
                accent = WarmBrass,
                icon = Icons.Outlined.CloudSync,
                headline = "Synchronising library with Supabase...",
                actionLabel = null,
            )
            !syncState.isOnline -> BannerStyle(
                bg = ArchivalSurface,
                border = WarningAmber,
                accent = WarningAmber,
                icon = Icons.Outlined.CloudOff,
                headline = if (syncState.pendingCount > 0) {
                    "Offline Mode · ${syncState.pendingCount} change(s) queued locally"
                } else {
                    "Offline Mode · Browsing & edits saved on device"
                },
                actionLabel = "Status",
            )
            else -> BannerStyle(
                bg = ArchivalSurface,
                border = WarmBrass.copy(alpha = 0.6f),
                accent = WarmBrass,
                icon = Icons.Outlined.Sync,
                headline = "${syncState.pendingCount} local change(s) ready to sync",
                actionLabel = "Sync",
            )
        }

        Surface(
            color = bgColor,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenSyncCenter)
                .testTag("connectivity_sync_banner"),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (syncState.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = accentColor,
                        )
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.labelMedium,
                        color = IvoryInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (actionLabel != null) {
                        TextButton(
                            onClick = {
                                if (actionLabel == "Sync") onSyncNow() else onOpenSyncCenter()
                            },
                            modifier = Modifier.testTag("banner_action_button"),
                        ) {
                            Text(
                                text = actionLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = accentColor,
                            )
                        }
                    }
                }
                HorizontalDivider(color = borderColor.copy(alpha = 0.6f))
            }
        }
    }
}

private data class BannerStyle(
    val bg: androidx.compose.ui.graphics.Color,
    val border: androidx.compose.ui.graphics.Color,
    val accent: androidx.compose.ui.graphics.Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val headline: String,
    val actionLabel: String?,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupabaseCloudSyncCard(
    syncState: SyncUiState,
    authBusy: Boolean,
    authError: String?,
    authNotice: String?,
    signOutPendingWarning: Int?,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    onClearAuthFeedback: () -> Unit,
    onOpenSignInPage: () -> Unit,
    onOpenSignUpPage: () -> Unit,
    onSyncNow: () -> Unit,
    onRequestSignOut: () -> Unit,
    onDismissSignOutWarning: () -> Unit,
    onSyncThenSignOut: () -> Unit,
    onConfirmSignOutDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var createAccountMode by remember { mutableStateOf(false) }
    var emailInput by remember { mutableStateOf(syncState.signedInEmail.orEmpty()) }
    var passwordInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    if (signOutPendingWarning != null) {
        AlertDialog(
            onDismissRequest = onDismissSignOutWarning,
            containerColor = ElevatedSurface,
            titleContentColor = IvoryInk,
            textContentColor = SecondaryInk,
            title = { Text("Unsynced Local Changes") },
            text = {
                Text(
                    text = "You have $signOutPendingWarning local change(s) queued that have not been uploaded to Supabase yet. Signing out clears private account data from this device to keep accounts isolated.",
                )
            },
            confirmButton = {
                Button(
                    onClick = onSyncThenSignOut,
                    colors = ButtonDefaults.buttonColors(containerColor = WarmBrass, contentColor = ObsidianBg),
                    modifier = Modifier.testTag("dialog_sync_then_signout"),
                ) {
                    Text("Sync First")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = onDismissSignOutWarning,
                        modifier = Modifier.testTag("dialog_cancel_signout"),
                    ) {
                        Text("Cancel", color = SecondaryInk)
                    }
                    TextButton(
                        onClick = onConfirmSignOutDiscard,
                        modifier = Modifier.testTag("dialog_discard_signout"),
                    ) {
                        Text("Discard & Sign Out", color = ErrorRoseInk)
                    }
                }
            },
        )
    }

    Surface(
        color = ArchivalSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(
            1.dp,
            when {
                syncState.conflicts.isNotEmpty() || syncState.authExpired -> ErrorTerracotta
                syncState.isSignedIn -> WarmBrass.copy(alpha = 0.65f)
                else -> HairlineBorder
            },
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("supabase_sync_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header row + Mode Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CLOUD SYNC · SUPABASE",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarmBrass,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Online & Offline Archive Sync",
                        style = MaterialTheme.typography.titleLarge,
                        color = IvoryInk,
                    )
                }
                SyncModePill(mode = syncState.mode)
            }

            // Two-room status summary chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusMetricChip(
                    label = "Pending Queue",
                    value = "${syncState.pendingCount}",
                    accent = if (syncState.pendingCount > 0) WarningAmber else WatchedMoss,
                )
                StatusMetricChip(
                    label = "Conflicts",
                    value = "${syncState.conflicts.size}",
                    accent = if (syncState.conflicts.isNotEmpty()) ErrorRoseInk else SecondaryInk,
                )
                StatusMetricChip(
                    label = "Last Synced",
                    value = formatSyncTimestamp(syncState.lastSyncedAt),
                    accent = SecondaryInk,
                )
            }

            if (syncState.lastSyncSummary.isNotBlank()) {
                Text(
                    text = syncState.lastSyncSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = WatchedMoss,
                )
            }
            if (!syncState.lastError.isNullOrBlank()) {
                Surface(
                    color = ErrorTerracottaSoft,
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, ErrorTerracotta),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = syncState.lastError,
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRoseInk,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }

            HorizontalDivider(color = HairlineBorder)

            when {
                !syncState.isSupabaseConfigured -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Offline-First Local Storage Active",
                            style = MaterialTheme.typography.titleSmall,
                            color = IvoryInk,
                        )
                        Text(
                            text = "All ratings, statuses, reviews, episodes, and collections are saved in Room SQLite and queued for sync. To activate Supabase cloud accounts, add SUPABASE_URL and SUPABASE_PUBLIC_KEY in AI Studio's Secrets panel and run supabase/schema.sql in your Supabase SQL Editor.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryInk,
                        )
                    }
                }

                syncState.isSignedIn -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudDone,
                                contentDescription = null,
                                tint = WatchedMoss,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = syncState.signedInEmail.orEmpty().ifBlank { "Supabase Account" },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = IvoryInk,
                                )
                                Text(
                                    text = "Two-way sync with Row Level Security & idempotent offline queue",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryInk,
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = onSyncNow,
                                enabled = !syncState.isSyncing && syncState.isOnline,
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = WarmBrass,
                                    contentColor = ObsidianBg,
                                ),
                                modifier = Modifier.testTag("supabase_sync_now_button"),
                            ) {
                                if (syncState.isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = ObsidianBg,
                                    )
                                } else {
                                    Icon(
                                        Icons.Outlined.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (syncState.isSyncing) "Syncing..." else "Sync Now")
                            }

                            OutlinedButton(
                                onClick = onRequestSignOut,
                                enabled = !syncState.isSyncing,
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, HairlineBorder),
                                modifier = Modifier.testTag("supabase_sign_out_button"),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.Logout,
                                    contentDescription = null,
                                    tint = SecondaryInk,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sign Out", color = IvoryInk)
                            }
                        }
                    }
                }

                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Sign in to sync your personal archive across devices. Any edits made offline stay in your local queue and upload automatically once signed in.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryInk,
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !createAccountMode,
                                onClick = {
                                    createAccountMode = false
                                    onClearAuthFeedback()
                                },
                                label = { Text("Sign In") },
                                shape = RoundedCornerShape(4.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = ObsidianBg,
                                    selectedContainerColor = ElevatedSurface,
                                    labelColor = SecondaryInk,
                                    selectedLabelColor = IvoryInk,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = !createAccountMode,
                                    borderColor = HairlineBorder,
                                    selectedBorderColor = WarmBrass,
                                ),
                                modifier = Modifier.testTag("auth_mode_sign_in"),
                            )
                            FilterChip(
                                selected = createAccountMode,
                                onClick = {
                                    createAccountMode = true
                                    onClearAuthFeedback()
                                },
                                label = { Text("Create Account") },
                                shape = RoundedCornerShape(4.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = ObsidianBg,
                                    selectedContainerColor = ElevatedSurface,
                                    labelColor = SecondaryInk,
                                    selectedLabelColor = IvoryInk,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = createAccountMode,
                                    borderColor = HairlineBorder,
                                    selectedBorderColor = WarmBrass,
                                ),
                                modifier = Modifier.testTag("auth_mode_sign_up"),
                            )
                        }

                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it.take(120) },
                            label = { Text("Email") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WarmBrass,
                                unfocusedBorderColor = HairlineBorder,
                                focusedTextColor = IvoryInk,
                                unfocusedTextColor = IvoryInk,
                                focusedLabelColor = WarmBrass,
                                unfocusedLabelColor = SecondaryInk,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("supabase_email_input"),
                        )

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it.take(128) },
                            label = { Text("Password (6+ characters)") },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = if (showPassword) "Hide password" else "Show password",
                                        tint = SecondaryInk,
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WarmBrass,
                                unfocusedBorderColor = HairlineBorder,
                                focusedTextColor = IvoryInk,
                                unfocusedTextColor = IvoryInk,
                                focusedLabelColor = WarmBrass,
                                unfocusedLabelColor = SecondaryInk,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("supabase_password_input"),
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = {
                                    if (createAccountMode) {
                                        onSignUp(emailInput, passwordInput)
                                    } else {
                                        onSignIn(emailInput, passwordInput)
                                    }
                                },
                                enabled = !authBusy && syncState.isOnline,
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = WarmBrass,
                                    contentColor = ObsidianBg,
                                ),
                                modifier = Modifier.testTag("supabase_auth_submit_button"),
                            ) {
                                if (authBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = ObsidianBg,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = when {
                                        !syncState.isOnline -> "Reconnect to Sign In"
                                        createAccountMode -> "Create Supabase Account"
                                        else -> "Sign In & Sync"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }

                            TextButton(
                                onClick = if (createAccountMode) onOpenSignUpPage else onOpenSignInPage,
                                modifier = Modifier.testTag("open_full_auth_page"),
                            ) {
                                Text(
                                    text = if (createAccountMode) "Open Sign-Up Page" else "Open Sign-In Page",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = WarmBrass,
                                )
                            }
                        }

                        if (!authError.isNullOrBlank()) {
                            Text(
                                text = authError,
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRoseInk,
                                modifier = Modifier.testTag("profile_auth_inline_error"),
                            )
                        }

                        if (!authNotice.isNullOrBlank()) {
                            Text(
                                text = authNotice,
                                style = MaterialTheme.typography.bodySmall,
                                color = WatchedMoss,
                                modifier = Modifier.testTag("profile_auth_inline_notice"),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncModePill(mode: SyncMode) {
    val (bg, border, dot, text) = when (mode) {
        SyncMode.ONLINE -> listOf(WatchedMossSoft, WatchedMoss, WatchedMoss, IvoryInk)
        SyncMode.OFFLINE -> listOf(ElevatedSurface, WarningAmber, WarningAmber, WarningAmber)
        SyncMode.SYNCING -> listOf(WarmBrassSoft, WarmBrass, WarmBrass, WarmBrass)
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(dot, CircleShape),
            )
            Text(
                text = mode.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = text,
            )
        }
    }
}

@Composable
private fun StatusMetricChip(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
) {
    Surface(
        color = ObsidianBg,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, HairlineBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MutedInk)
            Text(value, style = MaterialTheme.typography.labelMedium, color = accent)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncConflictsCard(
    conflicts: List<SyncConflictItem>,
    onResolveConflict: (Long, ConflictResolutionChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = ArchivalSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, ErrorTerracotta),
        modifier = modifier
            .fillMaxWidth()
            .testTag("sync_conflicts_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = ErrorRoseInk,
                    modifier = Modifier.size(20.dp),
                )
                Column {
                    Text(
                        text = "CONFLICT RESOLUTION REQUIRED",
                        style = MaterialTheme.typography.labelSmall,
                        color = ErrorRoseInk,
                    )
                    Text(
                        text = "Protected Edits Across Devices",
                        style = MaterialTheme.typography.titleMedium,
                        color = IvoryInk,
                    )
                }
            }

            Text(
                text = "MUVANTA never silently overwrites conflicting notes, reviews, or ratings edited on multiple devices. Choose which version to keep:",
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryInk,
            )

            conflicts.forEach { conflict ->
                Surface(
                    color = ObsidianBg,
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "${conflict.title} · ${conflict.fieldDisplayLabel}",
                            style = MaterialTheme.typography.titleSmall,
                            color = WarmBrass,
                        )
                        Surface(
                            color = ArchivalSurface,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, HairlineBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("THIS DEVICE", style = MaterialTheme.typography.labelSmall, color = WarmBrass)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = conflict.localValue.ifBlank { "(Empty)" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = IvoryInk,
                                )
                            }
                        }
                        Surface(
                            color = ArchivalSurface,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, HairlineBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("SUPABASE CLOUD", style = MaterialTheme.typography.labelSmall, color = WatchedMoss)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = conflict.cloudValue.ifBlank { "(Empty)" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = IvoryInk,
                                )
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { onResolveConflict(conflict.id, ConflictResolutionChoice.KEEP_LOCAL) },
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = WarmBrass, contentColor = ObsidianBg),
                                modifier = Modifier.testTag("conflict_keep_local_${conflict.id}"),
                            ) {
                                Text("Keep Mine")
                            }
                            OutlinedButton(
                                onClick = { onResolveConflict(conflict.id, ConflictResolutionChoice.KEEP_CLOUD) },
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, WatchedMoss),
                                modifier = Modifier.testTag("conflict_keep_cloud_${conflict.id}"),
                            ) {
                                Text("Keep Cloud", color = IvoryInk)
                            }
                            if (conflict.supportsMergeBoth) {
                                OutlinedButton(
                                    onClick = { onResolveConflict(conflict.id, ConflictResolutionChoice.MERGE_BOTH) },
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, HairlineBorder),
                                    modifier = Modifier.testTag("conflict_merge_both_${conflict.id}"),
                                ) {
                                    Text("Combine Both", color = WarmBrass)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OfflineArtworkCacheCard(
    cachedPosterCount: Int,
    onClearArtworkCache: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = ArchivalSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = null,
                    tint = WarmBrass,
                    modifier = Modifier.size(22.dp),
                )
                Column {
                    Text(
                        text = "Offline Poster Cache ($cachedPosterCount cached)",
                        style = MaterialTheme.typography.titleSmall,
                        color = IvoryInk,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Frees cached artwork without ever deleting your ratings, notes, or sync queue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryInk,
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            OutlinedButton(
                onClick = onClearArtworkCache,
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.testTag("clear_artwork_cache_button"),
            ) {
                Text("Clear Cache", color = IvoryInk)
            }
        }
    }
}

private fun formatSyncTimestamp(epochMs: Long): String {
    if (epochMs <= 0L) return "Not yet"
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
}
