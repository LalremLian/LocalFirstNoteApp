package com.lalrem.noteapp.ui.workspace


import android.app.Activity
import android.net.Uri
import android.view.DragEvent
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lalrem.noteapp.domain.model.Note
import com.lalrem.noteapp.util.extensions.dragVisuals
import com.lalrem.noteapp.util.extensions.reorderDragHandler
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun WorkspaceGrid(
    uiState: WorkspaceUiState,
    onEvent: (WorkspaceEvent) -> Unit,
    onNoteClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()

    // State for reordering
    var draggedItemId by remember { mutableStateOf<String?>(null) }
    var dropTargetId by remember { mutableStateOf<String?>(null) }
    var isDropAfter by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var listForDisplay by remember(uiState.notes) { mutableStateOf(uiState.notes) }

    // Update display list when notes change from repository
    LaunchedEffect(uiState.notes) {
        if (draggedItemId == null) {
            listForDisplay = uiState.notes
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .assetDropTarget(null) { _, uri ->
                onEvent(WorkspaceEvent.OnAssetDrop(null, uri))
            }
    ) {
        if (uiState.notes.isEmpty()) {
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
                        modifier = Modifier.dragVisuals(isDragging, dragOffset),
                        dragHandleModifier = Modifier.reorderDragHandler(
                            itemId = note.id,
                            gridState = gridState,
                            dragOffsetProvider = { dragOffset },
                            onDragStart = { draggedItemId = note.id },
                            onDrag = { dragOffset = it },
                            onDropTargetUpdate = { targetId, isAfter ->
                                dropTargetId = targetId
                                isDropAfter = isAfter
                            },
                            onDragEnd = {
                                if (dropTargetId != null) {
                                    val fromIdx = uiState.notes.indexOfFirst { it.id == draggedItemId }
                                    var toIdx = uiState.notes.indexOfFirst { it.id == dropTargetId }

                                    if (fromIdx != -1 && toIdx != -1) {
                                        if (isDropAfter) toIdx++
                                        if (fromIdx < toIdx) toIdx--

                                        if (fromIdx != toIdx) {
                                            onEvent(WorkspaceEvent.OnMoveNote(fromIdx, toIdx))
                                        }
                                    }
                                }
                                draggedItemId = null
                                dropTargetId = null
                                dragOffset = Offset.Zero
                            }
                        ),
                        onTap = { onNoteClick(note.id) },
                        onDelete = { onEvent(WorkspaceEvent.OnDeleteNote(note.id)) },
                        onRotationUpdate = { assetId, degrees ->
                            onEvent(WorkspaceEvent.OnUpdateAssetRotation(note, assetId, degrees))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyWorkspacePrompt() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Your workspace is empty", style = MaterialTheme.typography.headlineSmall, color = Color.Gray)
        Text("Click the + button to start taking notes", style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
    }
}

fun Modifier.assetDropTarget(
    noteId: String?,
    onDrop: (String?, Uri) -> Unit
): Modifier = composed { composed {
    val view = LocalView.current
    val context = LocalContext.current
    val activity = context as? Activity

    // Update the listener whenever the noteId (selected tab) changes
    LaunchedEffect(view, noteId) {
        view.setOnDragListener { v, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    true
                }
                DragEvent.ACTION_DROP -> {
                    activity?.requestDragAndDropPermissions(event)
                    val uri = event.clipData?.getItemAt(0)?.uri
                    if (uri != null) {
                        onDrop(noteId, uri)
                        true
                    } else {
                        false
                    }
                }
                else -> true
            }
        }
    }
    this
} }

// Show existing notes
@Composable
fun NoteSelectorSheet(
    availableNotes: List<Note>,
    openNoteIds: List<String>,
    inputBuffer: String,
    showWorkspaceNotes: Boolean = true,
    onBufferChange: (String) -> Unit,
    onNoteSelect: (Note) -> Unit,
    onAdd: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding().imePadding()
    ) {
        Text(if (showWorkspaceNotes) "Open a Note" else "Create New Note", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        val selectable = availableNotes.filter { !openNoteIds.contains(it.id) }
        if (showWorkspaceNotes && selectable.isNotEmpty()) {
            Text("From Workspace", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                items(selectable) { note ->
                    ListItem(
                        headlineContent = { Text(note.content, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.clickable { onNoteSelect(note) },
                        leadingContent = { Icon(Icons.Default.Menu, null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text("Create New", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = inputBuffer,
            onValueChange = onBufferChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("What's on your mind?") },
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onAdd(inputBuffer) },
            enabled = inputBuffer.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("New Tab", fontWeight = FontWeight.Bold)
        }
    }
}

enum class DropIndicatorType {
    NONE, TOP, BOTTOM
}

@Composable
fun NoteCard(
    note: Note,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
    dropIndicator: DropIndicatorType = DropIndicatorType.NONE,
    onTap: () -> Unit,
    onDelete: () -> Unit,
    onRotationUpdate: (String, Float) -> Unit
) {
    Column(modifier = modifier) {
        if (dropIndicator == DropIndicatorType.TOP) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                    .padding(bottom = 2.dp)
            )
        }
        
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { onTap() },
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
            shape = RoundedCornerShape(20.dp),
            border = if (note.isDirty) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Drag to reorder",
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(24.dp)
                        .then(dragHandleModifier),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Column(modifier = Modifier.padding(16.dp).weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = note.content,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = SimpleDateFormat("dd/MM/yyyy h:mma", Locale.getDefault()).format(note.timestamp),
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                        if (note.isDirty) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
        
        if (dropIndicator == DropIndicatorType.BOTTOM) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                    .padding(top = 2.dp)
            )
        }
    }
}