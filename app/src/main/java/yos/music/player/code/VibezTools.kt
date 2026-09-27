package yos.music.player.code

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.PlayListLibrary
import yos.music.player.data.libraries.PlayListLibrary.applyEdits
import yos.music.player.data.libraries.YosMediaItem

object VibezTools {

    fun exportJson(): String {
        val o = JSONObject()
        val arr = JSONArray()
        PlayListLibrary.playList.forEach { pl ->
            val p = JSONObject()
            p.put("name", pl.name)
            p.put("desc", pl.description ?: "")
            p.put("cover", pl.coverUri ?: "")
            p.put("pinned", pl.isPinned)
            p.put("uris", JSONArray(pl.songDataList.map { it.toString() }))
            arr.put(p)
        }
        o.put("app", "Big Vibez")
        o.put("kind", "v3nja-backup")
        o.put("playlists", arr)
        return o.toString(2)
    }

    fun importJson(text: String): Int {
        val o = JSONObject(text)
        val arr = o.getJSONArray("playlists")
        var n = 0
        for (i in 0 until arr.length()) {
            try {
                val p = arr.getJSONObject(i)
                val name = p.getString("name")
                if (PlayListLibrary.playList.any { it.name == name }) continue
                PlayListLibrary.create(name)
                val pl = PlayListLibrary.playList.first { it.name == name }
                val uris = mutableListOf<Uri>()
                p.optJSONArray("uris")?.let { ua ->
                    for (j in 0 until ua.length()) uris.add(Uri.parse(ua.getString(j)))
                }
                pl.applyEdits(
                    name = name,
                    description = p.optString("desc", "").ifEmpty { null },
                    coverUri = p.optString("cover", "").ifEmpty { null },
                    songs = uris,
                )
                n++
            } catch (_: Throwable) {
            }
        }
        return n
    }

    fun findDupes(items: List<YosMediaItem>): List<List<YosMediaItem>> =
        items.groupBy {
            (it.title?.lowercase()?.trim() ?: "untitled") + "|" + (it.duration / 1000L)
        }.values.filter { it.size > 1 }.toList()

    fun deleteExtras(context: Context, group: List<YosMediaItem>) {
        group.drop(1).forEach { m ->
            m.uri?.let { u ->
                try {
                    context.contentResolver.delete(u, null, null)
                } catch (_: Throwable) {
                }
            }
        }
    }
}

@Composable
fun VibezDupesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val groups = remember { VibezTools.findDupes(MediaController.mainMusicList) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Duplicate Finder") },
        text = {
            if (groups.isEmpty()) {
                Text("No duplicates found. Library is clean.")
            } else {
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(groups) { g ->
                        Column {
                            Text("${g.first().title ?: "Unknown"} — ${g.size} copies")
                            TextButton(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    VibezTools.deleteExtras(context, g)
                                    MusicLibrary.scanMedia(context)
                                }
                                onDismiss()
                            }) { Text("Keep first, delete rest") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
