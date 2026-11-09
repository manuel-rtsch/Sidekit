package hrln.interactive.sidekit.apps.emulator

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EmulatorLibrary(onBack: () -> Unit) {
    val context = LocalContext.current
    val library = remember { GameLibrary(context) }
    val scope = rememberCoroutineScope()
    var games by remember { mutableStateOf(library.games()) }
    var message by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                importing = true
                message = withContext(Dispatchers.IO) {
                    runCatching { library.import(uri) }.exceptionOrNull()?.message
                }
                games = library.games()
                importing = false
            }
        }
    }
    SidekitScaffold(
        title = "Emulator",
        actions = listOf(
            SideAction("back", label = "Back", onClick = onBack),
            SideAction("import", label = if (importing) "Importing…" else "Add ROM", onClick = { if (!importing) picker.launch(arrayOf("*/*")) })
        )
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SidekitText("Game Boy · Game Boy Color · Game Boy Advance", maxLines = Int.MAX_VALUE) }
            item { SidekitText("Add your .gb, .gbc or .gba files. Game saves are stored on this device.", maxLines = Int.MAX_VALUE) }
            message?.let { item { SidekitText(it, maxLines = Int.MAX_VALUE) } }
            if (games.isEmpty()) item { SidekitText("No games yet. Choose Add ROM to get started.", maxLines = Int.MAX_VALUE) }
            items(games, key = { it.name }) { game ->
                SidekitLineButton(library.title(game), onClick = {
                    context.startActivity(Intent(context, EmulatorActivity::class.java).putExtra("game", game.name))
                })
            }
            item { SidekitText("D-pad: move · A/B: action · X: Select · Y: Start · L1/R1: shoulders. Controls can be remapped in-game.", maxLines = Int.MAX_VALUE) }
            item {
                SidekitLineButton("About mGBA & licenses", onClick = {
                    android.app.AlertDialog.Builder(context).setTitle("mGBA 0.10.5")
                        .setMessage("Source: https://github.com/mgba-emu/mgba/tree/26b7884bc25a5933960f3cdcd98bac1ae14d42e2\n\n" + context.assets.open("licenses/mgba-README.md").bufferedReader().use { it.readText() } + "\n\n" + listOf("mgba-MPL-2.0.txt", "mgba-blip_buf.txt", "mgba-inih.txt").joinToString("\n\n") { name -> context.assets.open("licenses/$name").bufferedReader().use { it.readText() } })
                        .setPositiveButton("Close", null).show()
                })
            }
        }
    }
}
