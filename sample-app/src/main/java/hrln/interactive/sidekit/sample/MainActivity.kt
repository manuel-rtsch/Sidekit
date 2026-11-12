package hrln.interactive.sidekit.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import hrln.interactive.sidekit.design.theme.SidekitTheme
import hrln.interactive.sidekit.sdk.SidekitContract
import hrln.interactive.sidekit.sdk.app.SidekitActivity
import hrln.interactive.sidekit.sdk.config.HostStatus
import hrln.interactive.sidekit.sdk.config.SidekitConfigClient
import hrln.interactive.sidekit.sdk.theme.rememberSidekitThemeConfig

/**
 * CI smoke consumer: builds against the Sidekit artifacts, uses the SDK shell, and reads the host
 * configuration the way a real Sidekit-native app does. With no Sidekit installed it falls back to
 * the local stores and still renders.
 */
class MainActivity : SidekitActivity() {
    @Composable
    override fun SidekitContent() {
        val context = LocalContext.current
        val status = remember { SidekitConfigClient.status(context) }
        val config = remember { SidekitConfigClient.read(context) }
        val theme = rememberSidekitThemeConfig(themeStore)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = SidekitTheme.spacing.headerHeight, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BasicText(
                "Sidekit SDK sample",
                style = SidekitTheme.typography.headline.copy(color = SidekitTheme.colors.light)
            )
            BasicText(
                when (status) {
                    HostStatus.Available -> "Host: Sidekit ${config?.hostVersion.orEmpty()}"
                    HostStatus.InstalledUnsupported -> "Host: installed, unsupported schema"
                    HostStatus.NotPermitted -> "Host: installed, not trusted for this app"
                    HostStatus.NotInstalled -> "Host: not installed, using local settings"
                },
                style = SidekitTheme.typography.body.copy(color = SidekitTheme.colors.light)
            )
            BasicText(
                "Case colour: ${theme.caseColour?.label ?: "local default"}",
                style = SidekitTheme.typography.body.copy(color = SidekitTheme.colors.light)
            )
            BasicText(
                "Compact layout: ${if (theme.compact) "on" else "off"}",
                style = SidekitTheme.typography.body.copy(color = SidekitTheme.colors.light)
            )
            BasicText(
                "Contract schema: ${config?.schema ?: SidekitContract.SCHEMA_VERSION}",
                style = SidekitTheme.typography.body.copy(color = SidekitTheme.colors.light)
            )
        }
    }
}
