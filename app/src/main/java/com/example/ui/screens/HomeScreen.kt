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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.LocalMovies
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.CatalogItem
import com.example.model.DiscoverCategory
import com.example.model.EnrichedCatalogItem
import com.example.model.MoodCategory
import com.example.model.RecommendationItem
import com.example.ui.components.ArchivalPosterCard
import com.example.ui.components.ArchivalPosterFrame
import com.example.ui.components.CompactProgressBanner
import com.example.ui.components.EditorialFooter
import com.example.ui.components.EditorialSearchBar
import com.example.ui.components.EssentialArchiveFilmstrip
import com.example.ui.components.MuvantaWordmark
import com.example.ui.theme.ArchivalSurface
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.IvoryInk
import com.example.ui.theme.MutedInk
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SecondaryInk
import com.example.ui.theme.WarmBrass
import com.example.ui.viewmodel.HomeUiState

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    profileName: String,
    isSignedIn: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmitToDiscover: (String) -> Unit,
    onOpenCategory: (DiscoverCategory) -> Unit,
    onOpenMood: (MoodCategory) -> Unit,
    onOpenTitle: (String) -> Unit,
    onToggleWatched: (String) -> Unit,
    onRequestMetadata: (CatalogItem) -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSignIn: () -> Unit,
    onPickUnwatched: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.isLoading) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ObsidianBg),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = WarmBrass)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Loading archival catalog…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryInk,
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .testTag("home_screen"),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        // Editorial Top Bar
        item(key = "header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MuvantaWordmark()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!isSignedIn) {
                            Surface(
                                onClick = onOpenSignIn,
                                shape = RoundedCornerShape(4.dp),
                                color = ArchivalSurface,
                                border = BorderStroke(1.dp, HairlineBorder),
                                modifier = Modifier.testTag("home_sign_in_button"),
                            ) {
                                Text(
                                    text = "Sign In",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = WarmBrass,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                )
                            }
                        }
                        Surface(
                            onClick = onPickUnwatched,
                            shape = RoundedCornerShape(4.dp),
                            color = ElevatedSurface,
                            border = BorderStroke(1.dp, WarmBrass),
                            modifier = Modifier.testTag("home_pick_unwatched"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "CURATED LIFETIME ARCHIVE",
                    style = MaterialTheme.typography.labelMedium,
                    color = WarmBrass,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "The Ultimate Lifetime Cinema & Television Collection",
                    style = MaterialTheme.typography.headlineLarge,
                    color = IvoryInk,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "1,000 ranked films and 300 television series organized from all-time masterworks to comfort viewing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryInk,
                )

                Spacer(modifier = Modifier.height(16.dp))
                EditorialSearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onSearchSubmit = { onSearchSubmitToDiscover(uiState.searchQuery) },
                    testTag = "home_search_input",
                )

                // Live Search Preview Panel
                if (uiState.searchQuery.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = ArchivalSurface,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (uiState.searchPreviewResults.isEmpty()) {
                                Text(
                                    text = "No titles match \"${uiState.searchQuery}\".",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryInk,
                                )
                            } else {
                                uiState.searchPreviewResults.forEachIndexed { idx, match ->
                                    if (idx > 0) HorizontalDivider(color = HairlineBorder, modifier = Modifier.padding(vertical = 6.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenTitle(match.item.id) }
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = match.item.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = IvoryInk,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = "#${match.item.rank} · ${match.item.year} · ${match.effectiveGenre}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = SecondaryInk,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            tint = WarmBrass,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(
                                    onClick = { onSearchSubmitToDiscover(uiState.searchQuery) },
                                    modifier = Modifier.align(Alignment.End),
                                ) {
                                    Text(
                                        text = "View all results in Discover",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = WarmBrass,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                CompactProgressBanner(
                    stats = uiState.stats,
                    onOpenStats = onOpenStatistics,
                )
            }
        }

        // Featured Spotlight Carousel
        item(key = "spotlight") {
            Spacer(modifier = Modifier.height(8.dp))
            SectionHeaderRow(
                eyebrow = "FEATURED SPOTLIGHT",
                title = "Featured Movies & Series",
                actionLabel = "Explore All",
                onAction = { onOpenCategory(DiscoverCategory.ALL) },
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.testTag("featured_spotlight_row"),
            ) {
                items(uiState.featuredSpotlight, key = { it.item.id }) { item ->
                    LaunchedEffect(item.item.id) { onRequestMetadata(item.item) }
                    SpotlightEditorialCard(
                        enriched = item,
                        onClick = { onOpenTitle(item.item.id) },
                    )
                }
            }
        }

        // Essential Archive Filmstrip
        item(key = "filmstrip") {
            Spacer(modifier = Modifier.height(24.dp))
            EssentialArchiveFilmstrip(
                items = uiState.filmstripReel,
                onItemClick = onOpenTitle,
                onRequestMetadata = onRequestMetadata,
            )
        }

        // Discovery Categories Grid (Movies, Series, Anime, Documentaries)
        item(key = "categories") {
            Spacer(modifier = Modifier.height(24.dp))
            SectionHeaderRow(
                eyebrow = "BROWSE BY FORMAT",
                title = "Discovery Categories",
                actionLabel = null,
                onAction = null,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CategoryTileCard(
                        title = "Movies",
                        subtitle = "${uiState.movieCount} ranked films",
                        icon = Icons.Outlined.LocalMovies,
                        onClick = { onOpenCategory(DiscoverCategory.MOVIES) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("category_card_movies"),
                    )
                    CategoryTileCard(
                        title = "Series",
                        subtitle = "${uiState.seriesCount} iconic series",
                        icon = Icons.Outlined.LiveTv,
                        onClick = { onOpenCategory(DiscoverCategory.SERIES) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("category_card_series"),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CategoryTileCard(
                        title = "Anime",
                        subtitle = "${uiState.animeCount} films & series",
                        icon = Icons.Outlined.AutoAwesome,
                        onClick = { onOpenCategory(DiscoverCategory.ANIME) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("category_card_anime"),
                    )
                    CategoryTileCard(
                        title = "Documentaries",
                        subtitle = "${uiState.docCount} nonfiction works",
                        icon = Icons.Outlined.Public,
                        onClick = { onOpenCategory(DiscoverCategory.DOCUMENTARIES) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("category_card_documentaries"),
                    )
                }
            }
        }

        // Discover by Mood (Emotion-First Discovery — separate from Genre)
        item(key = "mood_discovery") {
            Spacer(modifier = Modifier.height(24.dp))
            SectionHeaderRow(
                eyebrow = "EMOTION-FIRST DISCOVERY",
                title = "Discover by Mood",
                actionLabel = null,
                onAction = null,
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.testTag("home_mood_row"),
            ) {
                items(MoodCategory.entries, key = { it.name }) { mood ->
                    Surface(
                        onClick = { onOpenMood(mood) },
                        color = ArchivalSurface,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier
                            .width(164.dp)
                            .testTag("home_mood_${mood.name.lowercase()}"),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = mood.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = WarmBrass,
                                maxLines = 1,
                            )
                            Text(
                                text = mood.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        // Explainable Personalized Recommendations (shown once user rates/watches titles)
        if (uiState.recommendations.isNotEmpty()) {
            item(key = "recommendations") {
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeaderRow(
                    eyebrow = "TASTE INTELLIGENCE",
                    title = "Recommended For You",
                    actionLabel = null,
                    onAction = null,
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.testTag("home_recommendations_row"),
                ) {
                    items(uiState.recommendations, key = { it.enriched.item.id }) { rec: RecommendationItem ->
                        Column(modifier = Modifier.width(156.dp)) {
                            ArchivalPosterCard(
                                enriched = rec.enriched,
                                onClick = { onOpenTitle(rec.enriched.item.id) },
                                onToggleWatched = { onToggleWatched(rec.enriched.item.id) },
                                onRequestMetadata = onRequestMetadata,
                                cardWidth = 156.dp,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = rec.reason,
                                style = MaterialTheme.typography.labelSmall,
                                color = WarmBrass,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        // Personalized / Continue Your Archive Section
        item(key = "personalized") {
            Spacer(modifier = Modifier.height(24.dp))
            if (uiState.continueArchive.isNotEmpty()) {
                SectionHeaderRow(
                    eyebrow = "FOR ${profileName.uppercase()}",
                    title = "Continue Your Archive",
                    actionLabel = "Statistics",
                    onAction = onOpenStatistics,
                )
                PosterCarouselRow(
                    items = uiState.continueArchive,
                    onItemClick = onOpenTitle,
                    onToggleWatched = onToggleWatched,
                    onRequestMetadata = onRequestMetadata,
                )
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    color = ArchivalSurface,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "PERSONALIZED ARCHIVE · PHASE 1",
                            style = MaterialTheme.typography.labelMedium,
                            color = WarmBrass,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Your Personal Cinema Log Starts Locally",
                            style = MaterialTheme.typography.headlineSmall,
                            color = IvoryInk,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Mark films as Watched, add to your Watchlist, or rate any title out of 10. Your activity appears here automatically and stays exportable via JSON & CSV in Profile. Cloud accounts arrive in Phase 2.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryInk,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onOpenProfile,
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElevatedSurface,
                                contentColor = IvoryInk,
                            ),
                            border = BorderStroke(1.dp, WarmBrass),
                            modifier = Modifier.testTag("home_open_profile_cta"),
                        ) {
                            Text("Open Archivist Profile & Backup")
                        }
                    }
                }
            }
        }

        // Trending & Top Ranked Movies
        item(key = "trending_movies") {
            Spacer(modifier = Modifier.height(26.dp))
            SectionHeaderRow(
                eyebrow = "MASTER LIST 1",
                title = "Top Ranked Movies",
                actionLabel = "All 1,000",
                onAction = { onOpenCategory(DiscoverCategory.MOVIES) },
            )
            PosterCarouselRow(
                items = uiState.trendingMovies,
                onItemClick = onOpenTitle,
                onToggleWatched = onToggleWatched,
                onRequestMetadata = onRequestMetadata,
            )
        }

        // Prestige Series
        item(key = "prestige_series") {
            Spacer(modifier = Modifier.height(26.dp))
            SectionHeaderRow(
                eyebrow = "MASTER LIST 2",
                title = "Essential Television Series",
                actionLabel = "All 300",
                onAction = { onOpenCategory(DiscoverCategory.SERIES) },
            )
            PosterCarouselRow(
                items = uiState.prestigeSeries,
                onItemClick = onOpenTitle,
                onToggleWatched = onToggleWatched,
                onRequestMetadata = onRequestMetadata,
            )
        }

        // Essential Anime
        item(key = "essential_anime") {
            Spacer(modifier = Modifier.height(26.dp))
            SectionHeaderRow(
                eyebrow = "ANIMATION & ANIME",
                title = "Essential Anime Cinema & Series",
                actionLabel = "Explore",
                onAction = { onOpenCategory(DiscoverCategory.ANIME) },
            )
            PosterCarouselRow(
                items = uiState.essentialAnime,
                onItemClick = onOpenTitle,
                onToggleWatched = onToggleWatched,
                onRequestMetadata = onRequestMetadata,
            )
        }

        // Your Watchlist Row (dynamic when user bookmarks titles)
        if (uiState.watchlistRow.isNotEmpty()) {
            item(key = "home_watchlist") {
                Spacer(modifier = Modifier.height(26.dp))
                SectionHeaderRow(
                    eyebrow = "SAVED FOR LATER",
                    title = "Your Watchlist",
                    actionLabel = null,
                    onAction = null,
                )
                PosterCarouselRow(
                    items = uiState.watchlistRow,
                    onItemClick = onOpenTitle,
                    onToggleWatched = onToggleWatched,
                    onRequestMetadata = onRequestMetadata,
                )
            }
        }

        // Recently Released Modern Standouts
        if (uiState.recentlyReleased.isNotEmpty()) {
            item(key = "recently_released") {
                Spacer(modifier = Modifier.height(26.dp))
                SectionHeaderRow(
                    eyebrow = "MODERN ERA · 2020S",
                    title = "Recently Released Standouts",
                    actionLabel = "All 2020s",
                    onAction = { onOpenCategory(DiscoverCategory.ALL) },
                )
                PosterCarouselRow(
                    items = uiState.recentlyReleased,
                    onItemClick = onOpenTitle,
                    onToggleWatched = onToggleWatched,
                    onRequestMetadata = onRequestMetadata,
                )
            }
        }

        // Hidden Gems
        if (uiState.hiddenGems.isNotEmpty()) {
            item(key = "hidden_gems") {
                Spacer(modifier = Modifier.height(26.dp))
                SectionHeaderRow(
                    eyebrow = "DEEPER IN THE ARCHIVE",
                    title = "Hidden Gems to Uncover",
                    actionLabel = "Explore",
                    onAction = { onOpenCategory(DiscoverCategory.MOVIES) },
                )
                PosterCarouselRow(
                    items = uiState.hiddenGems,
                    onItemClick = onOpenTitle,
                    onToggleWatched = onToggleWatched,
                    onRequestMetadata = onRequestMetadata,
                )
            }
        }

        // Acclaimed Documentaries
        item(key = "acclaimed_docs") {
            Spacer(modifier = Modifier.height(26.dp))
            SectionHeaderRow(
                eyebrow = "NONFICTION ARCHIVE",
                title = "Acclaimed Documentaries",
                actionLabel = "Explore",
                onAction = { onOpenCategory(DiscoverCategory.DOCUMENTARIES) },
            )
            PosterCarouselRow(
                items = uiState.acclaimedDocs,
                onItemClick = onOpenTitle,
                onToggleWatched = onToggleWatched,
                onRequestMetadata = onRequestMetadata,
            )
        }

        // Clean Attribution Footer
        item(key = "footer") {
            EditorialFooter()
        }
    }
}

@Composable
private fun SectionHeaderRow(
    eyebrow: String,
    title: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelSmall,
                color = WarmBrass,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = IvoryInk,
            )
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = WarmBrass,
                )
            }
        }
    }
}

@Composable
private fun SpotlightEditorialCard(
    enriched: EnrichedCatalogItem,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = ArchivalSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier
            .width(316.dp)
            .testTag("spotlight_card_${enriched.item.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            ArchivalPosterFrame(
                enriched = enriched,
                modifier = Modifier.width(104.dp),
                showQuickWatchToggle = false,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "#${enriched.item.rank} · ${enriched.item.type.label.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = WarmBrass,
                )
                Text(
                    text = enriched.item.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = IvoryInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(enriched.item.year)
                        if (enriched.effectiveGenre.isNotBlank()) {
                            append(" · ")
                            append(enriched.effectiveGenre)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (enriched.effectiveDescription.isNotBlank()) {
                    Text(
                        text = enriched.effectiveDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedInk,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Open Title Page →",
                    style = MaterialTheme.typography.labelMedium,
                    color = WarmBrass,
                )
            }
        }
    }
}

@Composable
private fun CategoryTileCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = ArchivalSurface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, HairlineBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = ElevatedSurface,
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = WarmBrass,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = IvoryInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PosterCarouselRow(
    items: List<EnrichedCatalogItem>,
    onItemClick: (String) -> Unit,
    onToggleWatched: (String) -> Unit,
    onRequestMetadata: (CatalogItem) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(items, key = { it.item.id }) { enriched ->
            ArchivalPosterCard(
                enriched = enriched,
                onClick = { onItemClick(enriched.item.id) },
                onToggleWatched = { onToggleWatched(enriched.item.id) },
                onRequestMetadata = onRequestMetadata,
                cardWidth = 144.dp,
            )
        }
    }
}
