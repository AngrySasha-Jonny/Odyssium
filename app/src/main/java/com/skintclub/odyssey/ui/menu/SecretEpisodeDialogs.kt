package com.skintclub.odyssey.ui.menu

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.skintclub.odyssey.R
import com.skintclub.odyssey.club.SecretEpisode
import com.skintclub.odyssey.club.SecretEpisodeThumbnails
import com.skintclub.odyssey.club.isSecretEpisodeId
import com.skintclub.odyssey.club.secretEpisodeTitle
import com.skintclub.odyssey.ui.component.DefaultDialog
import com.skintclub.odyssey.ui.component.ListDialog
import com.skintclub.odyssey.ui.component.TextFieldDialog
import com.skintclub.odyssey.viewmodels.OdysseyViewModel
import kotlinx.coroutines.launch

/*
 * The secret-episode-only entries of the episode menu (see OdysseyEpisodeMenu): rename, set a
 * thumbnail, and put the episode in an album. All three only change what's stored on this
 * device (see com.skintclub.odyssey.club.SecretEpisodesStore).
 */

/** Renames [episode]. Clearing the name goes back to the one read from its file name. */
@Composable
fun RenameSecretEpisodeDialog(
    viewModel: OdysseyViewModel,
    episode: SecretEpisode,
    onDismiss: () -> Unit,
) {
    val current = episode.displayTitle
    TextFieldDialog(
        icon = { Icon(painter = painterResource(R.drawable.edit), contentDescription = null) },
        title = { Text(stringResource(R.string.rename)) },
        initialTextFieldValue = TextFieldValue(current, TextRange(0, current.length)),
        placeholder = { Text(secretEpisodeTitle(episode.path)) },
        isInputValid = { true },
        onDone = { name ->
            // Typing the file-name title back in is the same as clearing it.
            val title = name.trim().takeUnless { it.isEmpty() || it == secretEpisodeTitle(episode.path) }
            viewModel.setSecretEpisodeDetails(episode.id, title, episode.thumbnail, episode.album)
        },
        onDismiss = onDismiss,
    )
}

/** Shows [episode]'s thumbnail with "Choose image" (the system photo picker) and "Remove".
 * Nothing is saved until "Save"; an image picked and then cancelled is deleted again. */
@Composable
fun SecretEpisodeThumbnailDialog(
    viewModel: OdysseyViewModel,
    episode: SecretEpisode,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var thumbnail by remember { mutableStateOf(episode.thumbnail) }
    var importing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    /** Drops [thumbnail] if it was picked in this dialog (the saved one stays until "Save"). */
    fun discardPicked() {
        val picked = thumbnail
        if (picked != null && picked != episode.thumbnail) {
            scope.launch { SecretEpisodeThumbnails.delete(context, picked) }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        failed = false
        scope.launch {
            val imported = SecretEpisodeThumbnails.import(context, uri)
            importing = false
            if (imported == null) {
                failed = true
            } else {
                discardPicked()
                thumbnail = imported
            }
        }
    }

    val cancel = {
        discardPicked()
        onDismiss()
    }

    DefaultDialog(
        onDismiss = cancel,
        icon = { Icon(painter = painterResource(R.drawable.image), contentDescription = null) },
        title = { Text(stringResource(R.string.thumbnail)) },
        buttons = {
            if (thumbnail != null) {
                TextButton(
                    onClick = {
                        discardPicked()
                        thumbnail = null
                    },
                ) { Text(stringResource(R.string.remove_thumbnail)) }
            }
            TextButton(onClick = cancel) { Text(stringResource(android.R.string.cancel)) }
            TextButton(
                enabled = !importing && thumbnail != episode.thumbnail,
                onClick = {
                    viewModel.setSecretEpisodeDetails(episode.id, episode.title, thumbnail, episode.album)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.save)) }
        },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = !importing) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
        ) {
            when {
                importing -> CircularProgressIndicator()
                thumbnail != null -> AsyncImage(
                    model = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(200.dp),
                )
                else -> Icon(
                    painter = painterResource(R.drawable.add),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = if (failed) stringResource(R.string.thumbnail_import_failed) else stringResource(R.string.tap_to_choose_image),
            style = MaterialTheme.typography.bodyMedium,
            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** Puts [episode] in an album: a new one by name, or any album already in the Albums screen -
 * albums of secret episodes first, then the club's. It still shows in "Secret Episodes" too.
 * "Remove from album" takes it back out. */
@Composable
fun SecretEpisodeAlbumDialog(
    viewModel: OdysseyViewModel,
    episode: SecretEpisode,
    onDismiss: () -> Unit,
) {
    val albums by viewModel.albums.collectAsState()
    val albumNames = remember(albums) {
        val (ownAlbums, clubAlbums) = albums
            .partition { album -> album.episodes.all { isSecretEpisodeId(it["id"] as? String) } }
        ownAlbums.map { it.name }.sorted() + clubAlbums.map { it.name }
    }
    var showNewAlbumDialog by remember { mutableStateOf(false) }

    fun choose(album: String?) {
        viewModel.setSecretEpisodeDetails(episode.id, episode.title, episode.thumbnail, album)
        onDismiss()
    }

    if (showNewAlbumDialog) {
        TextFieldDialog(
            icon = { Icon(painter = painterResource(R.drawable.album), contentDescription = null) },
            title = { Text(stringResource(R.string.new_album)) },
            placeholder = { Text(stringResource(R.string.album_name)) },
            isInputValid = { it.isNotBlank() },
            onDone = { choose(it.trim()) },
            onDismiss = { showNewAlbumDialog = false },
        )
        return
    }

    ListDialog(onDismiss = onDismiss) {
        item {
            AlbumChoiceRow(icon = R.drawable.add, text = stringResource(R.string.new_album)) { showNewAlbumDialog = true }
        }
        if (episode.album != null) {
            item {
                AlbumChoiceRow(icon = R.drawable.close, text = stringResource(R.string.remove_from_album)) { choose(null) }
            }
        }
        items(albumNames) { name ->
            AlbumChoiceRow(
                icon = if (name == episode.album) R.drawable.done else null,
                text = name,
            ) { choose(name) }
        }
    }
}

@Composable
private fun AlbumChoiceRow(icon: Int?, text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(modifier = Modifier.padding(end = 12.dp).size(24.dp)) {
            if (icon != null) Icon(painter = painterResource(icon), contentDescription = null)
        }
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}
