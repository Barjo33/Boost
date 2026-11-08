package app.aaps.ui.compose.overview.boost

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.sync.NsClient
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class BoostPanelPluginsTest {

    private fun plugin(type: PluginType, enabled: Boolean = true, content: Boolean = true, listed: Boolean = true, nsClient: Boolean = false): PluginBase {
        val plugin = if (nsClient) mock<PluginBase>(extraInterfaces = arrayOf(NsClient::class)) else mock<PluginBase>()
        whenever(plugin.pluginDescription).thenReturn(PluginDescription().mainType(type))
        whenever(plugin.isEnabled(type)).thenReturn(enabled)
        whenever(plugin.hasComposeContent()).thenReturn(content)
        whenever(plugin.showInList()).thenReturn(listed)
        return plugin
    }

    @Test fun `a plugin needs to be enabled, listed and have a page`() {
        val shown = plugin(PluginType.SYNC, nsClient = true)
        val panels = boostPanelPlugins(
            listOf(plugin(PluginType.SYNC, enabled = false, nsClient = true), plugin(PluginType.SYNC, content = false, nsClient = true), plugin(PluginType.SYNC, listed = false, nsClient = true), shown)
        )
        assertThat(panels).containsExactly(shown)
    }

    @Test fun `the pump comes before the integrations`() {
        val sync = plugin(PluginType.SYNC, nsClient = true)
        val aps = plugin(PluginType.APS)
        val pump = plugin(PluginType.PUMP)
        assertThat(boostPanelPlugins(listOf(sync, aps, pump))).containsExactly(pump, aps, sync).inOrder()
    }

    @Test fun `rarely used plugin pages and the other sync plugins get no tab`() {
        val bg = plugin(PluginType.BGSOURCE)
        val loop = plugin(PluginType.LOOP)
        val others = listOf(PluginType.SENSITIVITY, PluginType.SMOOTHING, PluginType.CONSTRAINTS, PluginType.GENERAL, PluginType.SYNC).map { plugin(it) }
        assertThat(boostPanelPlugins(others + bg + loop)).containsExactly(loop, bg).inOrder()
    }
}
