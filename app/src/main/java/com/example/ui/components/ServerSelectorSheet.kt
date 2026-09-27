package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CountryGroup
import com.example.data.model.SortOption
import com.example.data.model.VpnServer
import com.example.ui.theme.CrimsonCoral
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGlassSurface
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.TextPrimary
import com.example.ui.viewmodel.VpnUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSelectorSheet(
    state: VpnUiState,
    onDismiss: () -> Unit,
    onRefreshServers: () -> Unit,
    onSearchChanged: (String) -> Unit,
    onSortChanged: (SortOption) -> Unit,
    onToggleCountry: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleFavoritesSection: () -> Unit,
    onSelectServer: (VpnServer) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val config = LocalConfiguration.current
    val screenWidth = config.screenWidthDp.dp

    val mutedText = Color(0xFF94A3B8)
    val cardBorder = Color(0xFF1E293B)
    val amberGold = Color(0xFFF59E0B)

    // Filter Country Groups strictly in A to Z order, keeping max 3 best-ping nodes per country
    val query = state.searchQuery.trim().lowercase()
    val filteredGroups: List<CountryGroup> = remember(state.countryGroups, query) {
        state.countryGroups
            .mapNotNull { group ->
                if (query.isEmpty()) {
                    group
                } else {
                    val countryMatches = group.countryLong.lowercase().contains(query) ||
                        group.countryShort.lowercase().contains(query)
                    val matchingNodes = if (countryMatches) {
                        group.servers
                    } else {
                        group.servers.filter {
                            it.locationLabel.lowercase().contains(query) ||
                                it.hostName.lowercase().contains(query) ||
                                it.protocol.lowercase().contains(query) ||
                                it.cipher.lowercase().contains(query)
                        }
                    }
                    if (matchingNodes.isEmpty()) null else group.copy(servers = matchingNodes)
                }
            }
            .sortedBy { it.countryLong.lowercase() }
    }

    val favoriteGroups = remember(filteredGroups, state.favoriteCountries) {
        filteredGroups
            .filter { state.favoriteCountries.contains(it.countryShort.uppercase()) }
            .sortedBy { it.countryLong.lowercase() }
    }

    val totalNodes = filteredGroups.sumOf { it.servers.size }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DeepObsidian,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = (screenWidth * 0.042f).coerceIn(12.dp, 20.dp))
        ) {
            // 1. Top Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Select VPN Location",
                        color = TextPrimary,
                        fontSize = (screenWidth.value * 0.046f).coerceIn(16f, 20f).sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${filteredGroups.size} Countries • $totalNodes Verified Best-Ping Nodes",
                        color = EmeraldNeon,
                        fontSize = (screenWidth.value * 0.029f).coerceIn(10.5f, 13f).sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onRefreshServers) {
                        if (state.isLoadingServers) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = CyberCyan,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Servers",
                                tint = CyberCyan
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = mutedText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Search Input Bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "Search country or location...",
                        color = mutedText,
                        fontSize = (screenWidth.value * 0.034f).coerceIn(12f, 14f).sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = CyberCyan
                    )
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChanged("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = mutedText
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = cardBorder,
                    focusedContainerColor = CyberGlassSurface,
                    unfocusedContainerColor = CyberGlassSurface,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. 2-Button Sort Row (Lowest Ping #1->#3 First, Fastest Speed Second)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isPing = state.sortOption == SortOption.PING
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSortChanged(SortOption.PING) },
                    color = if (isPing) EmeraldNeon.copy(alpha = 0.18f) else CyberGlassSurface,
                    border = BorderStroke(1.2.dp, if (isPing) EmeraldNeon else cardBorder),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "📶 Best Ping (#1–#3)",
                            color = if (isPing) EmeraldNeon else mutedText,
                            fontSize = (screenWidth.value * 0.031f).coerceIn(11f, 13f).sp,
                            fontWeight = if (isPing) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                val isSpeed = state.sortOption == SortOption.SPEED
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSortChanged(SortOption.SPEED) },
                    color = if (isSpeed) CyberCyan.copy(alpha = 0.18f) else CyberGlassSurface,
                    border = BorderStroke(1.2.dp, if (isSpeed) CyberCyan else cardBorder),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "⚡ Fastest Speed",
                            color = if (isSpeed) CyberCyan else mutedText,
                            fontSize = (screenWidth.value * 0.031f).coerceIn(11f, 13f).sp,
                            fontWeight = if (isSpeed) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Single Vertical Scrolling List (One Country Below Another)
            if (filteredGroups.isEmpty() && !state.isLoadingServers) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No matching countries (< 400ms ping) found. Tap Refresh.",
                        color = mutedText,
                        fontSize = 13.5.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 28.dp)
                ) {
                    // SECTION 1: Pinned FAVORITE COUNTRIES Block (Always at the very top)
                    item(key = "section_favorites_header") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF111A2E)),
                            border = BorderStroke(1.dp, amberGold.copy(alpha = 0.45f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onToggleFavoritesSection() },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = amberGold,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "FAVORITE COUNTRIES (${favoriteGroups.size})",
                                            color = amberGold,
                                            fontSize = (screenWidth.value * 0.03f).coerceIn(11f, 13f).sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp,
                                            maxLines = 1
                                        )
                                    }

                                    Icon(
                                        imageVector = if (state.isFavoritesSectionExpanded) {
                                            Icons.Default.KeyboardArrowUp
                                        } else {
                                            Icons.Default.KeyboardArrowDown
                                        },
                                        contentDescription = null,
                                        tint = amberGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                if (state.isFavoritesSectionExpanded) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (favoriteGroups.isEmpty()) {
                                        Text(
                                            text = "Tap the ★ icon next to any country below to pin it here for quick access.",
                                            color = mutedText,
                                            fontSize = (screenWidth.value * 0.029f).coerceIn(11f, 12.5f).sp,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                                        )
                                    } else {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            favoriteGroups.forEach { group ->
                                                val isExpanded = state.expandedCountries.contains(group.countryShort.uppercase()) ||
                                                    query.isNotEmpty()
                                                VerticalCountryAccordionCard(
                                                    group = group,
                                                    isExpanded = isExpanded,
                                                    isFavorited = true,
                                                    selectedServer = state.selectedServer,
                                                    screenWidth = screenWidth,
                                                    onToggleCountry = { onToggleCountry(group.countryShort) },
                                                    onToggleFavorite = { onToggleFavorite(group.countryShort) },
                                                    onSelectServer = onSelectServer
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // SECTION 2: ALL COUNTRIES (A – Z) Header
                    item(key = "section_all_countries_header") {
                        Text(
                            text = "ALL COUNTRIES (A – Z)",
                            color = mutedText,
                            fontSize = (screenWidth.value * 0.028f).coerceIn(10.5f, 12f).sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                        )
                    }

                    // Vertical Country Cards (Strictly A to Z, One Below Another)
                    items(
                        items = filteredGroups,
                        key = { "country_card_${it.countryShort}" }
                    ) { group ->
                        val isExpanded = state.expandedCountries.contains(group.countryShort.uppercase()) ||
                            query.isNotEmpty()
                        val isFavorited = state.favoriteCountries.contains(group.countryShort.uppercase())

                        VerticalCountryAccordionCard(
                            group = group,
                            isExpanded = isExpanded,
                            isFavorited = isFavorited,
                            selectedServer = state.selectedServer,
                            screenWidth = screenWidth,
                            onToggleCountry = { onToggleCountry(group.countryShort) },
                            onToggleFavorite = { onToggleFavorite(group.countryShort) },
                            onSelectServer = onSelectServer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalCountryAccordionCard(
    group: CountryGroup,
    isExpanded: Boolean,
    isFavorited: Boolean,
    selectedServer: VpnServer?,
    screenWidth: androidx.compose.ui.unit.Dp,
    onToggleCountry: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSelectServer: (VpnServer) -> Unit
) {
    val mutedText = Color(0xFF94A3B8)
    val cardBorder = Color(0xFF1E293B)
    val amberGold = Color(0xFFF59E0B)

    val isActiveCountry = selectedServer?.countryShort.equals(group.countryShort, ignoreCase = true)
    val borderColor = if (isActiveCountry || group.isPinnedConnected) EmeraldNeon else cardBorder

    val flagBoxSize = (screenWidth * 0.095f).coerceIn(34.dp, 42.dp)
    val countryFontSize = (screenWidth.value * 0.038f).coerceIn(13.5f, 16f).sp
    val subFontSize = (screenWidth.value * 0.028f).coerceIn(10f, 12f).sp
    val badgeFontSize = (screenWidth.value * 0.027f).coerceIn(9.5f, 11.5f).sp

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CyberGlassSurface),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // A. Collapsed Country Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleCountry() }
                    .padding(
                        horizontal = (screenWidth * 0.034f).coerceIn(10.dp, 15.dp),
                        vertical = 12.dp
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Flag + Country Name & Best Ping Summary
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(flagBoxSize)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF161F33))
                            .border(1.dp, Color(0xFF263550), RoundedCornerShape(10.dp))
                    ) {
                        Text(
                            text = group.flagEmoji,
                            fontSize = (screenWidth.value * 0.048f).coerceIn(17f, 21f).sp
                        )
                    }

                    Spacer(modifier = Modifier.width((screenWidth * 0.025f).coerceIn(8.dp, 12.dp)))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = group.countryLong,
                                color = TextPrimary,
                                fontSize = countryFontSize,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = CyberCyan.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = group.countryShort,
                                    color = CyberCyan,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                            if (group.isPinnedConnected) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    color = EmeraldNeon.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "ACTIVE",
                                        color = EmeraldNeon,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "${group.servers.size} Locations • Best ${group.bestPing} ms",
                            color = mutedText,
                            fontSize = subFontSize,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Right: Best Ping Pill + Favorite Star + Expand/Collapse Chevron
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val bestPingColor = when {
                        group.bestPing < 80 -> EmeraldNeon
                        group.bestPing < 180 -> amberGold
                        else -> CrimsonCoral
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = bestPingColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, bestPingColor.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "${group.bestPing} ms",
                            color = bestPingColor,
                            fontSize = badgeFontSize,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorited) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (isFavorited) "Unfavorite" else "Favorite",
                            tint = if (isFavorited) amberGold else Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = mutedText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // B. Expanded Node List Inside Selected Country (Up to 3 Best-Ping Locations, Zero Raw IP!)
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(220)) + fadeIn(),
                exit = shrinkVertically(animationSpec = tween(220)) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    group.servers.forEachIndexed { index, server ->
                        val isSelected = selectedServer?.ip == server.ip
                        val pingColor = when {
                            server.ping < 80 -> EmeraldNeon
                            server.ping < 180 -> amberGold
                            else -> CrimsonCoral
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectServer(server) },
                            color = if (isSelected) EmeraldNeon.copy(alpha = 0.12f) else DeepObsidian.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, if (isSelected) EmeraldNeon else cardBorder),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left: Rank (#1, #2, #3) + Clean Location Name & Speed (NO RAW IP!)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        color = cardBorder,
                                        shape = CircleShape
                                    ) {
                                        Text(
                                            text = "#${index + 1}",
                                            color = CyberCyan,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = server.locationLabel.ifBlank { "${server.countryLong} • Location #${index + 1}" },
                                            color = TextPrimary,
                                            fontSize = (screenWidth.value * 0.035f).coerceIn(12.5f, 14.5f).sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "⚡ ${server.speedMbps} Mbps • ${server.protocol}:${server.port} • ${server.cipher}",
                                            color = mutedText,
                                            fontSize = (screenWidth.value * 0.027f).coerceIn(10f, 11.5f).sp,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Right: Ping Badge + Selected Checkmark
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = pingColor.copy(alpha = 0.14f),
                                        border = BorderStroke(1.dp, pingColor.copy(alpha = 0.35f))
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(pingColor)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${server.ping} ms",
                                                color = pingColor,
                                                fontSize = badgeFontSize,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }

                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = EmeraldNeon,
                                            modifier = Modifier.size(16.dp)
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
}
