package com.lalrem.noteapp.util.extensions

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

fun Modifier.dragVisuals(
    isDragging: Boolean,
    dragOffset: Offset
): Modifier = this.graphicsLayer {
    if (isDragging) {
        translationX = dragOffset.x
        translationY = dragOffset.y
        alpha = 0.8f
        scaleX = 1.05f
        scaleY = 1.05f
        shadowElevation = 8.dp.toPx()
    }
}

fun Modifier.reorderDragHandler(
    itemId: String,
    gridState: LazyGridState,
    dragOffsetProvider: () -> Offset,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDropTargetUpdate: (targetId: String?, isAfter: Boolean) -> Unit,
    onDragEnd: () -> Unit
): Modifier = this.pointerInput(itemId) {
    detectDragGestures(
        onDragStart = { onDragStart() },
        onDrag = { change, dragAmount ->
            change.consume()
            val newOffset = dragOffsetProvider() + dragAmount
            onDrag(newOffset)
            
            val layoutInfo = gridState.layoutInfo
            val draggedItemInfo = layoutInfo.visibleItemsInfo.find { it.key == itemId }
            if (draggedItemInfo != null) {
                val currentX = draggedItemInfo.offset.x + newOffset.x + (draggedItemInfo.size.width / 2)
                val currentY = draggedItemInfo.offset.y + newOffset.y + (draggedItemInfo.size.height / 2)
                
                val targetItem = layoutInfo.visibleItemsInfo.find { item ->
                    val left = item.offset.x
                    val right = item.offset.x + item.size.width
                    val top = item.offset.y
                    val bottom = item.offset.y + item.size.height
                    currentX >= left && currentX <= right && currentY >= top && currentY <= bottom
                }
                
                if (targetItem != null && targetItem.key != itemId) {
                    val targetId = targetItem.key as String
                    val itemCenterY = targetItem.offset.y + targetItem.size.height / 2
                    val isAfter = currentY > itemCenterY
                    onDropTargetUpdate(targetId, isAfter)
                } else {
                    onDropTargetUpdate(null, false)
                }
            }
        },
        onDragEnd = onDragEnd,
        onDragCancel = onDragEnd
    )
}
