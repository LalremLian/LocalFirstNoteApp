package com.lalrem.noteapp.ui.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.lalrem.noteapp.domain.model.Asset
import com.lalrem.noteapp.domain.model.Note
import com.lalrem.noteapp.ui.util.threeFingerRotation

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WorkspaceScreen(viewModel: WorkspaceViewModel) {
    val notes by viewModel.notes.collectAsState()
    val openNoteIds by viewModel.openNoteIds.collectAsState()
    val selectedNoteId by viewModel.selectedNoteId.collectAsState()
    val inputBuffer by viewModel.inputBuffer.collectAsState()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    
    val gridState = rememberLazyGridState()
    
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column(modifier = Modifier.statusBarsPadding()) {
                    // Only show large app bar if NO note is currently selected
                    if (selectedNoteId == null) {
                        LargeTopAppBar(
                            title = { 
                                Column {
                                    Text("Shared Workspace", fontWeight = FontWeight.ExtraBold)
                                    Text("Collaborate in real-time", style = MaterialTheme.typography.labelMedium)
                                }
                            },
                            colors = TopAppBarDefaults.largeTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )
                    }
                    
                    // Browser-Style Tab Bar - Fixed at top
                    if (openNoteIds.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LazyRow(
                                modifier = Modifier.weight(1f).height(48.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                items(openNoteIds) { noteId ->
                                    val note = notes.find { it.id == noteId }
                                    BrowserTab(
                                        title = note?.content?.take(15) ?: "Note",
                                        isSelected = selectedNoteId == noteId,
                                        onClick = { 
                                            // BLOCK switching if unsaved
                                            if (!hasUnsavedChanges || selectedNoteId == noteId) {
                                                viewModel.selectNote(noteId) 
                                            }
                                        },
                                        onClose = { 
                                            // BLOCK closing current tab if unsaved
                                            if (!hasUnsavedChanges || selectedNoteId != noteId) {
                                                viewModel.closeNote(noteId) 
                                            }
                                        }
                                    )
                                }
                            }
                            
                            IconButton(
                                // BLOCK opening new selector if unsaved
                                onClick = { if (!hasUnsavedChanges) showAddSheet = true }, 
                                enabled = !hasUnsavedChanges,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add, 
                                    "Open/New Note",
                                    tint = if (hasUnsavedChanges) Color.Gray.copy(alpha = 0.5f) else LocalContentColor.current
                                )
                            }
                        }
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            },
            floatingActionButton = {
                if (openNoteIds.isEmpty()) {
                    LargeFloatingActionButton(
                        onClick = { showAddSheet = true },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Note", Modifier.size(32.dp))
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (selectedNoteId != null) {
                    val selectedNote = notes.find { it.id == selectedNoteId }
                    val drafts by viewModel.drafts.collectAsState()
                    selectedNote?.let { note ->
                        val initialDraft = drafts[note.id] ?: note.content
                        EditNoteScreen(
                            note = note,
                            initialContent = initialDraft,
                            onDismiss = { viewModel.closeNote(note.id) },
                            onContentChange = { viewModel.updateDraft(note.id, it) },
                            onSave = { newContent ->
                                viewModel.updateNoteContent(note, newContent)
                            }
                        )
                    }
                } else if (notes.isEmpty()) {
                    EmptyWorkspacePrompt()
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 180.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(notes, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                onTap = { viewModel.openNote(note.id) },
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
                        onBufferChange = { viewModel.updateInputBuffer(it) },
                        onNoteSelect = { 
                            viewModel.openNote(it.id)
                            showAddSheet = false
                        },
                        onAdd = { 
                            viewModel.addNote(it)
                            showAddSheet = false 
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun BrowserTab(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .widthIn(min = 100.dp, max = 200.dp)
            .padding(horizontal = 2.dp)
            .clickable { onClick() },
        // Use a grey highlight for the selected tab
        color = if (isSelected) Color.LightGray.copy(alpha = 0.3f) else Color.Transparent,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (title.isBlank()) "Empty Note" else title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onClose, modifier = Modifier.size(16.dp)) {
                Icon(Icons.Default.Close, null, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNoteScreen(
    note: Note,
    initialContent: String,
    onDismiss: () -> Unit,
    onContentChange: (String) -> Unit,
    onSave: (String) -> Unit
) {
    key(note.id) {
        var content by remember { mutableStateOf(initialContent) }
        
        val isModified = content != note.content

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Edit Note", fontWeight = FontWeight.Bold)
                            if (isModified) {
                                Text(" • Unsaved", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        Button(
                            onClick = { onSave(content) },
                            enabled = isModified,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isModified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Text("SAVE", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp)
            ) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { 
                        content = it
                        onContentChange(it) // Notify persistence layer
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    textStyle = MaterialTheme.typography.headlineSmall,
                    placeholder = { Text("What's on your mind?") },
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
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

@Composable
fun NoteSelectorSheet(
    availableNotes: List<Note>,
    openNoteIds: List<String>,
    inputBuffer: String,
    onBufferChange: (String) -> Unit,
    onNoteSelect: (Note) -> Unit,
    onAdd: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding().imePadding()
    ) {
        Text("Open a Note", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        // Show existing notes not already in tabs
        val selectable = availableNotes.filter { !openNoteIds.contains(it.id) }
        if (selectable.isNotEmpty()) {
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

@Composable
fun NoteCard(
    note: Note,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onDelete: () -> Unit,
    onRotationUpdate: (String, Float) -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable { onTap() },
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
        shape = RoundedCornerShape(20.dp),
        border = if (note.isDirty) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
                
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            note.assets.forEach { asset ->
                AssetView(asset = asset, onRotationUpdate = onRotationUpdate)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(note.timestamp),
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

@Composable
fun AssetView(
    asset: Asset,
    onRotationUpdate: (String, Float) -> Unit
) {
    var hudVisible by remember { mutableStateOf(false) }
    var currentRotation by remember { mutableStateOf(asset.rotationDegrees) }

    Box(
        modifier = Modifier
            .size(100.dp)
            .threeFingerRotation(
                onRotationChanged = { 
                    currentRotation = it
                    onRotationUpdate(asset.id, it)
                },
                onHudVisibilityChanged = { hudVisible = it }
            )
            .rotate(currentRotation)
            .background(Color.LightGray, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = asset.url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        if (hudVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${currentRotation.toInt()}°",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        }
    }
}
