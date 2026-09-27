package yos.music.player.code

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import yos.music.player.data.libraries.PlayListLibrary
import yos.music.player.data.libraries.PlayListLibrary.applyEdits
import yos.music.player.data.libraries.SettingsLibrary
import yos.music.player.data.libraries.YosMediaItem

object VibezSmart {
    const val MOST = "Most Played"
    const val RECENT = "Recently Added"
    const val NEVER = "Never Played"

    fun sync(context: Context, library: List<YosMediaItem>) {
        try {
            if (library.isEmpty()) return
            val events = ListenStatsManager.statsEvents.value
            val countBy = HashMap<String, Int>()
            events.forEach { e -> e.uri?.let { countBy[it] = (countBy[it] ?: 0) + 1 } }
            val byUri = library.associateBy { it.uri?.toString() }
            val most = countBy.entries.sortedByDescending { it.value }
                .take(50).mapNotNull { byUri[it.key] }
            val recent = library.sortedByDescending { it.date }.take(50)
            val never = library.filter { m ->
                m.uri?.toString()?.let { countBy.containsKey(it) } != true
            }
            setSmart(MOST, most)
            setSmart(RECENT, recent)
            setSmart(NEVER, never)
        } catch (_: Throwable) {
        }
    }

    private fun setSmart(name: String, songs: List<YosMediaItem>) {
        val pl = PlayListLibrary.playList.firstOrNull { it.name == name }
            ?: run {
                PlayListLibrary.create(name)
                PlayListLibrary.playList.firstOrNull { it.name == name } ?: return
            }
        val uris = songs.mapNotNull { it.uri }
        pl.applyEdits(name = name, description = pl.description, coverUri = pl.coverUri, songs = uris)
    }
}

object VibezBlacklist {
    val blocked: Set<String>
        get() = SettingsLibrary.vibezBlacklist.orEmpty()
            .split('|').filter { it.isNotBlank() }.toSet()

    fun toggle(path: String) {
        val cur = blocked.toMutableSet()
        if (path in cur) cur.remove(path) else cur.add(path)
        SettingsLibrary.vibezBlacklist = cur.joinToString("|")
    }

    fun filter(list: List<YosMediaItem>): List<YosMediaItem> {
        val b = blocked
        return if (b.isEmpty()) list else list.filter { m -> b.none { m.path.startsWith(it) } }
    }
}

@Composable
fun VibezBlacklistDialog(onDismiss: () -> Unit) {
    var refresh by remember { mutableStateOf(0) }
    val library = MediaController.mainMusicList
    val folders = remember(refresh, library) {
        library.mapNotNull { it.path.substringBeforeLast('/').takeIf { p -> p.isNotBlank() } }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
    }
    LaunchedEffect(Unit) { refresh++ }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Blocked Folders") },
        text = {
            if (folders.isEmpty()) {
                Text("No folders found.")
            } else {
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(folders) { f ->
                        val isBlocked = f.key in VibezBlacklist.blocked
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = f.key.substringAfterLast('/') + "  (${f.value})",
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                VibezBlacklist.toggle(f.key)
                                refresh++
                            }) {
                                Text(if (isBlocked) "Unblock" else "Block")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
