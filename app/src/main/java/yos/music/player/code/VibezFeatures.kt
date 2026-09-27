package yos.music.player.code

import android.content.Context
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.PlayList
import yos.music.player.data.libraries.PlayListLibrary
import yos.music.player.data.libraries.PlayListLibrary.addMusic
import yos.music.player.data.libraries.YosMediaItem

/* ================= V3NJA EQUALIZER ================= */

object VibezEq {
    private var eq: Equalizer? = null
    var enabled by mutableStateOf(false)
    var sessionId by mutableStateOf(0)
    var levels by mutableStateOf(mapOf<Int, Float>())

    fun attach(sid: Int) {
        sessionId = sid
        try {
            eq?.release()
            val e = Equalizer(0, sid)
            eq = e
            e.enabled = enabled
            levels.ifEmpty {
                val n = e.numberOfBands.toInt()
                (0 until n).associateWith { 0f }
            }.also { levels = it }
        } catch (_: Throwable) {
            eq = null
        }
    }

    fun setEnabled(b: Boolean) {
        enabled = b
        try {
            eq?.enabled = b
        } catch (_: Throwable) {
        }
    }

    fun bandCount(): Int = try {
        eq?.numberOfBands?.toInt() ?: 0
    } catch (_: Throwable) {
        0
    }

    fun centerKhz(band: Int): Int = try {
        eq?.getCenterFreq(band.toShort())?.div(1000) ?: 0
    } catch (_: Throwable) {
        0
    }

    private fun range(): Pair<Short, Short> = try {
        val r = eq!!.bandLevelRange
        r[0] to r[1]
    } catch (_: Throwable) {
        -1500 to 1500
    }

    fun setBand(band: Int, v: Float) {
        val (lo, hi) = range()
        val level = (v * hi).toInt().toShort().coerceIn(lo, hi)
        levels = levels + (band to v)
        try {
            eq?.setBandLevel(band.toShort(), level)
        } catch (_: Throwable) {
        }
    }

    fun preset(pattern: List<Float>) {
        for (i in 0 until bandCount().coerceAtMost(pattern.size)) setBand(i, pattern[i])
    }
}

/* ================= V3NJA MULTI-SELECT ================= */

object VibezSelection {
    var active by mutableStateOf(false)
    var picked by mutableStateOf(setOf<Uri>())

    fun enter(u: Uri?) {
        active = true
        if (u != null) picked = picked + u
    }

    fun toggle(u: Uri?) {
        if (u == null) return
        picked = if (picked.contains(u)) picked - u else picked + u
        if (picked.isEmpty()) exit()
    }

    fun exit() {
        active = false
        picked = setOf()
    }
}

@Composable
fun VibezSelectionBar(allItems: List<YosMediaItem>) {
    if (!VibezSelection.active) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPicker by remember { mutableStateOf(false) }
    val deleteLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            scope.launch(Dispatchers.IO) { MusicLibrary.scanMedia(context) }
            VibezSelection.exit()
        }
    val selectedItems = allItems.filter { VibezSelection.picked.contains(it.uri) }

    Dialog(
        onDismissRequest = { VibezSelection.exit() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                tonalElevation = 12.dp,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(10.dp),
            ) {
                Column(modifier = Modifier.padding(vertical = 10.dp)) {
                    Text(
                        text = "${VibezSelection.picked.size} selected",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        TextButton(onClick = {
                            val first = selectedItems.firstOrNull() ?: return@TextButton
                            scope.launch(Dispatchers.IO) {
                                MediaController.prepare(first, selectedItems)
                            }
                            VibezSelection.exit()
                        }) { Text("Play") }
                        TextButton(onClick = { showPicker = true }) { Text("Playlist") }
                        TextButton(onClick = {
                            val uris = VibezSelection.picked.toList()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                try {
                                    val req = MediaStore.createDeleteRequest(context.contentResolver, uris)
                                    deleteLauncher.launch(
                                        IntentSenderRequest.Builder(req.intentSender).build()
                                    )
                                    return@TextButton
                                } catch (_: Throwable) {
                                }
                            }
                            uris.forEach { u ->
                                try {
                                    context.contentResolver.delete(u, null, null)
                                } catch (_: Throwable) {
                                }
                            }
                            scope.launch(Dispatchers.IO) { MusicLibrary.scanMedia(context) }
                            VibezSelection.exit()
                        }) { Text("Delete") }
                        TextButton(onClick = { VibezSelection.exit() }) { Text("Cancel") }
                    }
                }
            }
        }
    }

    if (showPicker) {
        VibezAddToPlaylistDialog(items = selectedItems) { showPicker = false; VibezSelection.exit() }
    }
}

@Composable
fun VibezAddToPlaylistDialog(items: List<YosMediaItem>, onDone: () -> Unit) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Add to Playlist") },
        text = {
            Column {
                TextField(value = newName, onValueChange = { newName = it }, placeholder = { Text("New playlist name") })
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                    items(PlayListLibrary.playList) { pl ->
                        TextButton(onClick = {
                            items.forEach { m -> pl.addMusic(m) }
                            onDone()
                        }) { Text(pl.name) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = newName.trim()
                if (n.isNotEmpty()) {
                    PlayListLibrary.create(n)
                    val pl = PlayListLibrary.playList.firstOrNull { it.name == n }
                    if (pl != null) items.forEach { m -> pl.addMusic(m) }
                }
                onDone()
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

/* ================= V3NJA EQ SHEET ================= */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibezEqualizerSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Equalizer", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("V3NJA sound lab", fontSize = 13.sp)
                }
                Switch(checked = VibezEq.enabled, onCheckedChange = { VibezEq.setEnabled(it) })
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "Flat" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
                    "Bass" to listOf(1f, .8f, .6f, .3f, 0f, 0f, 0f, 0f, 0f, 0f),
                    "Vocal" to listOf(-.2f, 0f, .4f, .7f, .7f, .5f, .2f, 0f, 0f, 0f),
                    "V-Shape" to listOf(.9f, .6f, .2f, -.2f, -.2f, -.2f, .2f, .5f, .8f, 1f),
                ).forEach { (name, p) ->
                    TextButton(onClick = { VibezEq.preset(p) }) { Text(name, fontSize = 13.sp) }
                }
            }
            val n = VibezEq.bandCount()
            if (n == 0) {
                Text("Equalizer not available on this device.")
            } else {
                for (b in 0 until n) {
                    val v = VibezEq.levels[b] ?: 0f
                    Column {
                        Text("${VibezEq.centerKhz(b)} Hz", fontSize = 12.sp)
                        Slider(value = v, onValueChange = { VibezEq.setBand(b, it) }, valueRange = -1f..1f)
                    }
                }
            }
        }
    }
}

@Composable
fun VibezCheckIcon(selected: Boolean) {
    Icon(
        imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
        contentDescription = null,
        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
    )
}
