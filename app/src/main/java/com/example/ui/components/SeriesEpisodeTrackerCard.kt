package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.SeriesEpisode
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
fun SeriesEpisodeTrackerCard(
    episodes: List<SeriesEpisode>,
    onToggleEpisode: (season: Int, episode: Int, currentlyWatched: Boolean) -> Unit,
    onSetSeasonWatched: (season: Int, seasonEpisodes: List<SeriesEpisode>, watched: Boolean) -> Unit,
    onRefreshEpisodes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val seasonsMap = remember(episodes) { episodes.groupBy { it.season }.toSortedMap() }
    val seasonNumbers = remember(seasonsMap) { seasonsMap.keys.toList() }
    var selectedSeason by remember(seasonNumbers) { mutableIntStateOf(seasonNumbers.firstOrNull() ?: 1) }

    val totalCount = episodes.size
    val watchedCount = episodes.count { it.watched }
    val overallProgress = if (totalCount > 0) watchedCount.toFloat() / totalCount else 0f
    val overallPercent = (overallProgress * 100f).toInt().coerceIn(0, 100)
    val nextEpisode = remember(episodes) { episodes.firstOrNull { !it.watched } }

    Surface(
        color = ArchivalSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("series_episode_tracker"),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SEASON & EPISODE TRACKER",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarmBrass,
                    )
                    Text(
                        text = if (totalCount > 0) {
                            "${seasonNumbers.size} Season${if (seasonNumbers.size == 1) "" else "s"} · $watchedCount / $totalCount Episodes ($overallPercent%)"
                        } else {
                            "Episode Guide"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = IvoryInk,
                    )
                }
                TextButton(
                    onClick = onRefreshEpisodes,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh episodes", tint = WarmBrass, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sync", style = MaterialTheme.typography.labelMedium, color = WarmBrass)
                }
            }

            if (totalCount == 0) {
                Text(
                    text = "Retrieving seasons and episode guide from TVmaze… Tap Sync if episodes do not appear shortly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryInk,
                )
            } else {
                LinearProgressIndicator(
                    progress = { overallProgress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = WatchedMoss,
                    trackColor = ObsidianBg,
                    strokeCap = StrokeCap.Butt,
                )

                // Resume / Next Up Episode Quick Action
                if (nextEpisode != null) {
                    Surface(
                        color = ElevatedSurface,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, WarmBrass.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "NEXT EPISODE · ${nextEpisode.code}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WarmBrass,
                                )
                                Text(
                                    text = nextEpisode.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = IvoryInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Button(
                                onClick = {
                                    onToggleEpisode(nextEpisode.season, nextEpisode.episode, false)
                                    selectedSeason = nextEpisode.season
                                },
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = WarmBrass, contentColor = ObsidianBg),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("mark_next_episode_watched"),
                            ) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Watched", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                } else {
                    Text(
                        text = "All $totalCount episodes completed.",
                        style = MaterialTheme.typography.labelMedium,
                        color = WatchedMoss,
                    )
                }

                // Season Tabs
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("season_tabs_row"),
                ) {
                    items(seasonNumbers, key = { it }) { sNum ->
                        val sEps = seasonsMap[sNum].orEmpty()
                        val sWatched = sEps.count { it.watched }
                        val sel = selectedSeason == sNum
                        FilterChip(
                            selected = sel,
                            onClick = { selectedSeason = sNum },
                            label = {
                                Text(
                                    text = "S$sNum ($sWatched/${sEps.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            shape = RoundedCornerShape(4.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = ObsidianBg,
                                selectedContainerColor = ElevatedSurface,
                                labelColor = SecondaryInk,
                                selectedLabelColor = IvoryInk,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = sel,
                                borderColor = HairlineBorder,
                                selectedBorderColor = WarmBrass,
                            ),
                        )
                    }
                }

                val currentSeasonEpisodes = seasonsMap[selectedSeason].orEmpty()
                val allSeasonWatched = currentSeasonEpisodes.isNotEmpty() && currentSeasonEpisodes.all { it.watched }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Season $selectedSeason (${currentSeasonEpisodes.count { it.watched }} / ${currentSeasonEpisodes.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = IvoryInk,
                    )
                    OutlinedButton(
                        onClick = {
                            onSetSeasonWatched(selectedSeason, currentSeasonEpisodes, !allSeasonWatched)
                        },
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, if (allSeasonWatched) WatchedMoss else HairlineBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("toggle_season_watched"),
                    ) {
                        Text(
                            text = if (allSeasonWatched) "Unmark Season" else "Mark Season Watched",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (allSeasonWatched) WatchedMoss else IvoryInk,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    currentSeasonEpisodes.forEachIndexed { idx, ep ->
                        if (idx > 0) HorizontalDivider(color = HairlineBorder.copy(alpha = 0.5f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleEpisode(ep.season, ep.episode, ep.watched) }
                                .padding(vertical = 8.dp)
                                .testTag("episode_row_${ep.code}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = if (ep.watched) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = if (ep.watched) "Watched ${ep.code}" else "Unwatched ${ep.code}",
                                tint = if (ep.watched) WatchedMoss else SecondaryInk,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${ep.code} · ${ep.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (ep.watched) SecondaryInk else IvoryInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val metaLine = buildList {
                                    if (ep.airDate.isNotBlank()) add(ep.airDate)
                                    if (ep.runtimeMinutes > 0) add("${ep.runtimeMinutes} min")
                                }.joinToString(" · ")
                                if (metaLine.isNotBlank()) {
                                    Text(
                                        text = metaLine,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MutedInk,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
