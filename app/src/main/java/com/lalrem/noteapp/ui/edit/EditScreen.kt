package com.lalrem.noteapp.ui.edit

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.LaunchedEffect
import com.lalrem.noteapp.ui.workspace.NoteSelectorSheet
import com.lalrem.noteapp.ui.workspace.assetDropTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(viewModel: EditViewModel, navController: NavController) {
    val notes by viewModel.notes.collectAsState()
    val openNoteIds by viewModel.openNoteIds.collectAsState()
    val selectedNoteId by viewModel.selectedNoteId.collectAsState()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsState()
    var inputBuffer by remember { mutableStateOf("") }
    var showAddSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        modifier = Modifier.weight(1f).height(48.dp).padding(start = 8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        items(openNoteIds) { noteId ->
                            val note = notes.find { it.id == noteId }
                            BrowserTab(
                                title = note?.content?.take(15) ?: "Note",
                                isSelected = selectedNoteId == noteId,
                                onClick = { 
                                    viewModel.selectNote(noteId) 
                                },
                                onClose = { 
                                    viewModel.closeNote(noteId)
                                }
                            )
                        }
                    }
                    
                    IconButton(
                        onClick = { showAddSheet = true }, 
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Add, 
                            "Open/New Note"
                        )
                    }
                }
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
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
                        onDismiss = { 
                            viewModel.closeNote(note.id)
                        },
                        onContentChange = { viewModel.updateDraft(note.id, it) },
                        onSave = { newContent ->
                            viewModel.updateNoteContent(note, newContent)
                        }
                    )
                }
            } 
        }
        
        LaunchedEffect(selectedNoteId) {
            if (selectedNoteId == null) {
                if (!navController.popBackStack()) {
                    navController.navigate("workspace") {
                        popUpTo("edit") { inclusive = true }
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
                    showWorkspaceNotes = true,
                    onBufferChange = { inputBuffer = it },
                    onNoteSelect = { 
                        viewModel.selectNote(it.id)
                        showAddSheet = false
                    },
                    onAdd = { 
                        viewModel.addNote(it) 
                        inputBuffer = ""
                        showAddSheet = false 
                    }
                )
            }
        }
    }
}
