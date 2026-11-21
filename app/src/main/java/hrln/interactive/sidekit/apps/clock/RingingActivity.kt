package hrln.interactive.sidekit.apps.clock

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hrln.interactive.sidekit.R
import hrln.interactive.sidekit.design.action.SideAction
import hrln.interactive.sidekit.design.canvas.SidekitCanvas
import hrln.interactive.sidekit.design.components.*
import hrln.interactive.sidekit.design.theme.SidekitTheme
import hrln.interactive.sidekit.locale.AppLanguage

class RingingActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        val repo = Clock.repository(applicationContext)
        setContent {
            val data by repo.data.collectAsStateWithLifecycle()
            SidekitTheme {
                SidekitCanvas {
                    val ring = data?.ringing?.takeIf { System.currentTimeMillis() - it.at < 600000 }
                    SidekitScaffold(
                        title = ring?.title(LocalContext.current) ?: stringResource(R.string.clock_title),
                        actions = if (ring == null) {
                            listOf(SideAction("dismiss", stringResource(R.string.clock_close)) { finish() })
                        } else {
                            listOf(
                                SideAction("snooze", stringResource(R.string.clock_snooze)) {
                                    repo.snooze()
                                    finish()
                                },
                                SideAction("dismiss", stringResource(R.string.clock_dismiss)) {
                                    repo.dismiss()
                                    finish()
                                }
                            )
                        }
                    ) { padding ->
                        val list = rememberLazyListState()
                        SidekitList(padding, state = list) {
                            item { SidekitItem(stringResource(if (ring == null) R.string.clock_no_ringing else R.string.clock_snooze_hint), Modifier.listFade(list, 0)) }
                        }
                    }
                }
            }
        }
    }
}
