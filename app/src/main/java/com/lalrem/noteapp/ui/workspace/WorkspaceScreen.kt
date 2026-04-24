package com.lalrem.noteapp.ui.workspace

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(viewModel: WorkspaceViewModel, navController: NavController) {
    val notes by viewModel.notes.collectAsState()
    val openNoteIds by viewModel.openNoteIds.collectAsState()
    val inputBuffer by viewModel.inputBuffer.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    
    val gridState = rememberLazyGridState()
    
    // State for reordering
    var draggedItemId by remember { mutableStateOf<String?>(null) }
    var dropTargetId by remember { mutableStateOf<String?>(null) }
    var isDropAfter by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var listForDisplay by remember(notes) { mutableStateOf(notes) }
    
    // Update display list when notes change from repository
    LaunchedEffect(notes) {
        if (draggedItemId == null) {
            listForDisplay = notes
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                LargeTopAppBar(
                    title = { 
                        Column {
                            Text("Local First Note App", fontWeight = FontWeight.ExtraBold)
                        }
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            floatingActionButton = {
                LargeFloatingActionButton(
                    onClick = { showAddSheet = true },
                    modifier = Modifier.size(54.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Note", Modifier.size(32.dp))
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .assetDropTarget(null) { _, uri -> 
                        viewModel.handleAssetDrop(null, uri)
                        navController.navigate("edit")
                    }
            ) {
                if (notes.isEmpty()) {
                    EmptyWorkspacePrompt()
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 180.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(listForDisplay.size, key = { listForDisplay[it].id }) { index ->
                            val note = listForDisplay[index]
                            val isDragging = draggedItemId == note.id
                            
                            val indicator = when {
                                dropTargetId == note.id && !isDropAfter -> DropIndicatorType.TOP
                                dropTargetId == note.id && isDropAfter -> DropIndicatorType.BOTTOM
                                else -> DropIndicatorType.NONE
                            }
                            
                            NoteCard(
                                note = note,
                                dropIndicator = indicator,
                                modifier = Modifier
                                    .graphicsLayer {
                                        if (isDragging) {
                                            translationX = dragOffset.x
                                            translationY = dragOffset.y
                                            alpha = 0.8f
                                            scaleX = 1.05f
                                            scaleY = 1.05f
                                            shadowElevation = 8.dp.toPx()
                                        }
                                    },
                                dragHandleModifier = Modifier.pointerInput(note.id) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggedItemId = note.id
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffset += dragAmount
                                            
                                            // Identify target index
                                            val layoutInfo = gridState.layoutInfo
                                            val draggedItemInfo = layoutInfo.visibleItemsInfo.find { it.key == note.id }
                                            if (draggedItemInfo != null) {
                                                val currentX = draggedItemInfo.offset.x + dragOffset.x + (draggedItemInfo.size.width / 2)
                                                val currentY = draggedItemInfo.offset.y + dragOffset.y + (draggedItemInfo.size.height / 2)
                                                
                                                val targetItem = layoutInfo.visibleItemsInfo.find { item ->
                                                    val left = item.offset.x
                                                    val right = item.offset.x + item.size.width
                                                    val top = item.offset.y
                                                    val bottom = item.offset.y + item.size.height
                                                    currentX >= left && currentX <= right && currentY >= top && currentY <= bottom
                                                }
                                                
                                                if (targetItem != null && targetItem.key != note.id) {
                                                    dropTargetId = targetItem.key as String
                                                    // Determine if top or bottom
                                                    val itemCenterY = targetItem.offset.y + targetItem.size.height / 2
                                                    isDropAfter = currentY > itemCenterY
                                                } else {
                                                    dropTargetId = null
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            if (dropTargetId != null) {
                                                val fromIndex = notes.indexOfFirst { it.id == draggedItemId }
                                                var toIndex = notes.indexOfFirst { it.id == dropTargetId }
                                                
                                                if (fromIndex != -1 && toIndex != -1) {
                                                    // Adjust toIndex if drop is after
                                                    if (isDropAfter) toIndex++
                                                    // If we're moving forward, the removal of the item at fromIndex will shift everything back
                                                    if (fromIndex < toIndex) toIndex--
                                                    
                                                    if (fromIndex != toIndex) {
                                                        viewModel.moveNote(fromIndex, toIndex)
                                                    }
                                                }
                                            }
                                            draggedItemId = null
                                            dropTargetId = null
                                            dragOffset = Offset.Zero
                                        },
                                        onDragCancel = {
                                            draggedItemId = null
                                            dropTargetId = null
                                            dragOffset = Offset.Zero
                                        }
                                    )
                                },
                                onTap = { 
                                    viewModel.openNote(note.id)
                                    navController.navigate("edit")
                                },
                                onDelete = { viewModel.deleteNote(note.id) },
                                onRotationUpdate = { assetId, degrees -> 
                                    viewModel.updateAssetRotation(note, assetId, degrees) 
                                }
                            )
                        }
                    }
                }
            }

            if (showAddSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showAddSheet = false },
                    dragHandle = { BottomSheetDefaults.DragHandle() },
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NoteSelectorSheet(
                        availableNotes = notes,
                        openNoteIds = openNoteIds,
                        inputBuffer = inputBuffer,
                        showWorkspaceNotes = false, // Only show "Create New"
                        onBufferChange = { viewModel.updateInputBuffer(it) },
                        onNoteSelect = { 
                            viewModel.openNote(it.id)
                            showAddSheet = false
                            navController.navigate("edit")
                        },
                        onAdd = { 
                            viewModel.addNote(it)
                            showAddSheet = false 
                            navController.navigate("edit")
                        }
                    )
                }
            }
        }
    }
}










