@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.zionhuang.music.ui.utils.reordering

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.checkScrollableContainerConstraints
import androidx.compose.foundation.clipScrollableContainer
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.layout.LazyLayout
import androidx.compose.foundation.lazy.layout.LazyLayoutMeasureScope
import androidx.compose.foundation.lazy.layout.lazyLayoutSemantics
import androidx.compose.foundation.overscroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReorderingLazyList(
    modifier: Modifier,
    reorderingState: ReorderingState,
    contentPadding: PaddingValues,
    reverseLayout: Boolean,
    isVertical: Boolean,
    flingBehavior: FlingBehavior,
    userScrollEnabled: Boolean,
    beyondBoundsItemCount: Int = 0,
    horizontalAlignment: Alignment.Horizontal? = null,
    verticalArrangement: Arrangement.Vertical? = null,
    verticalAlignment: Alignment.Vertical? = null,
    horizontalArrangement: Arrangement.Horizontal? = null,
    content: LazyListScope.() -> Unit,
) {
    val overscrollEffect = ScrollableDefaults.overscrollEffect()
    val itemProvider = rememberLazyListItemProvider(reorderingState.lazyListState, content)
    val semanticState =
        rememberLazyListSemanticState(reorderingState.lazyListState, itemProvider, reverseLayout, isVertical)
    val beyondBoundsInfo = reorderingState.lazyListBeyondBoundsInfo
    val scope = rememberCoroutineScope()
    val placementAnimator = remember(reorderingState.lazyListState, isVertical) {
        LazyListItemPlacementAnimator(scope, isVertical)
    }
    reorderingState.lazyListState.placementAnimator = placementAnimator

    val measurePolicy = rememberLazyListMeasurePolicy(
        itemProvider,
        reorderingState.lazyListState,
        beyondBoundsInfo,
        contentPadding,
        reverseLayout,
        isVertical,
        beyondBoundsItemCount,
        horizontalAlignment,
        verticalAlignment,
        horizontalArrangement,
        verticalArrangement,
        placementAnimator,
    )

    val orientation = if (isVertical) Orientation.Vertical else Orientation.Horizontal
    LazyLayout(
        modifier = modifier
            .then(reorderingState.lazyListState.remeasurementModifier)
            .then(reorderingState.lazyListState.awaitLayoutModifier)
            .lazyLayoutSemantics(
                itemProvider = itemProvider,
                state = semanticState,
                orientation = orientation,
                userScrollEnabled = userScrollEnabled
            )
            .clipScrollableContainer(orientation)
            .lazyListBeyondBoundsModifier(reorderingState.lazyListState, beyondBoundsInfo, reverseLayout, orientation)
            .overscroll(overscrollEffect)
            .scrollable(
                orientation = orientation,
                reverseDirection = ScrollableDefaults.reverseDirection(
                    LocalLayoutDirection.current,
                    orientation,
                    reverseLayout
                ),
                interactionSource = reorderingState.lazyListState.internalInteractionSource,
                flingBehavior = flingBehavior,
                state = reorderingState.lazyListState,
                overscrollEffect = overscrollEffect,
                enabled = userScrollEnabled
            ),
        prefetchState = reorderingState.lazyListState.prefetchState,
        measurePolicy = measurePolicy,
        itemProvider = itemProvider
    )
}

@ExperimentalFoundationApi
@Composable
private fun rememberLazyListMeasurePolicy(
    itemProvider: LazyListItemProvider,
    state: LazyListState,
    beyondBoundsInfo: LazyListBeyondBoundsInfo,
    contentPadding: PaddingValues,
    reverseLayout: Boolean,
    isVertical: Boolean,
    beyondBoundsItemCount: Int,
    horizontalAlignment: Alignment.Horizontal? = null,
    verticalAlignment: Alignment.Vertical? = null,
    horizontalArrangement: Arrangement.Horizontal? = null,
    verticalArrangement: Arrangement.Vertical? = null,
    placementAnimator: LazyListItemPlacementAnimator,
) = remember<LazyLayoutMeasureScope.(Constraints) -> MeasureResult>(
    state,
    beyondBoundsInfo,
    contentPadding,
    reverseLayout,
    isVertical,
    horizontalAlignment,
    verticalAlignment,
    horizontalArrangement,
    verticalArrangement,
    placementAnimator
) {
    { containerConstraints ->
        checkScrollableContainerConstraints(
            containerConstraints,
            if (isVertical) Orientation.Vertical else Orientation.Horizontal
        )

        // resolve content paddings
        val startPadding =
            if (isVertical) {
                contentPadding.calculateLeftPadding(layoutDirection).roundToPx()
            } else {
                // in horizontal configuration, padding is reversed by placeRelative
                contentPadding.calculateStartPadding(layoutDirection).roundToPx()
            }

        val endPadding =
            if (isVertical) {
                contentPadding.calculateRightPadding(layoutDirection).roundToPx()
            } else {
                // in horizontal configuration, padding is reversed by placeRelative
                contentPadding.calculateEndPadding(layoutDirection).roundToPx()
            }
        val topPadding = contentPadding.calculateTopPadding().roundToPx()
        val bottomPadding = contentPadding.calculateBottomPadding().roundToPx()
        val totalVerticalPadding = topPadding + bottomPadding
        val totalHorizontalPadding = startPadding + endPadding
        val totalMainAxisPadding = if (isVertical) totalVerticalPadding else totalHorizontalPadding
        val beforeContentPadding = when {
            isVertical && !reverseLayout -> topPadding
            isVertical && reverseLayout -> bottomPadding
            !isVertical && !reverseLayout -> startPadding
            else -> endPadding // !isVertical && reverseLayout
        }
        val afterContentPadding = totalMainAxisPadding - beforeContentPadding
        val contentConstraints =
            containerConstraints.offset(-totalHorizontalPadding, -totalVerticalPadding)

        // Update the state's cached Density
        state.density = this

        // this will update the scope used by the item composables
        itemProvider.itemScope.setMaxSize(
            width = contentConstraints.maxWidth,
            height = contentConstraints.maxHeight
        )

        val spaceBetweenItemsDp = if (isVertical) {
            requireNotNull(verticalArrangement).spacing
        } else {
            requireNotNull(horizontalArrangement).spacing
        }
        val spaceBetweenItems = spaceBetweenItemsDp.roundToPx()

        val itemsCount = itemProvider.itemCount

        // can be negative if the content padding is larger than the max size from constraints
        val mainAxisAvailableSize = if (isVertical) {
            containerConstraints.maxHeight - totalVerticalPadding
        } else {
            containerConstraints.maxWidth - totalHorizontalPadding
        }
        val visualItemOffset = if (!reverseLayout || mainAxisAvailableSize > 0) {
            IntOffset(startPadding, topPadding)
        } else {
            // When layout is reversed and paddings together take >100% of the available space,
            // layout size is coerced to 0 when positioning. To take that space into account,
            // we offset start padding by negative space between paddings.
            IntOffset(
                if (isVertical) startPadding else startPadding + mainAxisAvailableSize,
                if (isVertical) topPadding + mainAxisAvailableSize else topPadding
            )
        }

        val measuredItemProvider = LazyMeasuredItemProvider(
            contentConstraints,
            isVertical,
            itemProvider,
            this
        ) { index, key, placeables ->
            // we add spaceBetweenItems as an extra spacing for all items apart from the last one so
            // the lazy list measuring logic will take it into account.
            val spacing = if (index.value == itemsCount - 1) 0 else spaceBetweenItems
            LazyMeasuredItem(
                index = index.value,
                placeables = placeables,
                isVertical = isVertical,
                horizontalAlignment = horizontalAlignment,
                verticalAlignment = verticalAlignment,
                layoutDirection = layoutDirection,
                reverseLayout = reverseLayout,
                beforeContentPadding = beforeContentPadding,
                afterContentPadding = afterContentPadding,
                spacing = spacing,
                visualOffset = visualItemOffset,
                key = key,
                placementAnimator = placementAnimator
            )
        }
        state.premeasureConstraints = measuredItemProvider.childConstraints

        val firstVisibleItemIndex: DataIndex
        val firstVisibleScrollOffset: Int
        Snapshot.withoutReadObservation {
            firstVisibleItemIndex = DataIndex(state.firstVisibleItemIndex)
            firstVisibleScrollOffset = state.firstVisibleItemScrollOffset
        }

        measureLazyList(
            itemsCount = itemsCount,
            itemProvider = measuredItemProvider,
            mainAxisAvailableSize = mainAxisAvailableSize,
            beforeContentPadding = beforeContentPadding,
            afterContentPadding = afterContentPadding,
            spaceBetweenItems = spaceBetweenItems,
            firstVisibleItemIndex = firstVisibleItemIndex,
            firstVisibleItemScrollOffset = firstVisibleScrollOffset,
            scrollToBeConsumed = state.scrollToBeConsumed,
            constraints = contentConstraints,
            isVertical = isVertical,
            headerIndexes = itemProvider.headerIndexes,
            verticalArrangement = verticalArrangement,
            horizontalArrangement = horizontalArrangement,
            reverseLayout = reverseLayout,
            density = this,
            placementAnimator = placementAnimator,
            beyondBoundsInfo = beyondBoundsInfo,
            beyondBoundsItemCount = beyondBoundsItemCount,
            pinnedItems = state.pinnedItems,
            layout = { width, height, placement ->
                layout(
                    containerConstraints.constrainWidth(width + totalHorizontalPadding),
                    containerConstraints.constrainHeight(height + totalVerticalPadding),
                    emptyMap(),
                    placement
                )
            }
        ).also {
            state.applyMeasureResult(it)
        }
    }
}
