package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ArchiveStats
import com.example.model.CatalogItem
import com.example.model.EnrichedCatalogItem
import com.example.model.LibraryFilter
import com.example.model.LibrarySortOption
import com.example.ui.components.ArchivalPosterCard
import com.example.ui.components.ArchivalPosterFrame
import com.example.ui.components.CompactProgressBanner
import com.example.ui.components.EditorialSearchBar
import com.example.ui.components.EmptyArchiveState
import com.example.ui.theme.ArchivalSurface
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.IvoryInk
import com.example.ui.theme.MutedInk
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SecondaryInk
import com.example.ui.theme.WarmBrass
import com.example.ui.theme.WatchedMoss

@Composable
fun LibraryScreen(
    items: List<EnrichedCatalogItem>,
    activeFilter: LibraryFilter,
    searchQuery: String,
    sortOption: LibrarySortOption,
    isListMode: Boolean,
    stats: ArchiveStats,
    onFilterChange: (LibraryFilter) -> Unit,
    onSearchChange: (String) -> Unit,
    onSortChange: (LibrarySortOption) -> Unit,
    onToggleViewMode: () -> Unit,
    onOpenTitle: (String) -> Unit,
    onToggleWatched: (String) -> Unit,
    onRequestMetadata: (CatalogItem) -> Unit,
    onOpenStatistics: () -> Unit,
    onExploreCatalog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .testTag("library_screen"),
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
            item(key = "library_header") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PERSONAL VIEWING LOG",
                                style = MaterialTheme.typography.labelMedium,
                                color = WarmBrass,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "My Library",
                                style = MaterialTheme.typography.headlineMedium,
                                color = IvoryInk,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Track statuses, favourites, ratings, rewatches, and reviews.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryInk,
                            )
                        }
                        Surface(
                            color = ArchivalSurface,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, HairlineBorder),
                        ) {
                            IconButton(
                                onClick = onToggleViewMode,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("library_view_toggle"),
                            ) {
                                Icon(
                                    imageVector = if (isListMode) Icons.Outlined.GridView else Icons.AutoMirrored.Outlined.ViewList,
                                    contentDescription = if (isListMode) "Switch to Grid View" else "Switch to List View",
                                    tint = WarmBrass,
                                )
                            }
                        }
                    }

                    CompactProgressBanner(
                        stats = stats,
                        onOpenStats = onOpenStatistics,
                    )

                    EditorialSearchBar(
                        query = searchQuery,
                        onQueryChange = onSearchChange,
                        testTag = "library_search_input",
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("library_filter_chips"),
                    ) {
                        items(LibraryFilter.entries) { filter ->
                            val selected = filter == activeFilter
                            FilterChip(
                                selected = selected,
                                onClick = { onFilterChange(filter) },
                                label = { Text(filter.label, style = MaterialTheme.typography.labelLarge) },
                                shape = RoundedCornerShape(4.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = ArchivalSurface,
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
                                modifier = Modifier.testTag("library_chip_${filter.name.lowercase()}"),
                            )
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        item {
                            Text("Sort:", style = MaterialTheme.typography.labelSmall, color = MutedInk)
                        }
                        items(LibrarySortOption.entries) { opt ->
                            val sel = opt == sortOption
                            Surface(
                                onClick = { onSortChange(opt) },
                                color = if (sel) ElevatedSurface else ArchivalSurface,
                                shape = RoundedCornerShape(3.dp),
                                border = BorderStroke(1.dp, if (sel) WarmBrass else HairlineBorder),
                            ) {
                                Text(
                                    text = opt.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (sel) IvoryInk else SecondaryInk,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }

                    Text(
                        text = "${items.size} title${if (items.size == 1) "" else "s"} in ${activeFilter.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryInk,
                    )
                }
            }

            if (items.isEmpty()) {
                item(key = "library_empty") {
                    EmptyArchiveState(
                        title = "Your log is quiet here",
                        subtitle = "Mark titles as Watching, Watchlist, Watched, or Favourite—or add ratings and reviews—to build your archival library.",
                        actionLabel = "Explore Discover",
                        onAction = onExploreCatalog,
                    )
                }
            } else if (isListMode) {
                items(items, key = { "lib_list_${it.item.id}" }) { enriched ->
                    LaunchedEffect(enriched.item.id) { onRequestMetadata(enriched.item) }
                    LibraryListRow(
                        enriched = enriched,
                        onClick = { onOpenTitle(enriched.item.id) },
                    )
                }
            } else {
                items(
                    items = rows.withIndex().toList(),
                    key = { "lib_row_${it.index}_${it.value.firstOrNull()?.item?.id}" },
                ) { (_, rowItems) ->
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
private fun LibraryListRow(
    enriched: EnrichedCatalogItem,
    onClick: () -> Unit,
) {
    val u = enriched.user
    Surface(
        color = ArchivalSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArchivalPosterFrame(
                enriched = enriched,
                modifier = Modifier.width(62.dp),
                showQuickWatchToggle = false,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "#${enriched.item.rank} · ${enriched.item.year}",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarmBrass,
                    )
                    if (u.rating > 0) {
                        Text(
                            text = "${u.rating}/10 (${u.rating / 2.0}★)",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarmBrass,
                        )
                    }
                }
                Text(
                    text = enriched.item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = IvoryInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val summaryMeta = buildList {
                    add(u.status.label)
                    if (u.isFavorite) add("Favourite ★")
                    if (u.rewatchCount > 0) add("${u.rewatchCount}× rewatched")
                    if (u.tagList.isNotEmpty()) add(u.tagList.take(2).joinToString(", "))
                }.joinToString(" · ")
                Text(
                    text = summaryMeta,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (u.status.wire == "watched") WatchedMoss else SecondaryInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val snippet = u.reviewText.ifBlank { u.notes }
                if (snippet.isNotBlank()) {
                    Text(
                        text = snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
