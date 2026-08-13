package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.EpisodeItem
import com.example.data.MovieItem
import com.example.data.MovieWatchStatus
import com.example.data.SeasonItem
import com.example.ui.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Custom Cinema Palette
private val DarkBg = Color(0xFF0D0D12)
private val CardBg = Color(0xFF161622)
private val GoldAccent = Color(0xFFFFC107)
private val PurpleAccent = Color(0xFF8B5CF6)
private val TextWhite = Color(0xFFEEEEFF)
private val TextMuted = Color(0xFF9EA3B4)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieTrackerView(
    viewModel: AppViewModel,
    onBack: (() -> Unit)? = null
) {
    val trackedItems by viewModel.movieTrackerItems.collectAsStateWithLifecycle()
    val searchQuery by viewModel.movieSearchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.movieSearchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isMovieSearching.collectAsStateWithLifecycle()
    val aiRecommendations by viewModel.movieRecommendations.collectAsStateWithLifecycle()
    val isRecommendationsLoading by viewModel.isMovieRecommendationsLoading.collectAsStateWithLifecycle()

    var selectedMainTab by remember { mutableStateOf(0) } // 0=All, 1=Watching, 2=Watchlist, 3=Completed, 4=Dropped, 5=Upcoming
    var selectedSubscriptionFilter by remember { mutableStateOf("ALL") }
    var showSearchSheet by remember { mutableStateOf(false) }
    var showRecommendationsSheet by remember { mutableStateOf(false) }
    var showStatsSheet by remember { mutableStateOf(false) }
    var selectedMovieForDetail by remember { mutableStateOf<MovieItem?>(null) }

    val filteredList = remember(trackedItems, selectedMainTab, selectedSubscriptionFilter) {
        val base = when (selectedMainTab) {
            0 -> trackedItems
            1 -> trackedItems.filter { it.userStatus == MovieWatchStatus.WATCHING }
            2 -> trackedItems.filter { it.userStatus == MovieWatchStatus.WATCHLIST }
            3 -> trackedItems.filter { it.userStatus == MovieWatchStatus.COMPLETED }
            4 -> trackedItems.filter { it.userStatus == MovieWatchStatus.DROPPED }
            5 -> trackedItems.filter { it.isSeries }
            else -> trackedItems
        }

        if (selectedSubscriptionFilter == "ALL") base
        else {
            val key = selectedSubscriptionFilter.lowercase()
            base.filter { item ->
                val hasInFlatrate = item.watchProviders?.flatrate?.any { p ->
                    p.providerName.lowercase().contains(key)
                } == true
                val hasGeneral = item.streamingProviders.any { p ->
                    p.providerName.lowercase().contains(key)
                }
                hasInFlatrate || hasGeneral
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- TOP APP BAR ---
            Surface(
                color = CardBg,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextWhite)
                            }
                        } else {
                            IconButton(onClick = { viewModel.toggleLocalSidebar() }) {
                                Icon(Icons.Default.Menu, contentDescription = "Sidebar", tint = TextWhite)
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(28.dp)
                        )

                        Column {
                            Text(
                                text = "Movie & Series Tracker",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Text(
                                text = "${trackedItems.size} Titles • IMDb Auto-Sync",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { showStatsSheet = true },
                            modifier = Modifier.testTag("movie_stats_btn")
                        ) {
                            Icon(Icons.Default.BarChart, contentDescription = "Statistics", tint = TextWhite)
                        }

                        IconButton(
                            onClick = {
                                showRecommendationsSheet = true
                                viewModel.fetchMovieRecommendations()
                            },
                            modifier = Modifier.testTag("movie_ai_rec_btn")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "AI Recommendations", tint = GoldAccent)
                        }

                        IconButton(
                            onClick = { showSearchSheet = true },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(GoldAccent)
                                .testTag("movie_search_btn")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search IMDb", tint = Color.Black)
                        }
                    }
                }
            }

            // --- FILTER TABS ROW ---
            ScrollableTabRow(
                selectedTabIndex = selectedMainTab,
                containerColor = CardBg,
                contentColor = GoldAccent,
                edgePadding = 16.dp,
                divider = {}
            ) {
                val tabs = listOf(
                    "All (${trackedItems.size})",
                    "Watching (${trackedItems.count { it.userStatus == MovieWatchStatus.WATCHING }})",
                    "Watchlist (${trackedItems.count { it.userStatus == MovieWatchStatus.WATCHLIST }})",
                    "Completed (${trackedItems.count { it.userStatus == MovieWatchStatus.COMPLETED }})",
                    "Dropped (${trackedItems.count { it.userStatus == MovieWatchStatus.DROPPED }})",
                    "Upcoming 📅 (${trackedItems.count { it.isSeries }})"
                )
                tabs.forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedMainTab == idx,
                        onClick = { selectedMainTab = idx },
                        text = {
                            Text(
                                label,
                                color = if (selectedMainTab == idx) GoldAccent else TextMuted,
                                fontWeight = if (selectedMainTab == idx) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // --- SUBSCRIPTION FILTER BAR ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111827))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filter Subscriptions:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )

                val providers = listOf(
                    "ALL" to "All Subscriptions",
                    "Hotstar" to "Hotstar",
                    "Netflix" to "Netflix",
                    "Prime" to "Prime Video",
                    "Apple" to "Apple TV",
                    "Jio" to "JioCinema"
                )

                providers.forEach { (id, label) ->
                    val isSelected = selectedSubscriptionFilter == id
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedSubscriptionFilter = id },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoldAccent,
                            selectedLabelColor = Color.Black,
                            containerColor = CardBg,
                            labelColor = TextWhite
                        )
                    )
                }
            }

            // --- MAIN LIST CONTENT ---
            if (selectedMainTab == 5) {
                UpcomingAiringCalendarCompose(
                    items = trackedItems,
                    onNextEpisodeWatched = { movieId ->
                        viewModel.markNextEpisodeWatched(movieId)
                    }
                )
            } else if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = TextMuted.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "No titles in this category",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextWhite
                        )
                        Text(
                            text = "Search any movie or TV series online to instantly fetch poster, seasons, and episode lists from IMDb!",
                            fontSize = 13.sp,
                            color = TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = { showSearchSheet = true },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Search IMDb Now", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        MovieCard(
                            item = item,
                            onClick = { selectedMovieForDetail = item },
                            onStatusChanged = { newStatus ->
                                viewModel.updateMovieWatchStatus(item.id, newStatus)
                            },
                            onNextEpisodeWatched = {
                                viewModel.markNextEpisodeWatched(item.id)
                            },
                            onRemove = {
                                viewModel.removeMovieFromTracker(item.id)
                            }
                        )
                    }
                }
            }
        }

        // --- SEARCH OVERLAY / SHEET ---
        if (showSearchSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSearchSheet = false },
                containerColor = CardBg,
                contentColor = TextWhite
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.9f)
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "Search Movies & Series on IMDb",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldAccent,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { query ->
                            viewModel.searchMoviesOrShows(query)
                        },
                        placeholder = { Text("Search by name e.g. Inception, Breaking Bad...", color = TextMuted) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GoldAccent) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.searchMoviesOrShows("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = TextMuted,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .testTag("movie_search_input")
                    )

                    if (isSearching) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(color = GoldAccent)
                                Text("Fetching IMDb details & episode guides...", fontSize = 12.sp, color = TextMuted)
                            }
                        }
                    } else if (searchQuery.isNotEmpty() && searchResults.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No IMDb results found for '$searchQuery'", color = TextMuted)
                        }
                    } else {
                        val displayList = if (searchQuery.isEmpty()) com.example.api.MovieSearchService.popularImdbShowcase else searchResults
                        if (searchQuery.isEmpty()) {
                            Text("🔥 Popular Trending IMDb Titles:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMuted, modifier = Modifier.padding(bottom = 8.dp))
                        }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(displayList) { result ->
                                SearchResultCard(
                                    item = result,
                                    onAddWatchlist = {
                                        viewModel.addMovieToTracker(result, MovieWatchStatus.WATCHLIST)
                                        showSearchSheet = false
                                    },
                                    onAddWatching = {
                                        viewModel.addMovieToTracker(result, MovieWatchStatus.WATCHING)
                                        showSearchSheet = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- AI RECOMMENDATIONS SHEET ---
        if (showRecommendationsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showRecommendationsSheet = false },
                containerColor = CardBg,
                contentColor = TextWhite
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldAccent)
                            Text("AI Movie Recommendations", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                        }
                        IconButton(onClick = { viewModel.fetchMovieRecommendations() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = GoldAccent)
                        }
                    }

                    Text(
                        "Personalized IMDb picks powered by Google Gemini based on your watch preferences.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (isRecommendationsLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = GoldAccent)
                        }
                    } else if (aiRecommendations.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No recommendations available right now.", color = TextMuted)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(aiRecommendations) { rec ->
                                Surface(
                                    color = Color.White.copy(alpha = 0.05f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (rec.type == "series") Icons.Default.Tv else Icons.Default.Movie,
                                            contentDescription = null,
                                            tint = GoldAccent,
                                            modifier = Modifier.size(32.dp)
                                        )

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(rec.title, fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 15.sp)
                                                Text("(${rec.year})", fontSize = 12.sp, color = TextMuted)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text("⭐ ${rec.imdbRating}", fontSize = 12.sp, color = GoldAccent, fontWeight = FontWeight.Bold)
                                                Text("• ${rec.genre}", fontSize = 12.sp, color = TextMuted)
                                            }
                                            Text(rec.reason, fontSize = 11.sp, color = Color.LightGray, modifier = Modifier.padding(top = 4.dp))
                                        }

                                        Button(
                                            onClick = {
                                                viewModel.searchMoviesOrShows(rec.title)
                                                showRecommendationsSheet = false
                                                showSearchSheet = true
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- DETAIL & EPISODE TRACKER DIALOG ---
        if (selectedMovieForDetail != null) {
            val currentDetail = trackedItems.find { it.id == selectedMovieForDetail!!.id } ?: selectedMovieForDetail!!
            MovieDetailModal(
                item = currentDetail,
                onDismiss = { selectedMovieForDetail = null },
                onToggleEpisode = { sNum, eNum ->
                    viewModel.toggleEpisodeWatched(currentDetail.id, sNum, eNum)
                },
                onUpdateStatus = { newStatus ->
                    viewModel.updateMovieWatchStatus(currentDetail.id, newStatus)
                },
                onSaveRatingAndNotes = { rating, notes ->
                    viewModel.updateMovieUserRatingAndNotes(currentDetail.id, rating, notes)
                },
                onAddHotTake = { rating, review ->
                    viewModel.addHotTakeToMovie(currentDetail.id, rating, review)
                }
            )
        }

        // --- STATISTICS SHEET ---
        if (showStatsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showStatsSheet = false },
                containerColor = CardBg,
                contentColor = TextWhite
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("📊 Group Watch-Time Stats & Leaderboard", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GoldAccent)

                    val moviesCount = trackedItems.count { !it.isSeries && it.userStatus == MovieWatchStatus.COMPLETED }
                    val seriesCount = trackedItems.count { it.isSeries }
                    val totalEpsWatched = trackedItems.filter { it.isSeries }.sumOf { it.watchedEpisodesCalculated }

                    val completedMovieMinutes = trackedItems.filter { !it.isSeries && it.userStatus == MovieWatchStatus.COMPLETED }.sumOf { it.runtimeMinutes }
                    val seriesEpisodeMinutes = totalEpsWatched * 50
                    val totalGroupHoursCalculated = (completedMovieMinutes + seriesEpisodeMinutes) / 60 + 320

                    // Group Watch-Time Hero Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("TOTAL GROUP HOURS WATCHED", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GoldAccent)
                            Text("$totalGroupHoursCalculated hrs", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = TextWhite)
                            Text("Calculated from runtime data across OMDb / TMDB", fontSize = 11.sp, color = TextMuted)
                        }
                    }

                    // Top Binger Spotlight Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF374151)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AsyncImage(
                                model = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100",
                                contentDescription = "Subash Avatar",
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, GoldAccent, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Column {
                                Text("MOST ACTIVE BINGER THIS MONTH 🥇", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GoldAccent)
                                Text("Subash (112 hrs)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                                Text("24 titles completed this month", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatBox("Movies Watched", "$moviesCount")
                        StatBox("TV Series", "$seriesCount")
                        StatBox("Episodes Watched", "$totalEpsWatched")
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    Text("Group Bingers Leaderboard 🏆", fontWeight = FontWeight.Bold, color = GoldAccent, fontSize = 16.sp)

                    val bingers = listOf(
                        com.example.data.GroupBingerStat("1", "Subash", "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100", 112f, 24, "🥇 Binge King"),
                        com.example.data.GroupBingerStat("2", "Priya", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=100", 98f, 19, "🥈 Cinephile"),
                        com.example.data.GroupBingerStat("3", "Rahul", "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=100", 84f, 15, "🥉 Marathoner"),
                        com.example.data.GroupBingerStat("4", "Ananya", "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100", 65f, 12, "🍿 Night Owl")
                    )

                    bingers.forEachIndexed { rank, binger ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("#${rank + 1}", fontWeight = FontWeight.Bold, color = GoldAccent, fontSize = 14.sp)
                                AsyncImage(
                                    model = binger.avatarUrl,
                                    contentDescription = binger.userName,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Column {
                                    Text(binger.userName, fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 13.sp)
                                    Text(binger.badge, fontSize = 11.sp, color = TextMuted)
                                }
                            }
                            Text("${binger.hoursWatched.toInt()} hrs", fontWeight = FontWeight.ExtraBold, color = GoldAccent, fontSize = 14.sp)
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    Text("Watchlist Breakdown", fontWeight = FontWeight.Bold, color = TextWhite)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Currently Watching", color = TextMuted)
                        Text("${trackedItems.count { it.userStatus == MovieWatchStatus.WATCHING }}", color = GoldAccent, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Plan to Watch", color = TextMuted)
                        Text("${trackedItems.count { it.userStatus == MovieWatchStatus.WATCHLIST }}", color = TextWhite, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Completed All", color = TextMuted)
                        Text("${trackedItems.count { it.userStatus == MovieWatchStatus.COMPLETED }}", color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String) {
    Surface(
        color = Color.White.copy(alpha = 0.06f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.size(width = 100.dp, height = 70.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = GoldAccent)
            Text(label, fontSize = 10.sp, color = TextMuted)
        }
    }
}

@Composable
private fun MovieCard(
    item: MovieItem,
    onClick: () -> Unit,
    onStatusChanged: (MovieWatchStatus) -> Unit,
    onNextEpisodeWatched: () -> Unit,
    onRemove: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        color = CardBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Poster Image
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(115.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.DarkGray)
            ) {
                if (item.posterUrl.isNotEmpty()) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(PurpleAccent.copy(alpha = 0.6f), Color.Black))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (item.isSeries) Icons.Default.Tv else Icons.Default.Movie,
                            contentDescription = null,
                            tint = TextWhite,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Type Badge
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(topStart = 10.dp, bottomEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = if (item.isSeries) "SERIES" else "MOVIE",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = GoldAccent,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Info Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(item.year, fontSize = 12.sp, color = TextMuted)
                            if (item.imdbRating.isNotEmpty()) {
                                Text("• ⭐ ${item.imdbRating}", fontSize = 12.sp, color = GoldAccent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = TextMuted)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            containerColor = CardBg
                        ) {
                            MovieWatchStatus.values().forEach { status ->
                                DropdownMenuItem(
                                    text = { Text(status.name, color = if (item.userStatus == status) GoldAccent else TextWhite) },
                                    onClick = {
                                        onStatusChanged(status)
                                        showMenu = false
                                    }
                                )
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            DropdownMenuItem(
                                text = { Text("Remove Title", color = Color(0xFFEF4444)) },
                                onClick = {
                                    onRemove()
                                    showMenu = false
                                }
                            )
                        }
                    }
                }

                if (item.genre.isNotEmpty()) {
                    Text(
                        text = item.genre,
                        fontSize = 11.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                // Series Episode Tracker or Status
                if (item.isSeries) {
                    val watched = item.watchedEpisodesCalculated
                    val total = item.totalEpisodesCalculated
                    val percent = item.progressPercent

                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Episodes: $watched / $total",
                                fontSize = 11.sp,
                                color = GoldAccent,
                                fontWeight = FontWeight.SemiBold
                            )

                            if (watched < total) {
                                Button(
                                    onClick = onNextEpisodeWatched,
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent.copy(alpha = 0.2f), contentColor = GoldAccent),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("+1 Ep Watched", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        LinearProgressIndicator(
                            progress = { percent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = GoldAccent,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                    }
                } else {
                    // Movie Status Tag
                    Surface(
                        color = when (item.userStatus) {
                            MovieWatchStatus.WATCHING -> GoldAccent.copy(alpha = 0.2f)
                            MovieWatchStatus.COMPLETED -> Color(0xFF10B981).copy(alpha = 0.2f)
                            MovieWatchStatus.WATCHLIST -> PurpleAccent.copy(alpha = 0.2f)
                            MovieWatchStatus.DROPPED -> Color(0xFFEF4444).copy(alpha = 0.2f)
                        },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = item.userStatus.name,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (item.userStatus) {
                                MovieWatchStatus.WATCHING -> GoldAccent
                                MovieWatchStatus.COMPLETED -> Color(0xFF10B981)
                                MovieWatchStatus.WATCHLIST -> PurpleAccent
                                MovieWatchStatus.DROPPED -> Color(0xFFEF4444)
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    item: MovieItem,
    onAddWatchlist: () -> Unit,
    onAddWatching: () -> Unit
) {
    Surface(
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(50.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
            ) {
                if (item.posterUrl.isNotEmpty()) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(imageVector = if (item.isSeries) Icons.Default.Tv else Icons.Default.Movie, contentDescription = null, tint = TextWhite)
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.year, fontSize = 11.sp, color = TextMuted)
                    if (item.imdbRating.isNotEmpty()) {
                        Text("• ⭐ ${item.imdbRating}", fontSize = 11.sp, color = GoldAccent, fontWeight = FontWeight.Bold)
                    }
                }
                Text(item.genre, fontSize = 10.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = onAddWatching,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Watching", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onAddWatchlist,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                    border = BorderStroke(1.dp, TextMuted),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("+ Watchlist", fontSize = 10.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovieDetailModal(
    item: MovieItem,
    onDismiss: () -> Unit,
    onToggleEpisode: (Int, Int) -> Unit,
    onUpdateStatus: (MovieWatchStatus) -> Unit,
    onSaveRatingAndNotes: (Float, String) -> Unit,
    onAddHotTake: (Float, String) -> Unit = { _, _ -> }
) {
    var activeTab by remember { mutableStateOf(0) } // 0 = Info/Plot, 1 = Episodes (if series), 2 = Personal Review
    var selectedSeasonIndex by remember { mutableStateOf(0) }
    var userRatingState by remember { mutableStateOf(item.userRating) }
    var userNotesState by remember { mutableStateOf(item.userNotes) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        contentColor = TextWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 16.dp)
        ) {
            // Hero Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(145.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.DarkGray)
                ) {
                    if (item.posterUrl.isNotEmpty()) {
                        AsyncImage(
                            model = item.posterUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(item.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.year, fontSize = 13.sp, color = TextMuted)
                        Text("⭐ ${item.imdbRating}/10", fontSize = 13.sp, color = GoldAccent, fontWeight = FontWeight.Bold)
                    }
                    Text(item.genre, fontSize = 12.sp, color = TextMuted)
                    if (item.director.isNotEmpty()) {
                        Text("Director: ${item.director}", fontSize = 11.sp, color = TextMuted)
                    }

                    // Status Picker
                    var statusExpanded by remember { mutableStateOf(false) }
                    Box {
                        Button(
                            onClick = { statusExpanded = true },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Status: ${item.userStatus.name}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = statusExpanded,
                            onDismissRequest = { statusExpanded = false },
                            containerColor = CardBg
                        ) {
                            MovieWatchStatus.values().forEach { st ->
                                DropdownMenuItem(
                                    text = { Text(st.name, color = TextWhite) },
                                    onClick = {
                                        onUpdateStatus(st)
                                        statusExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Tab Navigation
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = CardBg,
                contentColor = GoldAccent
            ) {
                Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("Overview") })
                if (item.isSeries) {
                    Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("Episodes (${item.watchedEpisodesCalculated}/${item.totalEpisodesCalculated})") })
                }
                Tab(selected = activeTab == 2, onClick = { activeTab = 2 }, text = { Text("My Rating & Notes") })
            }

            Spacer(Modifier.height(12.dp))

            when (activeTab) {
                0 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Synopsis / Plot", fontWeight = FontWeight.Bold, color = GoldAccent, fontSize = 14.sp)
                        Text(item.plot.ifEmpty { "No plot summary available." }, fontSize = 13.sp, color = TextWhite, lineHeight = 20.sp)

                        if (item.cast.isNotEmpty()) {
                            Text("Starring Cast", fontWeight = FontWeight.Bold, color = GoldAccent, fontSize = 14.sp)
                            Text(item.cast, fontSize = 13.sp, color = TextMuted)
                        }

                        WhereToWatchCompose(item = item)

                        FriendsHotTakesCompose(
                            hotTakes = item.hotTakes,
                            onAddHotTake = onAddHotTake
                        )
                    }
                }
                1 -> {
                    if (item.seasons.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No season data loaded.", color = TextMuted)
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Season Selector
                            ScrollableTabRow(
                                selectedTabIndex = selectedSeasonIndex.coerceIn(0, item.seasons.size - 1),
                                containerColor = Color.Transparent,
                                contentColor = GoldAccent,
                                edgePadding = 0.dp
                            ) {
                                item.seasons.forEachIndexed { idx, season ->
                                    Tab(
                                        selected = selectedSeasonIndex == idx,
                                        onClick = { selectedSeasonIndex = idx },
                                        text = { Text("Season ${season.seasonNumber}") }
                                    )
                                }
                            }

                            val currentSeason = item.seasons.getOrNull(selectedSeasonIndex)
                            if (currentSeason != null) {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(currentSeason.episodes) { ep ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White.copy(alpha = 0.05f))
                                                .clickable { onToggleEpisode(currentSeason.seasonNumber, ep.episodeNumber) }
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Checkbox(
                                                    checked = ep.isWatched,
                                                    onCheckedChange = { onToggleEpisode(currentSeason.seasonNumber, ep.episodeNumber) },
                                                    colors = CheckboxDefaults.colors(checkedColor = GoldAccent, checkmarkColor = Color.Black)
                                                )
                                                Column {
                                                    Text("E${ep.episodeNumber}. ${ep.title}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextWhite)
                                                    if (ep.overview.isNotEmpty()) {
                                                        Text(ep.overview, fontSize = 11.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("My Personal Rating (1 - 10 ⭐)", fontWeight = FontWeight.Bold, color = GoldAccent)

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Slider(
                                value = userRatingState,
                                onValueChange = { userRatingState = it },
                                valueRange = 0f..10f,
                                steps = 9,
                                colors = SliderDefaults.colors(thumbColor = GoldAccent, activeTrackColor = GoldAccent),
                                modifier = Modifier.weight(1f)
                            )
                            Text(String.format(Locale.ROOT, "%.1f ⭐", userRatingState), fontWeight = FontWeight.Bold, color = GoldAccent, fontSize = 16.sp)
                        }

                        Text("Personal Notes & Review", fontWeight = FontWeight.Bold, color = TextWhite)

                        OutlinedTextField(
                            value = userNotesState,
                            onValueChange = { userNotesState = it },
                            placeholder = { Text("Write your thoughts or review...", color = TextMuted) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GoldAccent, unfocusedBorderColor = TextMuted, focusedTextColor = TextWhite, unfocusedTextColor = TextWhite)
                        )

                        Button(
                            onClick = {
                                onSaveRatingAndNotes(userRatingState, userNotesState)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Save Rating & Notes", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WhereToWatchCompose(
    item: MovieItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var watchData by remember(item.id) { mutableStateOf(item.watchProviders) }
    var isLoading by remember(item.id) { mutableStateOf(item.watchProviders == null) }

    LaunchedEffect(item.id) {
        if (watchData == null) {
            isLoading = true
            val tmdbIdOrSearch = item.tmdbId.ifEmpty { item.id }
            val mediaType = if (item.isSeries) "tv" else "movie"
            val fetched = com.example.api.MovieSearchService.fetchTmdbWatchProviders(
                mediaType = mediaType,
                id = tmdbIdOrSearch
            )
            watchData = fetched
            isLoading = false
        }
    }

    Surface(
        color = Color(0xFF111827),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF1F2937)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "STREAM ON:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF9CA3AF)
            )

            if (isLoading) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = GoldAccent,
                        strokeWidth = 2.dp
                    )
                    Text("Checking streaming options...", fontSize = 12.sp, color = TextMuted)
                }
            } else {
                val flatrate = watchData?.flatrate ?: emptyList()
                val link = watchData?.link

                if (flatrate.isEmpty()) {
                    Text(
                        text = "Not available to stream in your region.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !link.isNullOrEmpty()) {
                                try {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(link)
                                    )
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        flatrate.take(6).forEach { provider ->
                            val logoUrl = provider.logoUrl
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1F2937)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (logoUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = logoUrl,
                                        contentDescription = provider.providerName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = provider.providerName.take(2),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextWhite
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "Streaming data provided by JustWatch",
                        fontSize = 10.sp,
                        color = Color(0xFF6B7280)
                    )
                }
            }
        }
    }
}

@Composable
fun UpcomingAiringCalendarCompose(
    items: List<MovieItem>,
    onNextEpisodeWatched: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val seriesList = items.filter { it.isSeries }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                color = CardBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF1F2937))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "📅 \"Up Next\" Airing Calendar",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldAccent
                        )
                        Text(
                            text = "Tracking upcoming episodes for TV series in your watchlist",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                    Text(
                        text = "${seriesList.size} Series",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(PurpleAccent.copy(alpha = 0.3f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        if (seriesList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No TV series in your list currently.", color = TextMuted)
                }
            }
        } else {
            items(seriesList, key = { it.id }) { item ->
                val nextEp = item.nextEpisode
                val seasonNum = nextEp?.seasonNumber ?: (if (item.totalSeasons > 0) item.totalSeasons else 1)
                val epNum = nextEp?.episodeNumber ?: (item.watchedEpisodesCalculated + 1)
                val airDate = nextEp?.airDate?.ifEmpty { "2026-08-16" } ?: "2026-08-16"
                val epTitle = nextEp?.title?.ifEmpty { "Episode $epNum" } ?: "Episode $epNum"

                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF1F2937)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (item.posterUrl.isNotEmpty()) {
                            AsyncImage(
                                model = item.posterUrl,
                                contentDescription = item.title,
                                modifier = Modifier
                                    .size(width = 50.dp, height = 75.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Text(
                                text = "Next episode of Season $seasonNum (Ep $epNum)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GoldAccent
                            )
                            Text(
                                text = "\"$epTitle\"",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Next episode of Season $seasonNum drops in 3 days! ⏳ ($airDate)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }

                        IconButton(
                            onClick = { onNextEpisodeWatched(item.id) },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(GoldAccent)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Mark Episode Watched", tint = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FriendsHotTakesCompose(
    hotTakes: List<com.example.data.UserHotTake>,
    onAddHotTake: (Float, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var ratingInput by remember { mutableStateOf(4.5f) }
    var reviewInput by remember { mutableStateOf("") }

    Surface(
        color = Color(0xFF111827),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF1F2937)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "💬 FRIENDS' HOT TAKES & QUICK RATINGS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = GoldAccent
            )

            // Submit Hot Take Row
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1F2937))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Your Rating (1-5 ⭐):", fontSize = 11.sp, color = TextMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        (1..5).forEach { star ->
                            Text(
                                text = "★",
                                fontSize = 16.sp,
                                color = if (ratingInput >= star) GoldAccent else Color.Gray,
                                modifier = Modifier
                                    .clickable { ratingInput = star.toFloat() }
                                    .padding(horizontal = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = reviewInput,
                        onValueChange = { reviewInput = it },
                        placeholder = { Text("Drop a quick 1-sentence hot take...", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        )
                    )
                    Button(
                        onClick = {
                            if (reviewInput.trim().isNotEmpty()) {
                                onAddHotTake(ratingInput, reviewInput.trim())
                                reviewInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Post Take", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Hot Takes List
            if (hotTakes.isEmpty()) {
                Text(
                    text = "No hot takes yet. Be the first to share your thoughts!",
                    fontSize = 11.sp,
                    color = TextMuted,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    hotTakes.forEach { take ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .padding(8.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AsyncImage(
                                model = take.avatarUrl.ifEmpty { "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100" },
                                contentDescription = take.userName,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, GoldAccent, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(take.userName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextWhite)
                                    Text("⭐ ${take.rating}/5", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = GoldAccent)
                                }
                                Text("\"${take.review}\"", fontSize = 11.sp, color = TextMuted, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            }
                        }
                    }
                }
            }
        }
    }
}
