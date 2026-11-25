package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.LocalProfileEntity
import com.example.model.ArchiveStats
import com.example.model.ConflictResolutionChoice
import com.example.model.DiscoverMode
import com.example.model.ImportPreview
import com.example.model.SyncUiState
import com.example.ui.components.EditorialFooter
import com.example.ui.components.OfflineArtworkCacheCard
import com.example.ui.components.SupabaseCloudSyncCard
import com.example.ui.components.SyncConflictsCard
import com.example.ui.theme.ArchivalSurface
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.ErrorRoseInk
import com.example.ui.theme.ErrorTerracotta
import com.example.ui.theme.ErrorTerracottaSoft
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.IvoryInk
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SecondaryInk
import com.example.ui.theme.WarmBrass

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    profile: LocalProfileEntity,
    stats: ArchiveStats,
    syncState: SyncUiState,
    authBusy: Boolean,
    authError: String?,
    authNotice: String?,
    signOutPendingWarning: Int?,
    cachedPosterCount: Int,
    importPreview: ImportPreview?,
    canUndoBackup: Boolean,
    onSaveProfile: (String, String, DiscoverMode) -> Unit,
    onOpenStatistics: () -> Unit,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    onClearAuthFeedback: () -> Unit,
    onOpenSignInPage: () -> Unit,
    onOpenSignUpPage: () -> Unit,
    onSyncNow: () -> Unit,
    onResolveConflict: (Long, ConflictResolutionChoice) -> Unit,
    onRequestSignOut: () -> Unit,
    onDismissSignOutWarning: () -> Unit,
    onSyncThenSignOut: () -> Unit,
    onConfirmSignOutDiscard: () -> Unit,
    onClearArtworkCache: () -> Unit,
    onExportJsonClick: () -> Unit,
    onExportCsvClick: () -> Unit,
    onImportJsonClick: () -> Unit,
    onConfirmImportMerge: () -> Unit,
    onConfirmImportReplace: () -> Unit,
    onCancelImport: () -> Unit,
    onUndoBackupAction: () -> Unit,
    onClearAllProgress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var nameInput by remember { mutableStateOf(profile.displayName) }
    var taglineInput by remember { mutableStateOf(profile.tagline) }
    var preferredMode by remember { mutableStateOf(DiscoverMode.fromWire(profile.defaultDiscoverMode)) }
    var confirmClearPending by remember { mutableStateOf(false) }

    LaunchedEffect(profile) {
        nameInput = profile.displayName
        taglineInput = profile.tagline
        preferredMode = DiscoverMode.fromWire(profile.defaultDiscoverMode)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .testTag("profile_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "profile_header") {
            Column {
                Text(
                    text = "ARCHIVIST PROFILE & DATA",
                    style = MaterialTheme.typography.labelMedium,
                    color = WarmBrass,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Profile & Cloud Sync",
                    style = MaterialTheme.typography.headlineMedium,
                    color = IvoryInk,
                )
            }
        }

        // Supabase Cloud Account & Offline Sync Center
        item(key = "supabase_cloud_sync") {
            SupabaseCloudSyncCard(
                syncState = syncState,
                authBusy = authBusy,
                authError = authError,
                authNotice = authNotice,
                signOutPendingWarning = signOutPendingWarning,
                onSignIn = onSignIn,
                onSignUp = onSignUp,
                onClearAuthFeedback = onClearAuthFeedback,
                onOpenSignInPage = onOpenSignInPage,
                onOpenSignUpPage = onOpenSignUpPage,
                onSyncNow = onSyncNow,
                onRequestSignOut = onRequestSignOut,
                onDismissSignOutWarning = onDismissSignOutWarning,
                onSyncThenSignOut = onSyncThenSignOut,
                onConfirmSignOutDiscard = onConfirmSignOutDiscard,
            )
        }

        if (syncState.conflicts.isNotEmpty()) {
            item(key = "sync_conflicts") {
                SyncConflictsCard(
                    conflicts = syncState.conflicts,
                    onResolveConflict = onResolveConflict,
                )
            }
        }

        // Local Archivist Identity Card
        item(key = "profile_identity") {
            Surface(
                color = ArchivalSurface,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            color = ElevatedSurface,
                            shape = CircleShape,
                            border = BorderStroke(1.5.dp, WarmBrass),
                            modifier = Modifier.size(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = nameInput.trim().take(1).uppercase().ifEmpty { "M" },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = WarmBrass,
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.displayName,
                                style = MaterialTheme.typography.titleLarge,
                                color = IvoryInk,
                            )
                            Text(
                                text = buildString {
                                    append("${stats.totalWatched} watched · ${stats.ratedCount} rated · ")
                                    append(if (syncState.isSignedIn) "Supabase + Room" else "Room + Offline Queue")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryInk,
                            )
                        }
                    }

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it.take(60) },
                        label = { Text("Display Name") },
                        singleLine = true,
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
                            .testTag("profile_name_input"),
                    )

                    OutlinedTextField(
                        value = taglineInput,
                        onValueChange = { taglineInput = it.take(140) },
                        label = { Text("Archivist Bio / Tagline") },
                        maxLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WarmBrass,
                            unfocusedBorderColor = HairlineBorder,
                            focusedTextColor = IvoryInk,
                            unfocusedTextColor = IvoryInk,
                            focusedLabelColor = WarmBrass,
                            unfocusedLabelColor = SecondaryInk,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "DEFAULT DISCOVER VIEW",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryInk,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DiscoverMode.entries.forEach { mode ->
                                val selected = mode == preferredMode
                                FilterChip(
                                    selected = selected,
                                    onClick = { preferredMode = mode },
                                    label = { Text(mode.label, style = MaterialTheme.typography.labelMedium) },
                                    shape = RoundedCornerShape(4.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = ObsidianBg,
                                        selectedContainerColor = ElevatedSurface,
                                        labelColor = SecondaryInk,
                                        selectedLabelColor = IvoryInk,
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selected,
                                        borderColor = HairlineBorder,
                                        selectedBorderColor = WarmBrass,
                                    ),
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { onSaveProfile(nameInput, taglineInput, preferredMode) },
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WarmBrass, contentColor = ObsidianBg),
                        modifier = Modifier.testTag("profile_save_button"),
                    ) {
                        Text("Save Profile Preferences", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // Statistics Hub Entry Card
        item(key = "stats_entry") {
            Surface(
                onClick = onOpenStatistics,
                color = ArchivalSurface,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_open_statistics"),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        color = ElevatedSurface,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, WarmBrass.copy(alpha = 0.6f)),
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.BarChart, contentDescription = null, tint = WarmBrass, modifier = Modifier.size(22.dp))
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Statistics & Viewing Progress", style = MaterialTheme.typography.titleMedium, color = IvoryInk)
                        Text(
                            text = "${stats.totalWatched} / ${stats.totalTitles} completed (${stats.overallPercent}%) across all 10 tiers",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryInk,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = WarmBrass, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Local Data Ownership: Backup & Export
        item(key = "backup_section") {
            Surface(
                color = ArchivalSurface,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "LOCAL DATA OWNERSHIP",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarmBrass,
                    )
                    Text(
                        text = "Backup & Data Management",
                        style = MaterialTheme.typography.titleLarge,
                        color = IvoryInk,
                    )
                    Text(
                        text = "Export your viewing history as JSON (100% compatible with the MUVANTA web prototype backup schema) or CSV, and restore backups with Merge, Replace, and Undo safety.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryInk,
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = onExportJsonClick,
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElevatedSurface, contentColor = IvoryInk),
                            border = BorderStroke(1.dp, WarmBrass),
                            modifier = Modifier.testTag("export_json_button"),
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null, tint = WarmBrass, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export JSON")
                        }
                        OutlinedButton(
                            onClick = onExportCsvClick,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, HairlineBorder),
                            modifier = Modifier.testTag("export_csv_button"),
                        ) {
                            Icon(Icons.Outlined.TableChart, contentDescription = null, tint = SecondaryInk, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export CSV", color = IvoryInk)
                        }
                        OutlinedButton(
                            onClick = onImportJsonClick,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, HairlineBorder),
                            modifier = Modifier.testTag("import_json_button"),
                        ) {
                            Icon(Icons.Outlined.Upload, contentDescription = null, tint = SecondaryInk, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import JSON", color = IvoryInk)
                        }
                        if (canUndoBackup) {
                            OutlinedButton(
                                onClick = onUndoBackupAction,
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, WarmBrass),
                                modifier = Modifier.testTag("undo_backup_button"),
                            ) {
                                Icon(Icons.Outlined.Undo, contentDescription = null, tint = WarmBrass, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Undo Last Change", color = WarmBrass)
                            }
                        }
                    }

                    // Validated Import Preview Box
                    if (importPreview != null) {
                        Surface(
                            color = ObsidianBg,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, WarmBrass),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = buildString {
                                        append("Validated ${importPreview.validCount} record")
                                        if (importPreview.validCount != 1) append("s")
                                        if (importPreview.skippedCount > 0) {
                                            append(" (${importPreview.skippedCount} skipped)")
                                        }
                                        append(". Choose how to apply:")
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = IvoryInk,
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        onClick = onConfirmImportMerge,
                                        shape = RoundedCornerShape(4.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = WarmBrass, contentColor = ObsidianBg),
                                        modifier = Modifier.testTag("import_merge_button"),
                                    ) {
                                        Text("Merge with Existing")
                                    }
                                    OutlinedButton(
                                        onClick = onConfirmImportReplace,
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, HairlineBorder),
                                        modifier = Modifier.testTag("import_replace_button"),
                                    ) {
                                        Text("Replace All", color = IvoryInk)
                                    }
                                    OutlinedButton(
                                        onClick = onCancelImport,
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, HairlineBorder),
                                    ) {
                                        Text("Cancel", color = SecondaryInk)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Reset Local Progress Card
        item(key = "reset_progress") {
            Surface(
                color = ArchivalSurface,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Reset Local Watch Progress", style = MaterialTheme.typography.titleSmall, color = IvoryInk)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Clears all watched marks, ratings, and notes from this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryInk,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (!confirmClearPending) {
                                confirmClearPending = true
                            } else {
                                confirmClearPending = false
                                onClearAllProgress()
                            }
                        },
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ErrorTerracottaSoft,
                            contentColor = ErrorRoseInk,
                        ),
                        border = BorderStroke(1.dp, ErrorTerracotta),
                        modifier = Modifier.testTag("clear_progress_button"),
                    ) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (confirmClearPending) "Confirm Clear" else "Clear All")
                    }
                }
            }
        }

        // Offline Poster Cache Management
        item(key = "artwork_cache_card") {
            OfflineArtworkCacheCard(
                cachedPosterCount = cachedPosterCount,
                onClearArtworkCache = onClearArtworkCache,
            )
        }

        item(key = "profile_footer") {
            EditorialFooter()
        }
    }
}
