package app.aaps.ui.compose.overview.boost

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.sync.NsClient

/**
 * The plugin pages that get a tab, device first. Sensitivity, smoothing, constraints and the other sync
 * plugins are opened rarely and stay in the drawer and search, so the strip stays short.
 */
private val PANEL_TYPE_ORDER = listOf(PluginType.PUMP, PluginType.APS, PluginType.LOOP, PluginType.BGSOURCE, PluginType.SYNC)

/** The plugins with a page of their own, by the rule the quick-launch list and search use. Of the sync plugins, only NSClient. */
fun boostPanelPlugins(plugins: List<PluginBase>): List<PluginBase> =
    plugins
        .filter { it.isEnabled(it.pluginDescription.mainType) && it.hasComposeContent() && it.showInList() }
        .filter { it.pluginDescription.mainType in PANEL_TYPE_ORDER && (it.pluginDescription.mainType != PluginType.SYNC || it is NsClient) }
        .sortedBy { PANEL_TYPE_ORDER.indexOf(it.pluginDescription.mainType) }

private class BoostPanelTab(val label: String, val onClick: () -> Unit)

/**
 * Manage and the main plugin pages, one tap from the overview. Every other screen is in the drawer and
 * search. Each tab opens its own screen and Back returns, so the overview needs no pager and the graph
 * keeps its sideways gestures.
 */
@Composable
internal fun BoostPanelTabs(
    plugins: List<PluginBase>,
    actionsLabel: String,
    onActions: () -> Unit,
    onPlugin: (PluginBase) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = buildList {
        add(BoostPanelTab(actionsLabel, onActions))
        plugins.forEach { plugin -> add(BoostPanelTab(plugin.name) { onPlugin(plugin) }) }
    }

    LazyRow(modifier = modifier.fillMaxWidth().background(BoostV2Colors.cardBackground)) {
        items(tabs) { tab ->
            Text(
                text = tab.label,
                color = BoostV2Colors.textPrimary,
                fontSize = 13.sp,
                modifier = Modifier
                    .clickable(onClick = tab.onClick)
                    .heightIn(min = 48.dp)
                    .wrapContentHeight()
                    .padding(horizontal = 12.dp)
            )
        }
    }
}
