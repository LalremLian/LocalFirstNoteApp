package com.lalrem.noteapp.ui.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(viewModel: WorkspaceViewModel, navController: NavController) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                WorkspaceEffect.NavigateToEdit -> navController.navigate("edit")
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Text("Local First Note App",
                    Modifier.padding(16.dp),
                    fontWeight = FontWeight.ExtraBold)
            },
            floatingActionButton = {
                LargeFloatingActionButton(
                    onClick = { viewModel.onEvent(WorkspaceEvent.OnToggleAddSheet(true)) },
                    modifier = Modifier.size(54.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Note", Modifier.size(32.dp))
                }
            }
        ) { padding ->
            WorkspaceGrid(
                uiState = uiState,
                onEvent = { viewModel.onEvent(it) },
                onNoteClick = { noteId ->
                    viewModel.onEvent(WorkspaceEvent.OnOpenNote(noteId))
                },
                modifier = Modifier.padding(padding)
            )

            if (uiState.showAddSheet) {
                ModalBottomSheet(
                    onDismissRequest = { viewModel.onEvent(WorkspaceEvent.OnToggleAddSheet(false)) },
                    dragHandle = { BottomSheetDefaults.DragHandle() },
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NoteSelectorSheet(
                        availableNotes = uiState.notes,
                        openNoteIds = uiState.openNoteIds,
                        inputBuffer = uiState.inputBuffer,
                        showWorkspaceNotes = false,
                        onBufferChange = { viewModel.onEvent(WorkspaceEvent.OnInputBufferChange(it)) },
                        onNoteSelect = { 
                            viewModel.onEvent(WorkspaceEvent.OnOpenNote(it.id))
                            viewModel.onEvent(WorkspaceEvent.OnToggleAddSheet(false))
                            navController.navigate("edit")
                        },
                        onAdd = { 
                            viewModel.onEvent(WorkspaceEvent.OnAddNote(it))
                            viewModel.onEvent(WorkspaceEvent.OnToggleAddSheet(false))
                            navController.navigate("edit")
                        }
                    )
                }
            }
        }
    }
}


