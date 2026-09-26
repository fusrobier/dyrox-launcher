package net.dyrox.launcher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.dyrox.launcher.core.manifest.VersionManifest
import net.dyrox.launcher.ui.common.VersionCatalog
import net.dyrox.launcher.ui.theme.DyroxColors

/** Searchable Minecraft version list with a snapshot toggle and Fabric tags. */
@Composable
fun VersionList(catalog: VersionCatalog, selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var snapshots by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search versions…", color = DyroxColors.TextMuted) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = dyroxTextFieldColors(),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = snapshots,
                onCheckedChange = { snapshots = it },
                colors = SwitchDefaults.colors(checkedTrackColor = DyroxColors.Accent, checkedThumbColor = DyroxColors.OnAccent),
            )
            Spacer(Modifier.width(6.dp))
            Text("Snapshots", color = DyroxColors.TextSecondary, fontSize = 12.sp)
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                catalog.versions.isEmpty() && catalog.loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = DyroxColors.Accent)
                catalog.versions.isEmpty() && catalog.error != null ->
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Could not load versions", color = DyroxColors.Danger)
                        Text(catalog.error.orEmpty(), color = DyroxColors.TextMuted, fontSize = 12.sp)
                        TextButton(onClick = catalog::refresh) { Text("Retry", color = DyroxColors.Accent) }
                    }
                else -> {
                    val listState = rememberLazyListState()
                    LazyColumn(Modifier.fillMaxSize().padding(end = 10.dp), state = listState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(catalog.filtered(query, snapshots), key = { it.id }) { entry ->
                            VersionRow(entry, entry.id == selected, catalog.fabricVersions?.contains(entry.id) == true) { onSelect(entry.id) }
                        }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun VersionRow(entry: VersionManifest.Entry, selected: Boolean, fabric: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(if (selected) DyroxColors.Accent.copy(alpha = 0.12f) else Color.Transparent, tween(150))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(width = 3.dp, height = 16.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) DyroxColors.Accent else Color.Transparent))
        Text(
            entry.id,
            color = if (selected) DyroxColors.Accent else DyroxColors.TextPrimary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        if (fabric) Tag("Fabric", DyroxColors.Info)
        if (!entry.isRelease) Tag(entry.type, DyroxColors.Warning)
    }
}
