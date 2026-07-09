package com.thehbc.bilimusic.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thehbc.bilimusic.data.model.LyricLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LyricsView(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (lyrics.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "暂无歌词",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Auto-scroll to current index when it changes
    LaunchedEffect(currentIndex) {
        if (currentIndex in lyrics.indices) {
            coroutineScope.launch {
                // Wait until viewport height is measured
                var vh = listState.layoutInfo.viewportSize.height
                while (vh == 0) {
                    delay(50)
                    vh = listState.layoutInfo.viewportSize.height
                }
                // Center the item perfectly
                // scrollToItem aligns to top. We offset by half viewport minus a little for the item's own height.
                val offset = -(vh / 2) + 100 // roughly half the item height
                listState.animateScrollToItem(currentIndex, scrollOffset = offset)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        // Large padding so first and last items can reach the center
        contentPadding = PaddingValues(vertical = 400.dp), 
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(lyrics) { index, lyric ->
            val isActive = index == currentIndex
            
            val scale by animateFloatAsState(
                targetValue = if (isActive) 1.1f else 0.9f,
                animationSpec = tween(durationMillis = 400),
                label = "LyricScale"
            )
            val alpha by animateFloatAsState(
                targetValue = if (isActive) 1f else 0.4f,
                animationSpec = tween(durationMillis = 400),
                label = "LyricAlpha"
            )
            val color by animateColorAsState(
                targetValue = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface,
                animationSpec = tween(durationMillis = 400),
                label = "LyricColor"
            )

            Text(
                text = lyric.text,
                style = if (isActive) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = color,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(alpha)
                    .scale(scale)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSeekTo(lyric.timestampMs) }
                    .padding(horizontal = 24.dp)
            )
        }
    }
}
