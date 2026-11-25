package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.ArchiveStats
import com.example.model.NamedCount
import com.example.ui.theme.ArchivalSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.IvoryInk
import com.example.ui.theme.MutedInk
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SecondaryInk
import com.example.ui.theme.WarmBrass
import com.example.ui.theme.WatchedMoss
import java.util.Locale

@Composable
fun StatisticsScreen(
    stats: ArchiveStats,
    onBack: () -> Unit,
    onOpenTier: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .testTag("statistics_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "stats_header") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("stats_back_button"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = IvoryInk)
                    }
                    Column {
                        Text(
                            text = "PERSONAL ARCHIVE METRICS",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarmBrass,
                        )
                        Text(
                            text = "Statistics & Taste Intelligence",
                            style = MaterialTheme.typography.headlineSmall,
                            color = IvoryInk,
                        )
                    }
                }
                Text(
                    text = "Calculated strictly from the ${stats.totalTitles} collection entries and your recorded local viewing activity.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryInk,
                )
            }
        }

        // Summary KPI Cards
        item(key = "stats_summary") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricKpiCard(
                        label = "OVERALL COMPLETED",
                        value = "${stats.totalWatched} / ${stats.totalTitles}",
                        subtitle = "${stats.overallPercent}% · ${stats.totalRewatches} rewatches",
                        modifier = Modifier.weight(1f),
                    )
                    MetricKpiCard(
                        label = "MOVIES WATCHED",
                        value = "${stats.watchedMovies} / ${stats.totalMovies}",
                        subtitle = "${stats.moviesPercent}% · ${stats.watchedDocs} docs",
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricKpiCard(
                        label = "SERIES & EPISODES",
                        value = "${stats.watchedSeries} / ${stats.totalSeries}",
                        subtitle = "${stats.totalEpisodesWatched} eps · ${stats.watchedAnime} anime",
                        modifier = Modifier.weight(1f),
                    )
                    val avgText = if (stats.ratedCount > 0) {
                        String.format(Locale.US, "Avg %.1f / 10 (%d rated)", stats.averageRating, stats.ratedCount)
                    } else {
                        "0 titles personally rated"
                    }
                    MetricKpiCard(
                        label = "FAVOURITES & REVIEWS",
                        value = "${stats.favoritesCount} ★ · ${stats.reviewsCount}",
                        subtitle = avgText,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Viewing Status Distribution (all statuses)
        item(key = "stats_distribution") {
            Surface(
                color = ArchivalSurface,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Viewing Status Distribution",
                        style = MaterialTheme.typography.titleLarge,
                        color = IvoryInk,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusDistTile("Watched", stats.totalWatched, WatchedMoss, Modifier.weight(1f))
                        StatusDistTile("Watching", stats.watchingCount, WarmBrass, Modifier.weight(1f))
                        StatusDistTile("Rewatching", stats.rewatchingCount, WarmBrass, Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusDistTile("Watchlist", stats.bookmarkedCount, IvoryInk, Modifier.weight(1f))
                        StatusDistTile("On Hold", stats.onHoldCount, SecondaryInk, Modifier.weight(1f))
                        StatusDistTile("Dropped", stats.droppedCount, MutedInk, Modifier.weight(1f))
                    }
                }
            }
        }

        // Personal Rating Distribution Bar Chart (1–10)
        if (stats.ratedCount > 0) {
            item(key = "stats_ratings") {
                val maxBucket = (stats.ratingDistribution.values.maxOrNull() ?: 1).coerceAtLeast(1)
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
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Personal Rating Distribution", style = MaterialTheme.typography.titleLarge, color = IvoryInk)
                            Text(
                                text = String.format(Locale.US, "Avg %.1f★", stats.averageRating / 2f),
                                style = MaterialTheme.typography.labelLarge,
                                color = WarmBrass,
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            (1..10).forEach { score ->
                                val count = stats.ratingDistribution[score] ?: 0
                                val barFraction = if (count > 0) (count.toFloat() / maxBucket).coerceIn(0.12f, 1f) else 0.04f
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom,
                                ) {
                                    if (count > 0) {
                                        Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = SecondaryInk)
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height((72f * barFraction).dp)
                                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                            .background(if (count > 0) WarmBrass else HairlineBorder),
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(score.toString(), style = MaterialTheme.typography.labelSmall, color = MutedInk)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Personal Taste Profile (Top Genres, Top Moods, Top Decades)
        if (stats.topGenres.isNotEmpty() || stats.topMoods.isNotEmpty() || stats.topDecades.isNotEmpty()) {
            item(key = "stats_taste_profile") {
                Surface(
                    color = ArchivalSurface,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Column {
                            Text("TASTE PROFILE", style = MaterialTheme.typography.labelSmall, color = WarmBrass)
                            Text("Your Watching Preferences", style = MaterialTheme.typography.titleLarge, color = IvoryInk)
                        }
                        if (stats.topGenres.isNotEmpty()) {
                            TasteRankingSection("MOST-WATCHED GENRES", stats.topGenres)
                        }
                        if (stats.topMoods.isNotEmpty()) {
                            TasteRankingSection("TOP EMOTIONAL MOODS", stats.topMoods)
                        }
                        if (stats.topDecades.isNotEmpty()) {
                            TasteRankingSection("FAVOURITE DECADES", stats.topDecades)
                        }
                    }
                }
            }
        }

        // Completion by Collection Tier
        item(key = "stats_tiers") {
            Surface(
                color = ArchivalSurface,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Completion by Collection Tier",
                        style = MaterialTheme.typography.titleLarge,
                        color = IvoryInk,
                    )
                    stats.tierProgress.forEach { tp ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onOpenTier(tp.tier.id) }
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = tp.tier.fullLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = IvoryInk,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${tp.watchedCount} / ${tp.tier.totalCount} (${tp.percent}%)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = WarmBrass,
                                )
                            }
                            LinearProgressIndicator(
                                progress = { tp.fraction.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = WarmBrass,
                                trackColor = ObsidianBg,
                                strokeCap = StrokeCap.Butt,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TasteRankingSection(
    eyebrow: String,
    items: List<NamedCount>,
) {
    val maxVal = (items.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = eyebrow, style = MaterialTheme.typography.labelSmall, color = SecondaryInk)
        items.forEach { entry ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = IvoryInk,
                    modifier = Modifier.width(116.dp),
                )
                LinearProgressIndicator(
                    progress = { (entry.count.toFloat() / maxVal).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = WarmBrass,
                    trackColor = ObsidianBg,
                    strokeCap = StrokeCap.Butt,
                )
                Text(
                    text = entry.count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = WarmBrass,
                    modifier = Modifier.width(28.dp),
                )
            }
        }
    }
}

@Composable
private fun MetricKpiCard(
    label: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = ArchivalSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, HairlineBorder),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = SecondaryInk)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = value, style = MaterialTheme.typography.headlineSmall, color = IvoryInk)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MutedInk)
        }
    }
}

@Composable
private fun StatusDistTile(
    label: String,
    count: Int,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = ObsidianBg,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, HairlineBorder),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = SecondaryInk)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = count.toString(), style = MaterialTheme.typography.titleMedium, color = valueColor)
        }
    }
}
