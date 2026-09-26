package net.dyrox.launcher.ui.instances

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.dyrox.launcher.core.instance.ArgumentSplitter
import net.dyrox.launcher.core.instance.InstanceConfig
import net.dyrox.launcher.core.instance.LoaderType
import net.dyrox.launcher.core.launch.Resolution
import net.dyrox.launcher.ui.accounts.SecondaryButton
import net.dyrox.launcher.ui.components.AccentButton
import net.dyrox.launcher.ui.components.SegmentedToggle
import net.dyrox.launcher.ui.components.dyroxTextFieldColors
import net.dyrox.launcher.ui.platform.DesktopActions
import net.dyrox.launcher.ui.theme.DyroxColors
import kotlin.math.roundToInt

/** Per-instance settings. Edits a draft; nothing is written until Save. */
@Composable
fun InstanceSettingsPane(vm: InstancesViewModel, instance: InstanceConfig, running: Boolean, modifier: Modifier = Modifier) {
    var draft by remember(instance) { mutableStateOf(instance) }
    var jvmText by remember(instance) { mutableStateOf(ArgumentSplitter.join(instance.jvmArguments)) }
    var widthText by remember(instance) { mutableStateOf(instance.resolution?.width?.toString() ?: "1280") }
    var heightText by remember(instance) { mutableStateOf(instance.resolution?.height?.toString() ?: "720") }
    var customResolution by remember(instance) { mutableStateOf(instance.resolution != null) }
    var error by remember(instance) { mutableStateOf<String?>(null) }

    val resolution = if (customResolution) {
        val w = widthText.toIntOrNull()
        val h = heightText.toIntOrNull()
        if (w != null && h != null && w in 320..7680 && h in 240..4320) Resolution(w, h) else null
    } else {
        null
    }
    val resolutionValid = !customResolution || resolution != null
    val edited = draft.copy(jvmArguments = ArgumentSplitter.split(jvmText), resolution = resolution)
    val changed = edited != instance
    val fabricOk = draft.loader == LoaderType.VANILLA || vm.catalog.supportsFabric(draft.gameVersion)

    Column(modifier.verticalScroll(rememberScrollState()).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (running) Text("The game is running. Changes apply the next time it starts.", color = DyroxColors.Warning, fontSize = 12.sp)

        Field("Name") {
            OutlinedTextField(draft.name, { draft = draft.copy(name = it) }, singleLine = true, shape = RoundedCornerShape(10.dp), colors = dyroxTextFieldColors(), modifier = Modifier.fillMaxWidth())
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Field("Minecraft version", Modifier.weight(1f)) {
                SecondaryButton("${draft.gameVersion}  ▾", {
                    vm.dialog = InstanceDialog.ChooseVersion(draft.gameVersion) { draft = draft.copy(gameVersion = it) }
                })
            }
            Field("Mod loader", Modifier.weight(1f)) {
                SegmentedToggle(LoaderType.entries, draft.loader, { it.label }, { draft = draft.copy(loader = it) }, Modifier.fillMaxWidth())
                if (!fabricOk) Text("Fabric doesn't support ${draft.gameVersion}", color = DyroxColors.Warning, fontSize = 11.sp)
            }
        }

        if (draft.loader == LoaderType.FABRIC) {
            val supported = vm.core.mods.bundledClient?.minecraftVersion
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Switch(draft.dyroxClient, { draft = draft.copy(dyroxClient = it) }, colors = SwitchDefaults.colors(checkedTrackColor = DyroxColors.Accent, checkedThumbColor = DyroxColors.OnAccent))
                Column {
                    Text("Dyrox Client", color = DyroxColors.TextPrimary, fontSize = 13.sp)
                    Text(
                        when {
                            supported == null -> "Not bundled with this launcher build."
                            supported == draft.gameVersion -> "Installed automatically with Fabric Language Kotlin."
                            else -> "Available for Minecraft $supported only; this instance runs without it."
                        },
                        color = DyroxColors.TextMuted,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        Field("Memory ·${"%.1f".format(draft.maxMemoryMb / 1024f)} GB max") {
            Slider(
                value = draft.maxMemoryMb.toFloat(),
                onValueChange = { draft = draft.copy(maxMemoryMb = ((it / 512).roundToInt() * 512).coerceIn(1024, 16384)) },
                valueRange = 1024f..16384f,
                colors = SliderDefaults.colors(thumbColor = DyroxColors.Accent, activeTrackColor = DyroxColors.Accent, inactiveTrackColor = DyroxColors.SurfaceHighlight),
            )
        }

        Field("Extra JVM arguments") {
            OutlinedTextField(
                jvmText, { jvmText = it },
                placeholder = { Text("-XX:+UseZGC -Dfoo=\"a b\"", color = DyroxColors.TextMuted) },
                singleLine = true, shape = RoundedCornerShape(10.dp), colors = dyroxTextFieldColors(), modifier = Modifier.fillMaxWidth(),
            )
        }

        Field("Window size") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Switch(customResolution, { customResolution = it }, colors = SwitchDefaults.colors(checkedTrackColor = DyroxColors.Accent, checkedThumbColor = DyroxColors.OnAccent))
                Text(if (customResolution) "Custom" else "Game default", color = DyroxColors.TextSecondary, fontSize = 13.sp)
                if (customResolution) {
                    OutlinedTextField(widthText, { widthText = it.filter(Char::isDigit).take(4) }, singleLine = true, isError = !resolutionValid, shape = RoundedCornerShape(10.dp), colors = dyroxTextFieldColors(), modifier = Modifier.width(100.dp))
                    Text("×", color = DyroxColors.TextSecondary)
                    OutlinedTextField(heightText, { heightText = it.filter(Char::isDigit).take(4) }, singleLine = true, isError = !resolutionValid, shape = RoundedCornerShape(10.dp), colors = dyroxTextFieldColors(), modifier = Modifier.width(100.dp))
                }
            }
        }

        Field("Java") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    draft.javaPath.orEmpty(), { draft = draft.copy(javaPath = it.ifBlank { null }) },
                    placeholder = { Text("Automatic (Mojang's runtime for this version)", color = DyroxColors.TextMuted) },
                    singleLine = true, shape = RoundedCornerShape(10.dp), colors = dyroxTextFieldColors(), modifier = Modifier.weight(1f),
                )
                SecondaryButton("Browse", { DesktopActions.chooseOpenFile("Choose java/javaw executable")?.let { draft = draft.copy(javaPath = it.toString()) } })
            }
        }

        Field("Game folder") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    draft.gameDirectory.orEmpty(), { draft = draft.copy(gameDirectory = it.ifBlank { null }) },
                    placeholder = { Text(vm.core.instances.gameDirectory(draft.copy(gameDirectory = null)).toString(), color = DyroxColors.TextMuted) },
                    singleLine = true, shape = RoundedCornerShape(10.dp), colors = dyroxTextFieldColors(), modifier = Modifier.weight(1f),
                )
                SecondaryButton("Browse", { DesktopActions.chooseFolder("Choose game folder")?.let { draft = draft.copy(gameDirectory = it.toString()) } })
            }
            Text("Saves, options.txt, logs and mods live here. Each instance needs its own.", color = DyroxColors.TextMuted, fontSize = 11.sp)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Switch(draft.isolatedStorage, { draft = draft.copy(isolatedStorage = it) }, colors = SwitchDefaults.colors(checkedTrackColor = DyroxColors.Accent, checkedThumbColor = DyroxColors.OnAccent))
            Column {
                Text("Isolated storage", color = DyroxColors.TextPrimary, fontSize = 13.sp)
                Text("Own copy of versions, libraries and assets instead of the shared store. Uses more disk.", color = DyroxColors.TextMuted, fontSize = 11.sp)
            }
        }

        Field("Dyrox config profile") {
            OutlinedTextField(draft.configProfile, { draft = draft.copy(configProfile = it.filter { c -> c.isLetterOrDigit() || c in "-_" }.take(32)) }, singleLine = true, shape = RoundedCornerShape(10.dp), colors = dyroxTextFieldColors(), modifier = Modifier.width(260.dp))
        }

        error?.let { Text(it, color = DyroxColors.Danger, fontSize = 13.sp) }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AccentButton("Save", { error = vm.save(edited) }, enabled = changed && resolutionValid && edited.name.isNotBlank())
            TextButton(onClick = {
                draft = instance
                jvmText = ArgumentSplitter.join(instance.jvmArguments)
                customResolution = instance.resolution != null
                error = null
            }, enabled = changed) { Text("Revert", color = DyroxColors.TextSecondary) }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            SecondaryButton("Open folder", { vm.openFolder(instance) })
            SecondaryButton("Duplicate", { vm.duplicate(instance) })
            TextButton(onClick = { vm.dialog = InstanceDialog.ConfirmDelete(instance) }, enabled = !running) { Text("Delete", color = DyroxColors.Danger) }
        }
    }
}

@Composable
private fun Field(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = DyroxColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        content()
    }
}
