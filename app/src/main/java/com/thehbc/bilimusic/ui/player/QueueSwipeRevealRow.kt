package com.thehbc.bilimusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt

private enum class QueueSwipeRevealValue {
    Closed,
    DeleteRevealed
}

internal val QueueDeleteActionSize = 64.dp

@Composable
internal fun QueueSwipeRevealRow(
    isOpen: Boolean,
    enabled: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val deleteActionWidthPx = with(density) { QueueDeleteActionSize.toPx() }
    val anchors = remember(deleteActionWidthPx) {
        DraggableAnchors {
            QueueSwipeRevealValue.Closed at 0f
            QueueSwipeRevealValue.DeleteRevealed at -deleteActionWidthPx
        }
    }
    val swipeState = remember {
        AnchoredDraggableState(initialValue = QueueSwipeRevealValue.Closed)
    }
    val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
        state = swipeState,
        positionalThreshold = { distance -> distance * 0.35f }
    )

    SideEffect {
        swipeState.updateAnchors(anchors)
    }

    LaunchedEffect(isOpen) {
        swipeState.animateTo(
            if (isOpen) QueueSwipeRevealValue.DeleteRevealed else QueueSwipeRevealValue.Closed
        )
    }

    LaunchedEffect(swipeState.settledValue) {
        onOpenChange(swipeState.settledValue == QueueSwipeRevealValue.DeleteRevealed)
    }

    val offsetX = if (swipeState.offset.isNaN()) 0f else swipeState.requireOffset()

    Box {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(vertical = 4.dp)
                .size(QueueDeleteActionSize)
                .background(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .anchoredDraggable(
                    state = swipeState,
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    flingBehavior = flingBehavior
                )
        ) {
            content()
        }
    }
}
