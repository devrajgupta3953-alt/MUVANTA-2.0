package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ArchiveStats
import com.example.model.CatalogItem
import com.example.model.CustomCollectionWithItems
import com.example.model.EnrichedCatalogItem
import com.example.ui.components.ArchivalPosterCard
import com.example.ui.components.EmptyArchiveState
import com.example.ui.theme.ArchivalSurface
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.ErrorRoseInk
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.IvoryInk
import com.example.ui.theme.MutedInk
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SecondaryInk
import com.example.ui.theme.WarmBrass

@Composable
fun CollectionsScreen(
    stats: ArchiveStats,
    customCollections: List<CustomCollectionWithItems>,
    onOpenTier: (String) -> Unit,
    onOpenCustomCollection: (Long) -> Unit,
    onCreateCollection: (String, String) -> Unit,
    onDuplicateCollection: (CustomCollectionWithItems) -> Unit,
    onRenameCollection: (Long, String, String) -> Unit,
    onDeleteCollection: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingCollection by remember { mutableStateOf<CustomCollectionWithItems?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .testTag("collections_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "collections_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CURATED & PERSONAL LISTS",
                        style = MaterialTheme.typography.labelMedium,
                        color = WarmBrass,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Collections",
                        style = MaterialTheme.typography.headlineMedium,
                        color = IvoryInk,
                    )
                }
                Button(
                    onClick = { showCreateDialog = true },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElevatedSurface,
                        contentColor = IvoryInk,
                    ),
                    border = BorderStroke(1.dp, WarmBrass),
                    modifier = Modifier.testTag("create_collection_button"),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = WarmBrass, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New List", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Custom User Collections Section
        item(key = "custom_header") {
            Text(
                text = "MY CUSTOM COLLECTIONS (${customCollections.size})",
                style = MaterialTheme.typography.labelMedium,
                color = SecondaryInk,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (customCollections.isEmpty()) {
            item(key = "custom_empty") {
                Surface(
                    color = ArchivalSurface,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderSpecial,
                            contentDescription = null,
                            tint = WarmBrass,
                            modifier = Modifier.size(28.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Build personal playlists",
                                style = MaterialTheme.typography.titleSmall,
                                color = IvoryInk,
                            )
                            Text(
                                text = "Create lists like \"Late-Night Sci-Fi\" or \"Weekend Watch\" and add titles from any movie or series detail page.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryInk,
                            )
                        }
                    }
                }
            }
        } else {
            items(customCollections, key = { "custom_${it.id}" }) { col ->
                Surface(
                    onClick = { onOpenCustomCollection(col.id) },
                    color = ArchivalSurface,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_collection_${col.id}"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = col.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = IvoryInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (col.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = col.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryInk,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${col.itemIds.size} title${if (col.itemIds.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = WarmBrass,
                            )
                        }
                        IconButton(
                            onClick = { onDuplicateCollection(col) },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Duplicate ${col.name}", tint = SecondaryInk, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { editingCollection = col },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Rename ${col.name}", tint = SecondaryInk, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { onDeleteCollection(col.id) },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete ${col.name}", tint = ErrorRoseInk, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Curated Archive Tiers
        item(key = "tiers_header") {
            Spacer(modifier = Modifier.height(8.dp))
            Column {
                Text(
                    text = "ARCHIVAL CURATION",
                    style = MaterialTheme.typography.labelSmall,
                    color = WarmBrass,
                )
                Text(
                    text = "10 Master Collection Tiers",
                    style = MaterialTheme.typography.headlineSmall,
                    color = IvoryInk,
                )
            }
        }

        items(stats.tierProgress, key = { "tier_${it.tier.id}" }) { tp ->
            Surface(
                onClick = { onOpenTier(tp.tier.id) },
                color = ArchivalSurface,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tier_card_${tp.tier.id}"),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tp.tier.fullLabel,
                                style = MaterialTheme.typography.titleSmall,
                                color = IvoryInk,
                            )
                            Text(
                                text = tp.tier.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryInk,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open ${tp.tier.shortTitle}",
                            tint = WarmBrass,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    LinearProgressIndicator(
                        progress = { tp.fraction.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = WarmBrass,
                        trackColor = ObsidianBg,
                        strokeCap = StrokeCap.Butt,
                    )
                    Text(
                        text = "${tp.watchedCount} / ${tp.tier.totalCount} watched (${tp.percent}%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedInk,
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CollectionEditorDialog(
            dialogTitle = "New Custom Collection",
            initialName = "",
            initialDescription = "",
            confirmLabel = "Create",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, desc ->
                onCreateCollection(name, desc)
                showCreateDialog = false
            },
        )
    }

    editingCollection?.let { target ->
        CollectionEditorDialog(
            dialogTitle = "Edit Collection",
            initialName = target.name,
            initialDescription = target.description,
            confirmLabel = "Save",
            onDismiss = { editingCollection = null },
            onConfirm = { name, desc ->
                onRenameCollection(target.id, name, desc)
                editingCollection = null
            },
        )
    }
}

@Composable
fun CustomCollectionDetailScreen(
    collection: CustomCollectionWithItems?,
    items: List<EnrichedCatalogItem>,
    onBack: () -> Unit,
    onOpenTitle: (String) -> Unit,
    onToggleWatched: (String) -> Unit,
    onRequestMetadata: (CatalogItem) -> Unit,
    onExploreCatalog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .testTag("collection_detail_screen"),
    ) {
        val columns = when {
            maxWidth >= 1000.dp -> 5
            maxWidth >= 720.dp -> 4
            maxWidth >= 520.dp -> 3
            else -> 2
        }
        val rows = remember(items, columns) { items.chunked(columns) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "col_detail_header") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("collection_detail_back"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to collections", tint = IvoryInk)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text("CUSTOM COLLECTION", style = MaterialTheme.typography.labelSmall, color = WarmBrass)
                            Text(
                                text = collection?.name ?: "Collection",
                                style = MaterialTheme.typography.headlineSmall,
                                color = IvoryInk,
                            )
                        }
                    }
                    if (!collection?.description.isNullOrBlank()) {
                        Text(
                            text = collection!!.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryInk,
                        )
                    }
                    Text(
                        text = "${items.size} title${if (items.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedInk,
                    )
                }
            }

            if (items.isEmpty()) {
                item(key = "col_detail_empty") {
                    EmptyArchiveState(
                        title = "No titles in this collection yet",
                        subtitle = "Open any movie or series detail page and tap \"Add to Collection\" to curate this list.",
                        actionLabel = "Browse Discover",
                        onAction = onExploreCatalog,
                    )
                }
            } else {
                items(rows.withIndex().toList(), key = { "cd_row_${it.index}" }) { (_, rowItems) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        rowItems.forEach { enriched ->
                            ArchivalPosterCard(
                                enriched = enriched,
                                onClick = { onOpenTitle(enriched.item.id) },
                                onToggleWatched = { onToggleWatched(enriched.item.id) },
                                onRequestMetadata = onRequestMetadata,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(columns - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionEditorDialog(
    dialogTitle: String,
    initialName: String,
    initialDescription: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ArchivalSurface,
        titleContentColor = IvoryInk,
        textContentColor = SecondaryInk,
        title = { Text(dialogTitle, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text("Collection Name") },
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
                        .testTag("collection_name_input"),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(240) },
                    label = { Text("Description (optional)") },
                    maxLines = 3,
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
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, description) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = WarmBrass, contentColor = ObsidianBg),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.testTag("collection_save_button"),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SecondaryInk)
            }
        },
    )
}
