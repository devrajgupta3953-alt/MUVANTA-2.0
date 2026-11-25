package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.remote.MetadataService
import com.example.data.repository.CatalogRepository
import com.example.model.CatalogItem
import com.example.model.CustomCollectionWithItems
import com.example.model.EnrichedCatalogItem
import com.example.model.MediaType
import com.example.model.SeriesEpisode
import com.example.model.ViewingLogEntry
import com.example.model.WatchStatus
import com.example.ui.components.ArchivalPosterCard
import com.example.ui.components.ArchivalPosterFrame
import com.example.ui.components.SeriesEpisodeTrackerCard
import com.example.ui.theme.ArchivalSurface
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.ErrorRoseInk
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.IvoryInk
import com.example.ui.theme.MutedInk
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SecondaryInk
import com.example.ui.theme.WarmBrass
import com.example.ui.theme.WarmBrassSoft
import com.example.ui.theme.WatchedMoss
import com.example.ui.theme.WatchedMossSoft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TitleDetailScreen(
    enriched: EnrichedCatalogItem?,
    similarTitles: List<EnrichedCatalogItem>,
    customCollections: List<CustomCollectionWithItems>,
    viewingHistory: List<ViewingLogEntry>,
    seriesEpisodes: List<SeriesEpisode>,
    onBack: () -> Unit,
    onOpenTitle: (String) -> Unit,
    onToggleWatched: (String) -> Unit,
    onSetStatus: (WatchStatus) -> Unit,
    onToggleFavorite: () -> Unit,
    onLogRewatch: () -> Unit,
    onDeleteViewingLog: (Long) -> Unit,
    onSetRating: (Int) -> Unit,
    onSetReview: (String) -> Unit,
    onSetNotes: (String) -> Unit,
    onSetTags: (String) -> Unit,
    onSetCustomPoster: (String) -> Unit,
    onEnsureMetadata: (CatalogItem) -> Unit,
    onForceRefreshMetadata: (CatalogItem) -> Unit,
    onEnsureEpisodes: (CatalogItem, Boolean) -> Unit,
    onToggleEpisode: (Int, Int, Boolean) -> Unit,
    onSetSeasonWatched: (Int, List<SeriesEpisode>, Boolean) -> Unit,
    onToggleInCollection: (Long, String) -> Unit,
    onCreateCollection: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (enriched == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ObsidianBg),
            contentAlignment = Alignment.Center,
        ) {
            Text("Title not found in archive.", style = MaterialTheme.typography.bodyMedium, color = SecondaryInk)
        }
        return
    }

    val context = LocalContext.current
    val item = enriched.item
    val user = enriched.user
    val meta = enriched.meta

    LaunchedEffect(item.id) {
        onEnsureMetadata(item)
        if (item.type == MediaType.SERIES) {
            onEnsureEpisodes(item, false)
        }
    }

    var reviewDraft by remember(item.id) { mutableStateOf(user.reviewText) }
    var notesDraft by remember(item.id) { mutableStateOf(user.notes) }
    var tagsDraft by remember(item.id) { mutableStateOf(user.tags) }
    var customPosterDraft by remember(item.id) { mutableStateOf(user.customPosterUrl) }
    var showMetadataOptions by remember { mutableStateOf(false) }
    var showCollectionSheet by remember { mutableStateOf(false) }

    LaunchedEffect(user.reviewText) { if (reviewDraft != user.reviewText) reviewDraft = user.reviewText }
    LaunchedEffect(user.notes) { if (notesDraft != user.notes) notesDraft = user.notes }
    LaunchedEffect(user.tags) { if (tagsDraft != user.tags) tagsDraft = user.tags }
    LaunchedEffect(user.customPosterUrl) { if (customPosterDraft != user.customPosterUrl) customPosterDraft = user.customPosterUrl }

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.US) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .testTag("title_detail_screen"),
    ) {
        val isWide = maxWidth >= 700.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // Editorial Top Breadcrumb Bar with Favourite Toggle
            item(key = "detail_topbar") {
                Surface(
                    color = ArchivalSurface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("detail_back_button"),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = IvoryInk)
                            }
                            Text(
                                text = "#${item.rank}",
                                style = MaterialTheme.typography.labelLarge,
                                color = WarmBrass,
                            )
                            Text("  /  ", style = MaterialTheme.typography.labelSmall, color = HairlineBorder)
                            Text(
                                text = item.type.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = IvoryInk,
                            )
                            Text("  /  ", style = MaterialTheme.typography.labelSmall, color = HairlineBorder)
                            Text(
                                text = item.tier.shortTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = onToggleFavorite,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("detail_favorite_button"),
                            ) {
                                Icon(
                                    imageVector = if (user.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = if (user.isFavorite) "Remove from favourites" else "Add to favourites",
                                    tint = if (user.isFavorite) WarmBrass else SecondaryInk,
                                )
                            }
                        }
                        HorizontalDivider(color = HairlineBorder)
                    }
                }
            }

            // Main Hero & Editorial Metadata Section
            item(key = "detail_hero") {
                if (isWide) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        ArchivalPosterFrame(
                            enriched = enriched,
                            modifier = Modifier.width(240.dp),
                            showQuickWatchToggle = false,
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            DetailMetadataBlock(enriched)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            ArchivalPosterFrame(
                                enriched = enriched,
                                modifier = Modifier.width(136.dp),
                                showQuickWatchToggle = false,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = item.tier.fullLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WarmBrass,
                                )
                                Text(
                                    text = "${item.title} (${item.year})",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = IvoryInk,
                                )
                                val subMeta = buildList {
                                    add(item.year.toString())
                                    if (enriched.effectiveDirector.isNotBlank()) add("Directed by ${enriched.effectiveDirector}")
                                    if (enriched.effectiveRuntime.isNotBlank()) add(enriched.effectiveRuntime)
                                    if (meta.network.isNotBlank()) add(meta.network)
                                }.joinToString(" · ")
                                Text(
                                    text = subMeta,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryInk,
                                )
                            }
                        }

                        DetailSynopsisAndTags(enriched)
                    }
                }
            }

            // Dedicated Series, Season & Episode Tracker (Section 6)
            if (item.type == MediaType.SERIES) {
                item(key = "series_episodes_section") {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        SeriesEpisodeTrackerCard(
                            episodes = seriesEpisodes,
                            onToggleEpisode = onToggleEpisode,
                            onSetSeasonWatched = onSetSeasonWatched,
                            onRefreshEpisodes = { onEnsureEpisodes(item, true) },
                        )
                    }
                }
            }

            // Personal Watch Status, Rating, Rewatch, Collections, Review, Notes & Tags
            item(key = "detail_tracking") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    HorizontalDivider(color = HairlineBorder)

                    // Status Selector (All 7 states including Rewatching)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "PERSONAL WATCH STATUS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedInk,
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            WatchStatus.entries.forEach { st ->
                                val active = user.status == st
                                val borderColor = when {
                                    active && st == WatchStatus.WATCHED -> WatchedMoss
                                    active && (st == WatchStatus.WATCHING || st == WatchStatus.REWATCHING) -> WarmBrass
                                    active -> SecondaryInk
                                    else -> HairlineBorder
                                }
                                val bgColor = when {
                                    active && st == WatchStatus.WATCHED -> WatchedMossSoft
                                    active && (st == WatchStatus.WATCHING || st == WatchStatus.REWATCHING) -> WarmBrassSoft
                                    active -> ElevatedSurface
                                    else -> ArchivalSurface
                                }
                                Surface(
                                    onClick = { onSetStatus(st) },
                                    shape = RoundedCornerShape(4.dp),
                                    color = bgColor,
                                    border = BorderStroke(1.dp, borderColor),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("detail_status_${st.wire}"),
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 14.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = st.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (active) IvoryInk else SecondaryInk,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Quick Action Bar: Favourite + Log Rewatch + Custom Collections
                    val memberCount = customCollections.count { item.id in it.itemIds }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedButton(
                            onClick = onToggleFavorite,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, if (user.isFavorite) WarmBrass else HairlineBorder),
                            modifier = Modifier.testTag("detail_favorite_chip"),
                        ) {
                            Icon(
                                imageVector = if (user.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = null,
                                tint = WarmBrass,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (user.isFavorite) "Favourited" else "Favourite", color = IvoryInk)
                        }

                        OutlinedButton(
                            onClick = onLogRewatch,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, if (user.rewatchCount > 0) WarmBrass else HairlineBorder),
                            modifier = Modifier.testTag("detail_log_rewatch"),
                        ) {
                            Icon(Icons.Outlined.Replay, contentDescription = null, tint = WarmBrass, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (user.rewatchCount > 0) "Log Rewatch (${user.rewatchCount}×)" else "Log Rewatch",
                                color = IvoryInk,
                            )
                        }

                        OutlinedButton(
                            onClick = { showCollectionSheet = true },
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, if (memberCount > 0) WarmBrass else HairlineBorder),
                            modifier = Modifier.testTag("detail_add_to_collection"),
                        ) {
                            Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, tint = WarmBrass, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (memberCount > 0) "Collections ($memberCount)" else "Add to List",
                                color = IvoryInk,
                            )
                        }
                    }

                    // Personal 1-10 Rating (0.5–5.0★ half-star equivalent)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "PERSONAL RATING (0.5–5★ SCALE)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedInk,
                            )
                            Text(
                                text = if (user.rating > 0) "${user.rating} / 10  ·  ${user.rating / 2.0}★" else "Unrated",
                                style = MaterialTheme.typography.labelLarge,
                                color = WarmBrass,
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            (1..10).forEach { score ->
                                val active = score <= user.rating
                                Surface(
                                    onClick = { onSetRating(score) },
                                    shape = RoundedCornerShape(3.dp),
                                    color = if (active) WarmBrass else ObsidianBg,
                                    border = BorderStroke(1.dp, if (active) WarmBrass else HairlineBorder),
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("detail_rating_$score"),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = score.toString(),
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                            ),
                                            color = if (active) ObsidianBg else SecondaryInk,
                                        )
                                    }
                                }
                            }
                            if (user.rating > 0) {
                                Surface(
                                    onClick = { onSetRating(0) },
                                    shape = RoundedCornerShape(3.dp),
                                    color = ArchivalSurface,
                                    border = BorderStroke(1.dp, HairlineBorder),
                                    modifier = Modifier
                                        .height(38.dp)
                                        .testTag("detail_rating_clear"),
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("Clear", style = MaterialTheme.typography.labelMedium, color = SecondaryInk)
                                    }
                                }
                            }
                        }
                    }

                    // Personal Review (separate from private notes — Section 7)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("PERSONAL REVIEW", style = MaterialTheme.typography.labelSmall, color = WarmBrass)
                            Text("${reviewDraft.length} / ${CatalogRepository.MAX_REVIEW_LENGTH}", style = MaterialTheme.typography.labelSmall, color = MutedInk)
                        }
                        OutlinedTextField(
                            value = reviewDraft,
                            onValueChange = { updated ->
                                val clipped = updated.take(CatalogRepository.MAX_REVIEW_LENGTH)
                                reviewDraft = clipped
                                onSetReview(clipped)
                            },
                            placeholder = {
                                Text(
                                    text = "Write your full critique or impression of this title…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedInk,
                                )
                            },
                            minLines = 2,
                            maxLines = 6,
                            shape = RoundedCornerShape(4.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ArchivalSurface,
                                unfocusedContainerColor = ArchivalSurface,
                                focusedBorderColor = WarmBrass,
                                unfocusedBorderColor = HairlineBorder,
                                focusedTextColor = IvoryInk,
                                unfocusedTextColor = IvoryInk,
                                cursorColor = WarmBrass,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("detail_review_input"),
                        )
                    }

                    // Private Notes
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("PRIVATE NOTES", style = MaterialTheme.typography.labelSmall, color = MutedInk)
                            Text("${notesDraft.length} / ${CatalogRepository.MAX_NOTE_LENGTH}", style = MaterialTheme.typography.labelSmall, color = MutedInk)
                        }
                        OutlinedTextField(
                            value = notesDraft,
                            onValueChange = { updated ->
                                val clipped = updated.take(CatalogRepository.MAX_NOTE_LENGTH)
                                notesDraft = clipped
                                onSetNotes(clipped)
                            },
                            placeholder = {
                                Text(
                                    text = "Private viewing context, who you watched with, or timestamps…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedInk,
                                )
                            },
                            minLines = 2,
                            maxLines = 5,
                            shape = RoundedCornerShape(4.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ArchivalSurface,
                                unfocusedContainerColor = ArchivalSurface,
                                focusedBorderColor = WarmBrass,
                                unfocusedBorderColor = HairlineBorder,
                                focusedTextColor = IvoryInk,
                                unfocusedTextColor = IvoryInk,
                                cursorColor = WarmBrass,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("detail_notes_input"),
                        )
                    }

                    // Personal Tags / Labels
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("PERSONAL TAGS (COMMA-SEPARATED)", style = MaterialTheme.typography.labelSmall, color = MutedInk)
                        OutlinedTextField(
                            value = tagsDraft,
                            onValueChange = { updated ->
                                val clipped = updated.take(300)
                                tagsDraft = clipped
                                onSetTags(clipped)
                            },
                            placeholder = {
                                Text(
                                    text = "e.g., Late-night, IMAX, Comfort watch, With friends",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MutedInk,
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(4.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ArchivalSurface,
                                unfocusedContainerColor = ArchivalSurface,
                                focusedBorderColor = WarmBrass,
                                unfocusedBorderColor = HairlineBorder,
                                focusedTextColor = IvoryInk,
                                unfocusedTextColor = IvoryInk,
                                cursorColor = WarmBrass,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("detail_tags_input"),
                        )
                    }

                    // Viewing History & Rewatch Timeline (preserves all past logs)
                    if (viewingHistory.isNotEmpty()) {
                        Surface(
                            color = ArchivalSurface,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, HairlineBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(Icons.Outlined.History, contentDescription = null, tint = WarmBrass, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "VIEWING & REWATCH TIMELINE (${viewingHistory.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = WarmBrass,
                                    )
                                }
                                viewingHistory.forEachIndexed { idx, log ->
                                    if (idx > 0) HorizontalDivider(color = HairlineBorder.copy(alpha = 0.6f))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = buildString {
                                                    append(log.actionLabel)
                                                    if (log.ratingAtLog > 0) append(" · Rated ${log.ratingAtLog}/10")
                                                },
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (log.isRewatch) WarmBrass else IvoryInk,
                                            )
                                            Text(
                                                text = dateFormatter.format(Date(log.loggedAt)),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MutedInk,
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeleteViewingLog(log.id) },
                                            modifier = Modifier.size(34.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.DeleteOutline,
                                                contentDescription = "Delete log entry",
                                                tint = ErrorRoseInk,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Collapsible Metadata & Poster Options
                    HorizontalDivider(color = HairlineBorder)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showMetadataOptions = !showMetadataOptions }
                                .padding(vertical = 4.dp)
                                .testTag("detail_metadata_options_toggle"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "METADATA & POSTER OPTIONS",
                                style = MaterialTheme.typography.labelMedium,
                                color = SecondaryInk,
                            )
                            Icon(
                                imageVector = if (showMetadataOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle metadata options",
                                tint = SecondaryInk,
                            )
                        }

                        AnimatedVisibility(visible = showMetadataOptions) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = { onForceRefreshMetadata(item) },
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, HairlineBorder),
                                        modifier = Modifier.testTag("detail_refresh_metadata"),
                                    ) {
                                        Icon(Icons.Outlined.Refresh, contentDescription = null, tint = WarmBrass, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Refresh Metadata", color = IvoryInk)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val wikiTarget = meta.wikiUrl.takeIf { MetadataService.isValidWikipediaUrl(it) }
                                                ?: MetadataService.defaultWikiSearchUrl(item)
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(wikiTarget)))
                                            }
                                        },
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, HairlineBorder),
                                        modifier = Modifier.testTag("detail_open_wikipedia"),
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = SecondaryInk, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open on Wikipedia", color = IvoryInk)
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Custom Poster URL (HTTPS only)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SecondaryInk,
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        OutlinedTextField(
                                            value = customPosterDraft,
                                            onValueChange = { customPosterDraft = it.take(1000) },
                                            placeholder = { Text("https://example.com/poster.jpg", color = MutedInk) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(4.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = ArchivalSurface,
                                                unfocusedContainerColor = ArchivalSurface,
                                                focusedBorderColor = WarmBrass,
                                                unfocusedBorderColor = HairlineBorder,
                                                focusedTextColor = IvoryInk,
                                                unfocusedTextColor = IvoryInk,
                                            ),
                                            modifier = Modifier.weight(1f),
                                        )
                                        Button(
                                            onClick = { onSetCustomPoster(customPosterDraft) },
                                            shape = RoundedCornerShape(4.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = ElevatedSurface, contentColor = IvoryInk),
                                            border = BorderStroke(1.dp, WarmBrass),
                                            modifier = Modifier.testTag("detail_apply_custom_poster"),
                                        ) {
                                            Text("Apply")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Similar Titles Carousel
            if (similarTitles.isNotEmpty()) {
                item(key = "similar_titles") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HorizontalDivider(color = HairlineBorder, modifier = Modifier.padding(horizontal = 16.dp))
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text("SIMILAR IN THE ARCHIVE", style = MaterialTheme.typography.labelSmall, color = WarmBrass)
                            Text("You Might Also Appreciate", style = MaterialTheme.typography.headlineSmall, color = IvoryInk)
                        }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(similarTitles, key = { it.item.id }) { sim ->
                                ArchivalPosterCard(
                                    enriched = sim,
                                    onClick = { onOpenTitle(sim.item.id) },
                                    onToggleWatched = { onToggleWatched(sim.item.id) },
                                    onRequestMetadata = onEnsureMetadata,
                                    cardWidth = 136.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCollectionSheet) {
        var quickListName by remember { mutableStateOf("") }
        ModalBottomSheet(
            onDismissRequest = { showCollectionSheet = false },
            containerColor = ArchivalSurface,
            contentColor = IvoryInk,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Add to Custom Collections", style = MaterialTheme.typography.headlineSmall, color = IvoryInk)

                if (customCollections.isEmpty()) {
                    Text(
                        text = "You haven't created any custom collections yet. Create one below:",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryInk,
                    )
                } else {
                    customCollections.forEach { col ->
                        val checked = item.id in col.itemIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleInCollection(col.id, item.id) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { onToggleInCollection(col.id, item.id) },
                                colors = CheckboxDefaults.colors(checkedColor = WarmBrass, checkmarkColor = ObsidianBg),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(col.name, style = MaterialTheme.typography.titleSmall, color = IvoryInk)
                                Text("${col.itemIds.size} titles", style = MaterialTheme.typography.bodySmall, color = MutedInk)
                            }
                            if (checked) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = WarmBrass, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                HorizontalDivider(color = HairlineBorder)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = quickListName,
                        onValueChange = { quickListName = it.take(80) },
                        placeholder = { Text("New collection name…", color = MutedInk) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WarmBrass,
                            unfocusedBorderColor = HairlineBorder,
                            focusedTextColor = IvoryInk,
                            unfocusedTextColor = IvoryInk,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            if (quickListName.isNotBlank()) {
                                onCreateCollection(quickListName, "")
                                quickListName = ""
                            }
                        },
                        enabled = quickListName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = WarmBrass, contentColor = ObsidianBg),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text("Create")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailMetadataBlock(enriched: EnrichedCatalogItem) {
    val item = enriched.item
    val meta = enriched.meta
    Text(text = item.tier.fullLabel, style = MaterialTheme.typography.labelMedium, color = WarmBrass)
    Text(text = "${item.title} (${item.year})", style = MaterialTheme.typography.headlineMedium, color = IvoryInk)
    val subMeta = buildList {
        add(item.year.toString())
        if (enriched.effectiveDirector.isNotBlank()) add("Directed by ${enriched.effectiveDirector}")
        if (enriched.effectiveRuntime.isNotBlank()) add(enriched.effectiveRuntime)
        if (meta.network.isNotBlank()) add(meta.network)
    }.joinToString(" · ")
    Text(text = subMeta, style = MaterialTheme.typography.bodyMedium, color = SecondaryInk)
    DetailSynopsisAndTags(enriched)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailSynopsisAndTags(enriched: EnrichedCatalogItem) {
    val synopsis = enriched.effectiveDescription.ifBlank {
        if (enriched.meta.failedAt > 0L) {
            "No synopsis is currently available from external metadata providers."
        } else {
            "Retrieving synopsis from Wikipedia, TVmaze, and iTunes…"
        }
    }
    Text(
        text = synopsis,
        style = MaterialTheme.typography.bodyMedium,
        color = SecondaryInk,
    )

    val tags = buildList {
        enriched.effectiveGenre.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { add(it) }
        if (enriched.effectiveCountry.isNotBlank()) add(enriched.effectiveCountry)
        if (enriched.effectiveLanguage.isNotBlank()) add(enriched.effectiveLanguage)
        if (enriched.item.isAnime && none { it.equals("Anime", ignoreCase = true) }) add("Anime")
        if (enriched.item.isDocumentary && none { it.equals("Documentary", ignoreCase = true) }) add("Documentary")
    }.distinct()

    val moods = enriched.effectiveMoods.take(3)
    if (tags.isNotEmpty() || moods.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tags.forEach { tag ->
                Surface(
                    color = ArchivalSurface,
                    shape = RoundedCornerShape(3.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryInk,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            moods.forEach { mood ->
                Surface(
                    color = ElevatedSurface,
                    shape = RoundedCornerShape(3.dp),
                    border = BorderStroke(1.dp, WarmBrass.copy(alpha = 0.5f)),
                ) {
                    Text(
                        text = mood.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = WarmBrass,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}
