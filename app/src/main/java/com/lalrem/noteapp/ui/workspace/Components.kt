package com.lalrem.noteapp.ui.workspace

//noinspection SuspiciousImport
import android.R
import android.app.Activity
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.view.DragEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lalrem.noteapp.domain.model.Asset
import com.lalrem.noteapp.domain.model.Note
import com.lalrem.noteapp.ui.util.threeFingerRotation
import java.text.SimpleDateFormat
import java.util.Locale

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
    viewModel: WorkspaceViewModel,
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
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Show assets in the editor
                if (note.assets.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(note.assets) { asset ->
                            AssetView(
                                asset = asset,
                                onRotationUpdate = { assetId, degrees ->
                                    viewModel.updateAssetRotation(note, assetId, degrees)
                                }
                            )
                        }
                    }
                }

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
fun AssetView(
    asset: Asset,
    onRotationUpdate: (String, Float) -> Unit
) {
    var hudVisible by remember { mutableStateOf(false) }
    var currentRotation by remember { mutableStateOf(asset.rotationDegrees) }

    val imageBitmap = remember(asset.url) {
        if (asset.url.startsWith("data:")) {
            try {
                val base64Data = asset.url.substringAfter("base64,", "")
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (e: Exception) {
                Log.e("AssetView", "Failed to decode Base64 image", e)
                null
            }
        } else {
            null
        }
    }

    Box(
        modifier = Modifier
            .size(300.dp)
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

        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Note Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            AsyncImage(
                model = asset.url,
                contentDescription = "Note Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                error = painterResource(R.drawable.ic_menu_report_image),
                placeholder = painterResource(R.drawable.ic_menu_gallery)
            )
        }

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

@Composable
fun Modifier.assetDropTarget(
    noteId: String?,
    onDrop: (String?, Uri) -> Unit
): Modifier = composed {
    val view = LocalView.current
    val context = LocalContext.current
    val activity = context as? Activity

    // Update the listener whenever the noteId (selected tab) changes
    LaunchedEffect(view, noteId) {
        view.setOnDragListener { v, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    // Accept the drag if it contains a URI
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
}


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

        // Show existing notes not already in tabs
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