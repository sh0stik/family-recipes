package recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import data.Links
import data.PickedPhoto
import data.rememberPhotoPicker
import data.resolveImageUrl
import errors.errorText
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import primary
import recipeappkmp.generated.resources.*
import white

/**
 * The one photo of a recipe, on the create and edit screens. Empty: "Upload photo" (the system
 * picker) and "Add link". Filled: the photo with "Replace" (the same two choices) and "Remove".
 * An upload starts as soon as a picture is picked, with a spinner over its thumbnail; its URL
 * replaces the photo only once the server has it, so a failed upload keeps what was there.
 * Nothing reaches the recipe until Save.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoSlotEditor(
    slot: PhotoSlot,
    onChange: (PhotoSlot) -> Unit,
    upload: suspend (ByteArray) -> Result<String>,
) {
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(slot)
    val change by rememberUpdatedState(onChange)
    var choosing by remember { mutableStateOf(false) }
    var linkOpen by remember { mutableStateOf(false) }
    var linkText by remember { mutableStateOf("") }

    val pick = rememberPhotoPicker { picked ->
        when (picked) {
            is PickedPhoto.Ready -> {
                choosing = false
                change(current.uploadStarted(picked.jpeg))
                scope.launch {
                    val result = upload(picked.jpeg)
                    change(current.uploadFinished(result))
                }
            }
            PickedPhoto.Unreadable -> change(current.unreadable())
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.photo_section), style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)

        val uploading = slot.uploading
        val photo = slot.photo
        when {
            uploading != null -> PhotoCard(model = uploading, caption = stringResource(Res.string.uploading), busy = true)
            photo != null -> PhotoCard(model = resolveImageUrl(photo), caption = photoCaption(photo), busy = false) {
                TextButton(onClick = { choosing = !choosing; linkOpen = false }) {
                    Text(stringResource(Res.string.replace_photo), color = primary)
                }
                TextButton(onClick = { change(slot.without(photo)); choosing = false; linkOpen = false }) {
                    Text(stringResource(Res.string.remove_photo), color = Color.Red)
                }
            }
        }

        if (uploading == null && (photo == null || choosing)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pick != null) {
                    Button(
                        onClick = { linkOpen = false; pick() },
                        colors = ButtonDefaults.buttonColors(backgroundColor = primary, contentColor = white),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text(stringResource(Res.string.upload_photo))
                    }
                }
                OutlinedButton(onClick = { linkOpen = !linkOpen }, shape = RoundedCornerShape(20.dp)) {
                    Text(stringResource(Res.string.add_photo_link), color = primary)
                }
            }
        }

        if (linkOpen && uploading == null) {
            LinkInput(
                value = linkText,
                onValueChange = { linkText = it },
                label = stringResource(Res.string.photo_url_label),
                addLabel = stringResource(Res.string.add_photo_link),
                onAdd = {
                    slot.withLink(linkText)?.let {
                        change(it)
                        linkText = ""
                        linkOpen = false
                        choosing = false
                    }
                },
            )
        }

        if (slot.extras.isNotEmpty()) {
            Text(stringResource(Res.string.photo_extras_hint), style = MaterialTheme.typography.caption, color = Color.Gray)
            for (extra in slot.extras) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AsyncImage(
                        model = resolveImageUrl(extra),
                        contentDescription = stringResource(Res.string.photo_description),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).background(Color.LightGray),
                    )
                    Text(photoCaption(extra), style = MaterialTheme.typography.caption, color = Color.Gray,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = { change(slot.without(extra)) }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.remove_photo), tint = Color.Red)
                    }
                }
            }
        }

        slot.error?.let {
            Text(errorText(it), color = MaterialTheme.colors.error, style = MaterialTheme.typography.body2)
        }
    }
}

@Composable
private fun PhotoCard(model: Any?, caption: String, busy: Boolean, actions: @Composable () -> Unit = {}) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), elevation = 2.dp) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = model,
                    contentDescription = stringResource(Res.string.photo_description),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(88.dp).clip(RoundedCornerShape(8.dp)).background(Color.LightGray),
                )
                if (busy) CircularProgressIndicator(color = primary, modifier = Modifier.size(32.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(caption, style = MaterialTheme.typography.caption, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row { actions() }
            }
        }
    }
}

/** "Uploaded photo" for a file on our server; the link itself, shortened, for a link. */
@Composable
private fun photoCaption(url: String): String =
    if (url.startsWith("/uploads/")) stringResource(Res.string.uploaded_photo)
    else url.substringAfter("://").let { if (it.length > 60) it.take(57) + "..." else it }

@Composable
private fun LinkInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    addLabel: String,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = primary,
                focusedLabelColor = primary,
                cursorColor = primary,
            ),
        )
        IconButton(onClick = onAdd, enabled = Links.isWebLink(value)) {
            Icon(Icons.Default.Add, contentDescription = addLabel, tint = if (Links.isWebLink(value)) primary else Color.Gray)
        }
    }
}

/** The recipe's video links, on the create and edit screens. Links only. */
@Composable
fun VideoLinksEditor(videos: List<String>, onChange: (List<String>) -> Unit) {
    var newLink by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.videos_links_section), style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
        LinkInput(
            value = newLink,
            onValueChange = { newLink = it },
            label = stringResource(Res.string.video_url_label),
            addLabel = stringResource(Res.string.add_video),
            onAdd = {
                val link = newLink.trim()
                if (Links.isWebLink(link) && link !in videos) {
                    onChange(videos + link)
                    newLink = ""
                }
            },
        )
        for (url in videos) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), elevation = 2.dp) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = url,
                        style = MaterialTheme.typography.caption,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onChange(videos.filter { it != url }) }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.delete), tint = Color.Red)
                    }
                }
            }
        }
    }
}
