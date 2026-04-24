package com.lalrem.noteapp.ui.util

import android.annotation.SuppressLint
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.atan2

@SuppressLint("ModifierFactoryUnreferencedReceiver")
fun Modifier.threeFingerRotation(
    onRotationChanged: (Float) -> Unit,
    onHudVisibilityChanged: (Boolean) -> Unit
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val activePointers = mutableMapOf<PointerId, Offset>()
        
        awaitFirstDown(requireUnconsumed = false)
        
        var isHudVisible = false

        do {
            val event: PointerEvent = awaitPointerEvent()
            
            // Update pointer positions
            event.changes.forEach { change ->
                if (change.pressed) {
                    activePointers[change.id] = change.position
                } else {
                    activePointers.remove(change.id)
                }
            }

            val pointerIds = activePointers.keys.toList()
            
            // Logic based on pointer count
            when (activePointers.size) {
                1 -> {
                    // Selection/Focus logic (Finger 1)
                    if (isHudVisible) {
                        isHudVisible = false
                        onHudVisibilityChanged(false)
                    }
                }
                2 -> {
                    // Rotation calculation (Finger 1 & 2)
                    val p1 = activePointers[pointerIds[0]]!!
                    val p2 = activePointers[pointerIds[1]]!!
                    
                    val angle = Math.toDegrees(
                        atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble())
                    ).toFloat()
                    
                    onRotationChanged(angle)
                    
                    if (isHudVisible) {
                        isHudVisible = false
                        onHudVisibilityChanged(false)
                    }
                }
                3 -> {
                    // HUD Trigger (Finger 3)
                    if (!isHudVisible) {
                        isHudVisible = true
                        onHudVisibilityChanged(true)
                    }
                    
                    // Still update rotation if Fingers 1 & 2 move
                    val p1 = activePointers[pointerIds[0]]!!
                    val p2 = activePointers[pointerIds[1]]!!
                    val angle = Math.toDegrees(
                        atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble())
                    ).toFloat()
                    onRotationChanged(angle)
                }
            }
            
        } while (activePointers.isNotEmpty())
        
        if (isHudVisible) {
            onHudVisibilityChanged(false)
        }
    }
}
