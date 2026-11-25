package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.MovieFilter
import androidx.compose.material.icons.outlined.Theaters
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.model.ArchiveStats
import com.example.model.CatalogItem
import com.example.model.EnrichedCatalogItem
import com.example.model.MediaType
import com.example.model.WatchStatus
import com.example.ui.theme.ArchivalSurface
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.IvoryInk
import com.example.ui.theme.MutedInk
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SecondaryInk
import com.example.ui.theme.SprocketDark
import com.example.ui.theme.WarmBrass
import com.example.ui.theme.WatchedMoss
import kotlinx.coroutines.launch

@Composable
fun MuvantaWordmark(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onClick)
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Theaters,
            contentDescription = stringResource(R.string.cd_brand_logo),
            tint = WarmBrass,
            modifier = Modifier.size(24.dp),
        )
        Column {
            Text(
                text = stringResource(R.string.brand_wordmark),
                style = MaterialTheme.typography.titleLarge.copy(
                    letterSpacing = 1.8.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = IvoryInk,
            )
            Text(
                text = stringResource(R.string.brand_tagline).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
                color = MutedInk,
            )
        }
    }
}

@Composable
fun EditorialSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.search_placeholder),
    onSearchSubmit: (() -> Unit)? = null,
    testTag: String = "search_input",
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        singleLine = true,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MutedInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.cd_search),
                tint = MutedInk,
                modifier = Modifier.size(20.dp),
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("${testTag}_clear"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_clear_search),
                        tint = SecondaryInk,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearchSubmit?.invoke() }),
        shape = RoundedCornerShape(4.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = ArchivalSurface,
            unfocusedContainerColor = ArchivalSurface,
            focusedBorderColor = WarmBrass,
            unfocusedBorderColor = HairlineBorder,
            cursorColor = WarmBrass,
            focusedTextColor = IvoryInk,
            unfocusedTextColor = IvoryInk,
        ),
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
fun CompactProgressBanner(
    stats: ArchiveStats,
    onOpenStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("compact_progress_banner"),
        color = ArchivalSurface,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, HairlineBorder),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Viewing Progress",
                    style = MaterialTheme.typography.titleSmall,
                    color = IvoryInk,
                )
                TextButton(
                    onClick = onOpenStats,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.testTag("view_statistics_button"),
                ) {
                    Text(
                        text = stringResource(R.string.action_view_statistics),
                        style = MaterialTheme.typography.labelLarge,
                        color = WarmBrass,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { (stats.overallPercent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = WarmBrass,
                trackColor = ObsidianBg,
                strokeCap = StrokeCap.Butt,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${stats.totalWatched} / ${stats.totalTitles} watched (${stats.overallPercent}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryInk,
                )
                Text(
                    text = "${stats.watchedMovies} films · ${stats.watchedSeries} series",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedInk,
                )
            }
        }
    }
}

@Composable
fun ArchivalPosterFrame(
    enriched: EnrichedCatalogItem,
    modifier: Modifier = Modifier,
    borderColor: Color = if (enriched.user.status == WatchStatus.WATCHED) WatchedMoss.copy(alpha = 0.75f) else HairlineBorder,
    showQuickWatchToggle: Boolean = true,
    onToggleWatched: (() -> Unit)? = null,
) {
    val posterUrl = enriched.effectivePosterUrl
    var imageLoaded by remember(posterUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(4.dp))
            .background(ArchivalSurface)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp)),
    ) {
        // Typographic Archival Plate shown while thumbnail loads or when unavailable
        if (!imageLoaded || posterUrl.isBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(ElevatedSurface, ArchivalSurface, ObsidianBg),
                        ),
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = enriched.item.type.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = WarmBrass.copy(alpha = 0.85f),
                    )
                    Text(
                        text = enriched.item.year.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedInk,
                    )
                }
                Text(
                    text = enriched.item.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 15.sp, lineHeight = 20.sp),
                    color = IvoryInk,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(1.5.dp)
                            .background(WarmBrass.copy(alpha = 0.65f)),
                    )
                    Text(
                        text = "#${enriched.item.rank}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedInk,
                    )
                }
            }
        }

        if (posterUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(posterUrl)
                    .addHeader("User-Agent", com.example.data.remote.MetadataService.CLIENT_USER_AGENT)
                    .crossfade(true)
                    .build(),
                contentDescription = "${enriched.item.title} (${enriched.item.year}) poster",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { imageLoaded = true },
                onError = { imageLoaded = false },
            )
        }

        if (showQuickWatchToggle && onToggleWatched != null) {
            val isWatched = enriched.user.status == WatchStatus.WATCHED
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("toggle_watch_${enriched.item.id}"),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    onClick = onToggleWatched,
                    shape = RoundedCornerShape(3.dp),
                    color = if (isWatched) WatchedMoss else ObsidianBg.copy(alpha = 0.82f),
                    border = BorderStroke(1.dp, if (isWatched) WatchedMoss else HairlineBorder),
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isWatched) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = if (isWatched) {
                                "Unmark ${enriched.item.title} as watched"
                            } else {
                                "Mark ${enriched.item.title} as watched"
                            },
                            tint = if (isWatched) ObsidianBg else SecondaryInk,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArchivalPosterCard(
    enriched: EnrichedCatalogItem,
    onClick: () -> Unit,
    onToggleWatched: () -> Unit,
    onRequestMetadata: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp? = null,
) {
    LaunchedEffect(enriched.item.id) {
        onRequestMetadata(enriched.item)
    }

    val user = enriched.user
    val statusParts = buildList {
        when (user.status) {
            WatchStatus.WATCHED -> add("Watched")
            WatchStatus.WATCHING -> add("Watching")
            WatchStatus.REWATCHING -> add("Rewatching")
            WatchStatus.BOOKMARKED -> add("Watchlist")
            WatchStatus.ON_HOLD -> add("On Hold")
            WatchStatus.DROPPED -> add("Dropped")
            WatchStatus.UNWATCHED -> Unit
        }
        if (user.isFavorite) add("★")
        if (user.rating > 0) add("${user.rating}/10")
    }

    Column(
        modifier = modifier
            .then(if (cardWidth != null) Modifier.width(cardWidth) else Modifier.fillMaxWidth())
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .testTag("poster_card_${enriched.item.id}"),
    ) {
        ArchivalPosterFrame(
            enriched = enriched,
            modifier = Modifier.fillMaxWidth(),
            onToggleWatched = onToggleWatched,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "#${enriched.item.rank}",
                style = MaterialTheme.typography.labelMedium,
                color = WarmBrass,
            )
            Text(
                text = enriched.item.year.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MutedInk,
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = enriched.item.title,
            style = MaterialTheme.typography.titleSmall,
            color = IvoryInk,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = enriched.effectiveGenre,
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (statusParts.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusParts.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = when (user.status) {
                        WatchStatus.WATCHED -> WatchedMoss
                        WatchStatus.WATCHING -> WarmBrass
                        else -> SecondaryInk
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun SprocketTrack(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp),
    ) {
        drawLine(
            color = SprocketDark,
            start = Offset(0f, 0.5f),
            end = Offset(size.width, 0.5f),
            strokeWidth = 1f,
        )
        drawLine(
            color = SprocketDark,
            start = Offset(0f, size.height - 0.5f),
            end = Offset(size.width, size.height - 0.5f),
            strokeWidth = 1f,
        )
        val pitch = 20.dp.toPx()
        val holeWidth = 10.dp.toPx()
        var x = 6.dp.toPx()
        while (x < size.width) {
            drawRect(
                color = SprocketDark,
                topLeft = Offset(x, 2.dp.toPx()),
                size = Size(holeWidth, size.height - 4.dp.toPx()),
            )
            x += pitch
        }
    }
}

@Composable
fun EssentialArchiveFilmstrip(
    items: List<EnrichedCatalogItem>,
    onItemClick: (String) -> Unit,
    onRequestMetadata: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Essential Archive Reel",
                    style = MaterialTheme.typography.headlineSmall,
                    color = IvoryInk,
                )
                Text(
                    text = "Top-ranked milestones across both master lists.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryInk,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    onClick = {
                        scope.launch {
                            val target = (listState.firstVisibleItemIndex - 2).coerceAtLeast(0)
                            listState.animateScrollToItem(target)
                        }
                    },
                    shape = RoundedCornerShape(4.dp),
                    color = ArchivalSurface,
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Scroll filmstrip left",
                            tint = IvoryInk,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Surface(
                    onClick = {
                        scope.launch {
                            val target = (listState.firstVisibleItemIndex + 2).coerceAtMost((items.size - 1).coerceAtLeast(0))
                            listState.animateScrollToItem(target)
                        }
                    },
                    shape = RoundedCornerShape(4.dp),
                    color = ArchivalSurface,
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Scroll filmstrip right",
                            tint = IvoryInk,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        SprocketTrack()
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.testTag("essential_filmstrip_row"),
        ) {
            items(items, key = { it.item.id }) { enriched ->
                LaunchedEffect(enriched.item.id) {
                    onRequestMetadata(enriched.item)
                }
                Column(
                    modifier = Modifier
                        .width(126.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onItemClick(enriched.item.id) }
                        .testTag("filmstrip_item_${enriched.item.id}"),
                ) {
                    ArchivalPosterFrame(
                        enriched = enriched,
                        modifier = Modifier.fillMaxWidth(),
                        showQuickWatchToggle = false,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = enriched.item.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = IvoryInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "#${enriched.item.rank} · ${enriched.item.year}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedInk,
                    )
                }
            }
        }
        SprocketTrack()
    }
}

@Composable
fun EmptyArchiveState(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        color = ArchivalSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, HairlineBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.MovieFilter,
                contentDescription = null,
                tint = WarmBrass,
                modifier = Modifier.size(36.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = IvoryInk,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = SecondaryInk,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElevatedSurface,
                        contentColor = IvoryInk,
                    ),
                    border = BorderStroke(1.dp, WarmBrass),
                    modifier = Modifier.testTag("empty_state_action"),
                ) {
                    Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun EditorialFooter(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 16.dp),
    ) {
        HorizontalDivider(color = HairlineBorder)
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "MUVANTA · Your World of Stories",
                style = MaterialTheme.typography.labelMedium,
                color = SecondaryInk,
            )
            Text(
                text = "1,000 Films · 300 Series",
                style = MaterialTheme.typography.labelSmall,
                color = MutedInk,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.footer_attribution),
            style = MaterialTheme.typography.bodySmall,
            color = MutedInk,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
