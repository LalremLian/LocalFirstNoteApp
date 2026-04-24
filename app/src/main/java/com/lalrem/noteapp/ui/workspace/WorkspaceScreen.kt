package com.lalrem.noteapp.ui.workspace

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
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
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .assetDropTarget(selectedNoteId) { id, uri -> 
                        viewModel.handleAssetDrop(id, uri) 
                    }
            ) {
                if (selectedNoteId != null) {
                    val selectedNote = notes.find { it.id == selectedNoteId }
                    val drafts by viewModel.drafts.collectAsState()
                    selectedNote?.let { note ->
                        val initialDraft = drafts[note.id] ?: note.content
                        EditNoteScreen(
                            note = note,
                            initialContent = initialDraft,
                            viewModel = viewModel,
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










