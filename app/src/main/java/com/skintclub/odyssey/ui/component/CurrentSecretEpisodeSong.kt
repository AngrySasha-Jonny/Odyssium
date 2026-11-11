package com.skintclub.odyssey.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.skintclub.odyssey.club.secretEpisodeMap
import com.skintclub.odyssey.db.entities.Song
import com.skintclub.odyssey.playback.queues.odysseyEpisodeToMediaMetadata
import com.skintclub.odyssey.viewmodels.OdysseyViewModel

/**
 * For screens listing database [Song] rows (History, Stats): a function giving a played secret
 * episode as it's set up now. The row keeps the title and art the episode had when it was
 * played, but a rename, thumbnail or album set since (see SecretEpisodeDialogs.kt) should show
 * too. Anything that isn't a secret episode comes back unchanged.
 */
@Composable
fun rememberCurrentSecretEpisodeSong(viewModel: OdysseyViewModel): (Song) -> Song {
    val context = LocalContext.current
    val secretState by viewModel.secretEpisodesState.collectAsState()
    return remember(secretState) {
        val secretById = secretState.episodes.associateBy { it.id }
        val cache = HashMap<Song, Song>()
        fun(song: Song): Song {
            val secret = secretById[song.id] ?: return song
            return cache.getOrPut(song) {
                val metadata = odysseyEpisodeToMediaMetadata(context, secretEpisodeMap(secret))
                val albumName = metadata.album?.title
                song.copy(
                    song = song.song.copy(title = metadata.title, thumbnailUrl = metadata.thumbnailUrl),
                    artists = if (albumName == null) song.artists else song.artists.map { it.copy(name = albumName) },
                )
            }
        }
    }
}
