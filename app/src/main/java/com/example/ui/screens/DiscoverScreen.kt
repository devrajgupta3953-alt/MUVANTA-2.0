package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.ArchiveTiers
import com.example.model.CatalogItem
import com.example.model.DiscoverCategory
import com.example.model.DiscoverMode
import com.example.model.EnrichedCatalogItem
import com.example.model.MoodCategory
import com.example.model.SortOption
import com.example.model.StatusFilter
import com.example.ui.components.ArchivalPosterCard
import com.example.ui.components.ArchiveOrbView
import com.example.ui.components.EditorialSearchBar
import com.example.ui.components.EmptyArchiveState
import com.example.ui.theme.ArchivalSurface
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.IvoryInk
import com.example.ui.theme.MutedInk
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SecondaryInk
import com.example.ui.theme.ShelfLedgeWalnut
import com.example.ui.theme.WarmBrass
import com.example.ui.viewmodel.DiscoverUiState
import com.example.ui.viewmodel.MuvantaViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiscoverScreen(
    uiState: DiscoverUiState,
    searchHistory: List<String>,
    onSearchChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onClearSearchHistory: () -> Unit,
    onCategoryChange: (DiscoverCategory) -> Unit,
    onToggleMood: (MoodCategory) -> Unit,
    onClearMoods: () -> Unit,
    onGenreChange: (String?) -> Unit,
    onLanguageOrCountryChange: (String?) -> Unit,
    onMinRatingChange: (Int) -> Unit,
    onStatusFilterChange: (StatusFilter) -> Unit,
    onTierChange: (String?) -> Unit,
    onDecadeChange: (Int?) -> Unit,
    onSortChange: (SortOption) -> Unit,
    onModeChange: (DiscoverMode) -> Unit,
    onPageChange: (Int) -> Unit,
    onResetFilters: () -> Unit,
    onPickUnwatched: () -> Unit,
    onOpenTitle: (String) -> Unit,
    onToggleWatched: (String) -> Unit,
    onRequestMetadata: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFilterSheet by remember { mutableStateOf(false) }
    val filters = uiState.filters

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .testTag("discover_screen"),
    ) {
        val gridColumns = when {
            maxWidth >= 1000.dp -> 5
            maxWidth >= 720.dp -> 4
            maxWidth >= 520.dp -> 3
            else -> 2
        }
        val shelfColumns = when {
            maxWidth >= 900.dp -> 5
            maxWidth >= 600.dp -> 3
            else -> 2
        }

        val rows: List<List<EnrichedCatalogItem>> = remember(uiState.currentPageItems, filters.mode, gridColumns, shelfColumns) {
            val cols = if (filters.mode == DiscoverMode.SHELF) shelfColumns else gridColumns
            uiState.currentPageItems.chunked(cols)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Heading + Search + Recent Searches + Mode Switcher
            item(key = "discover_controls") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Text(
                            text = when (filters.category) {
                                DiscoverCategory.ALL -> "FULL CATALOG ARCHIVE"
                                DiscoverCategory.MOVIES -> "MASTER LIST 1 · FILMS"
                                DiscoverCategory.SERIES -> "MASTER LIST 2 · TELEVISION"
                                DiscoverCategory.ANIME -> "CURATED ANIME ARCHIVE"
                                DiscoverCategory.DOCUMENTARIES -> "NONFICTION & DOCUMENTARY"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = WarmBrass,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when (filters.category) {
                                DiscoverCategory.ALL -> "Discover 1,300 Titles"
                                DiscoverCategory.MOVIES -> "Top 1,000 Movies"
                                DiscoverCategory.SERIES -> "Top 300 Television Series"
                                DiscoverCategory.ANIME -> "Essential Anime"
                                DiscoverCategory.DOCUMENTARIES -> "Acclaimed Documentaries"
                            },
                            style = MaterialTheme.typography.headlineMedium,
                            color = IvoryInk,
                        )
                    }

                    EditorialSearchBar(
                        query = filters.searchQuery,
                        onQueryChange = onSearchChange,
                        onSearchSubmit = { onSearchSubmit(filters.searchQuery) },
                        testTag = "discover_search_input",
                    )

                    // Recent Searches Strip (shown when search box is empty & history exists)
                    if (filters.searchQuery.isBlank() && searchHistory.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Outlined.History, contentDescription = null, tint = MutedInk, modifier = Modifier.size(16.dp))
                            LazyRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(searchHistory, key = { it }) { past ->
                                    Surface(
                                        onClick = { onSearchChange(past) },
                                        color = ArchivalSurface,
                                        shape = RoundedCornerShape(3.dp),
                                        border = BorderStroke(1.dp, HairlineBorder),
                                    ) {
                                        Text(
                                            text = past,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SecondaryInk,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        )
                                    }
                                }
                            }
                            TextButton(
                                onClick = onClearSearchHistory,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text("Clear", style = MaterialTheme.typography.labelSmall, color = MutedInk)
                            }
                        }
                    }

                    // Category Filter Strip
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("discover_category_chips"),
                    ) {
                        items(DiscoverCategory.entries) { category ->
                            val selected = filters.category == category
                            FilterChip(
                                selected = selected,
                                onClick = { onCategoryChange(category) },
                                label = {
                                    Text(
                                        text = category.label,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                },
                                shape = RoundedCornerShape(4.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = ArchivalSurface,
                                    labelColor = SecondaryInk,
                                    selectedContainerColor = ElevatedSurface,
                                    selectedLabelColor = IvoryInk,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = HairlineBorder,
                                    selectedBorderColor = WarmBrass,
                                ),
                                modifier = Modifier.testTag("category_chip_${category.name.lowercase()}"),
                            )
                        }
                    }

                    // Mode Switcher (Grid / Shelf / 3D Orb) + Filters + Pick Unwatched
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Surface(
                            color = ArchivalSurface,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, HairlineBorder),
                        ) {
                            Row(
                                modifier = Modifier.padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                ModePillButton(
                                    label = "Grid",
                                    icon = Icons.Outlined.GridView,
                                    selected = filters.mode == DiscoverMode.GRID,
                                    onClick = { onModeChange(DiscoverMode.GRID) },
                                    testTag = "mode_grid_button",
                                )
                                ModePillButton(
                                    label = "Shelf",
                                    icon = Icons.Outlined.AutoStories,
                                    selected = filters.mode == DiscoverMode.SHELF,
                                    onClick = { onModeChange(DiscoverMode.SHELF) },
                                    testTag = "mode_shelf_button",
                                )
                                ModePillButton(
                                    label = "3D Orb",
                                    icon = Icons.Outlined.Public,
                                    selected = filters.mode == DiscoverMode.ORB,
                                    onClick = { onModeChange(DiscoverMode.ORB) },
                                    testTag = "mode_orb_button",
                                )
                            }
                        }

                        Surface(
                            onClick = { showFilterSheet = true },
                            color = ArchivalSurface,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, if (filters.activeFilterCount > 0) WarmBrass else HairlineBorder),
                            modifier = Modifier.testTag("discover_filters_button"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Tune,
                                    contentDescription = null,
                                    tint = SecondaryInk,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = if (filters.activeFilterCount > 0) "Filters (${filters.activeFilterCount})" else "Filters",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = IvoryInk,
                                )
                            }
                        }

                        Surface(
                            onClick = onPickUnwatched,
                            color = ElevatedSurface,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, WarmBrass),
                            modifier = Modifier.testTag("discover_pick_unwatched"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Shuffle,
                                    contentDescription = null,
                                    tint = WarmBrass,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "Pick Unwatched",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = IvoryInk,
                                )
                            }
                        }
                    }

                    // Active Result Summary & Removable Filter Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Showing ${uiState.totalFilteredCount} title${if (uiState.totalFilteredCount == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryInk,
                        )
                        if (filters.hasAnyFilterOrSearch) {
                            TextButton(
                                onClick = onResetFilters,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("reset_filters_button"),
                            ) {
                                Text(
                                    text = "Reset all filters",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = WarmBrass,
                                )
                            }
                        }
                    }

                    if (filters.moods.isNotEmpty() ||
                        filters.genre != null ||
                        filters.languageOrCountry != null ||
                        filters.minRating > 0 ||
                        filters.statusFilter != StatusFilter.ALL ||
                        filters.tierId != null ||
                        filters.decade != null ||
                        filters.sortOption != SortOption.RANK_ASC
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            filters.moods.forEach { m ->
                                RemovableChip("Mood: ${m.label}") { onToggleMood(m) }
                            }
                            filters.genre?.let { g ->
                                RemovableChip("Genre: $g") { onGenreChange(null) }
                            }
                            filters.languageOrCountry?.let { origin ->
                                RemovableChip("Origin: $origin") { onLanguageOrCountryChange(null) }
                            }
                            if (filters.minRating > 0) {
                                RemovableChip("My Rating ≥ ${filters.minRating}/10") { onMinRatingChange(0) }
                            }
                            if (filters.statusFilter != StatusFilter.ALL) {
                                RemovableChip("Status: ${filters.statusFilter.label}") { onStatusFilterChange(StatusFilter.ALL) }
                            }
                            filters.tierId?.let { tid ->
                                val label = ArchiveTiers.findById(tid)?.shortTitle ?: tid
                                RemovableChip(label) { onTierChange(null) }
                            }
                            filters.decade?.let { dec ->
                                RemovableChip(if (dec == 1930) "1930s & Earlier" else "${dec}s") { onDecadeChange(null) }
                            }
                            if (filters.sortOption != SortOption.RANK_ASC) {
                                RemovableChip("Sort: ${filters.sortOption.label}") { onSortChange(SortOption.RANK_ASC) }
                            }
                        }
                    }
                }
            }

            // Empty State
            if (uiState.totalFilteredCount == 0 && !uiState.isLoading) {
                item(key = "empty") {
                    EmptyArchiveState(
                        title = "No matching titles",
                        subtitle = "No entries in the archive match your current search or combined filter criteria.",
                        actionLabel = "Reset all filters",
                        onAction = onResetFilters,
                    )
                }
            } else if (filters.mode == DiscoverMode.ORB) {
                // Interactive 3D Archive Orb Mode
                item(key = "orb_stage") {
                    ArchiveOrbView(
                        items = uiState.currentPageItems,
                        onSelectTitle = onOpenTitle,
                    )
                }
            } else if (filters.mode == DiscoverMode.SHELF) {
                // Archival Shelf Mode
                items(
                    items = rows.withIndex().toList(),
                    key = { "shelf_${it.index}_${it.value.firstOrNull()?.item?.id}" },
                ) { (_, rowItems) ->
                    Column(modifier = Modifier.fillMaxWidth()) {
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
                            repeat(shelfColumns - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .shadow(elevation = 6.dp)
                                .background(ShelfLedgeWalnut),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            } else {
                // Poster Grid Mode
                items(
                    items = rows.withIndex().toList(),
                    key = { "grid_${it.index}_${it.value.firstOrNull()?.item?.id}" },
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
                        repeat(gridColumns - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Pagination Footer
            if (uiState.totalFilteredCount > 0) {
                item(key = "pagination") {
                    Column(modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) {
                        HorizontalDivider(color = HairlineBorder)
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Page ${filters.page} of ${uiState.totalPages}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryInk,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onPageChange(filters.page - 1) },
                                    enabled = filters.page > 1,
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, HairlineBorder),
                                    modifier = Modifier.testTag("pagination_prev"),
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Previous", style = MaterialTheme.typography.labelLarge)
                                }
                                OutlinedButton(
                                    onClick = { onPageChange(filters.page + 1) },
                                    enabled = filters.page < uiState.totalPages,
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, HairlineBorder),
                                    modifier = Modifier.testTag("pagination_next"),
                                ) {
                                    Text("Next", style = MaterialTheme.typography.labelLarge)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState,
            containerColor = ArchivalSurface,
            contentColor = IvoryInk,
        ) {
            AdvancedFilterSheetContent(
                moods = filters.moods,
                genre = filters.genre,
                languageOrCountry = filters.languageOrCountry,
                minRating = filters.minRating,
                statusFilter = filters.statusFilter,
                tierId = filters.tierId,
                decade = filters.decade,
                sortOption = filters.sortOption,
                onToggleMood = onToggleMood,
                onClearMoods = onClearMoods,
                onGenreChange = onGenreChange,
                onLanguageOrCountryChange = onLanguageOrCountryChange,
                onMinRatingChange = onMinRatingChange,
                onStatusChange = onStatusFilterChange,
                onTierChange = onTierChange,
                onDecadeChange = onDecadeChange,
                onSortChange = onSortChange,
                onReset = onResetFilters,
                onDone = { showFilterSheet = false },
            )
        }
    }
}

@Composable
private fun ModePillButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(3.dp),
        color = if (selected) ElevatedSurface else ArchivalSurface,
        border = if (selected) BorderStroke(1.dp, WarmBrass) else null,
        modifier = Modifier.testTag(testTag),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) WarmBrass else SecondaryInk,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) IvoryInk else SecondaryInk,
            )
        }
    }
}

@Composable
private fun RemovableChip(
    label: String,
    onRemove: () -> Unit,
) {
    Surface(
        onClick = onRemove,
        color = ArchivalSurface,
        shape = RoundedCornerShape(3.dp),
        border = BorderStroke(1.dp, HairlineBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = IvoryInk)
            Icon(Icons.Default.Close, contentDescription = "Remove $label", tint = MutedInk, modifier = Modifier.size(14.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedFilterSheetContent(
    moods: Set<MoodCategory>,
    genre: String?,
    languageOrCountry: String?,
    minRating: Int,
    statusFilter: StatusFilter,
    tierId: String?,
    decade: Int?,
    sortOption: SortOption,
    onToggleMood: (MoodCategory) -> Unit,
    onClearMoods: () -> Unit,
    onGenreChange: (String?) -> Unit,
    onLanguageOrCountryChange: (String?) -> Unit,
    onMinRatingChange: (Int) -> Unit,
    onStatusChange: (StatusFilter) -> Unit,
    onTierChange: (String?) -> Unit,
    onDecadeChange: (Int?) -> Unit,
    onSortChange: (SortOption) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    val decades = listOf(2020, 2010, 2000, 1990, 1980, 1970, 1960, 1950, 1940, 1930)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Archive Filters & Sort", style = MaterialTheme.typography.headlineSmall, color = IvoryInk)
            TextButton(onClick = onReset) {
                Text("Reset", color = WarmBrass)
            }
        }

        // 1. Emotion / Mood (Multi-select combination — separate from Genre!)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("EMOTION / MOOD (COMBINE MULTIPLE)", style = MaterialTheme.typography.labelMedium, color = WarmBrass)
                if (moods.isNotEmpty()) {
                    TextButton(onClick = onClearMoods, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                        Text("Clear (${moods.size})", style = MaterialTheme.typography.labelSmall, color = SecondaryInk)
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MoodCategory.entries.forEach { m ->
                    val sel = m in moods
                    ArchiveChip(label = m.label, selected = sel, onClick = { onToggleMood(m) })
                }
            }
        }

        // 2. Genre (Separate from Mood)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("GENRE", style = MaterialTheme.typography.labelMedium, color = WarmBrass)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ArchiveChip(label = "Any Genre", selected = genre == null, onClick = { onGenreChange(null) })
                MuvantaViewModel.FILTER_GENRES.forEach { g ->
                    ArchiveChip(label = g, selected = genre == g, onClick = { onGenreChange(if (genre == g) null else g) })
                }
            }
        }

        // 3. Language & Country
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("LANGUAGE & COUNTRY", style = MaterialTheme.typography.labelMedium, color = WarmBrass)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ArchiveChip(label = "All Regions", selected = languageOrCountry == null, onClick = { onLanguageOrCountryChange(null) })
                MuvantaViewModel.FILTER_ORIGINS.forEach { origin ->
                    ArchiveChip(
                        label = origin,
                        selected = languageOrCountry == origin,
                        onClick = { onLanguageOrCountryChange(if (languageOrCountry == origin) null else origin) },
                    )
                }
            }
        }

        // 4. Viewing Status
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("VIEWING STATUS", style = MaterialTheme.typography.labelMedium, color = WarmBrass)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusFilter.entries.forEach { st ->
                    ArchiveChip(label = st.label, selected = st == statusFilter, onClick = { onStatusChange(st) })
                }
            }
        }

        // 5. Minimum Personal Rating
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("PERSONAL RATING FLOOR", style = MaterialTheme.typography.labelMedium, color = WarmBrass)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 6, 7, 8, 9, 10).forEach { floor ->
                    ArchiveChip(
                        label = if (floor == 0) "Any Rating" else "$floor+/10",
                        selected = minRating == floor,
                        onClick = { onMinRatingChange(floor) },
                    )
                }
            }
        }

        // 6. Release Decade
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("RELEASE DECADE", style = MaterialTheme.typography.labelMedium, color = WarmBrass)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ArchiveChip(label = "All Decades", selected = decade == null, onClick = { onDecadeChange(null) })
                decades.forEach { d ->
                    ArchiveChip(
                        label = if (d == 1930) "1930s & Earlier" else "${d}s",
                        selected = decade == d,
                        onClick = { onDecadeChange(d) },
                    )
                }
            }
        }

        // 7. Sort Order
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SORT ORDER", style = MaterialTheme.typography.labelMedium, color = WarmBrass)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SortOption.entries.forEach { opt ->
                    ArchiveChip(label = opt.label, selected = opt == sortOption, onClick = { onSortChange(opt) })
                }
            }
        }

        // 8. Collection Tier
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("COLLECTION TIER", style = MaterialTheme.typography.labelMedium, color = WarmBrass)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ArchiveChip(label = "All Tiers", selected = tierId == null, onClick = { onTierChange(null) })
                ArchiveTiers.all.forEach { tier ->
                    ArchiveChip(label = tier.shortTitle, selected = tierId == tier.id, onClick = { onTierChange(tier.id) })
                }
            }
        }

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 20.dp),
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WarmBrass, contentColor = ObsidianBg),
        ) {
            Text("Apply Filters", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ArchiveChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
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
