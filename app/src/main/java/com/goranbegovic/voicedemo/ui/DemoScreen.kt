package com.goranbegovic.voicedemo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DemoRoute(viewModel: DemoViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DemoScreen(
        state = state,
        onTextChange = viewModel::onTextChange,
        onVoiceIdChange = viewModel::onVoiceIdChange,
        onModelIdChange = viewModel::onModelIdChange,
        onModeChange = viewModel::onModeChange,
        onTokenDelayChange = viewModel::onTokenDelayChange,
        onLowLatencyBufferChange = viewModel::onLowLatencyBufferChange,
        onSpeak = viewModel::speak,
        onStop = viewModel::stop,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoScreen(
    state: DemoUiState,
    onTextChange: (String) -> Unit,
    onVoiceIdChange: (String) -> Unit,
    onModelIdChange: (String) -> Unit,
    onModeChange: (StreamMode) -> Unit,
    onTokenDelayChange: (Int) -> Unit,
    onLowLatencyBufferChange: (Boolean) -> Unit,
    onSpeak: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Voice Stream Demo", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Time-to-first-audio with ElevenLabs streaming TTS",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!state.hasApiKey) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        "No API key. Add ELEVENLABS_API_KEY to local.properties and rebuild.",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                StreamMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.mode == mode,
                        onClick = { onModeChange(mode) },
                        enabled = !state.isRunning,
                        shape = SegmentedButtonDefaults.itemShape(index, StreamMode.entries.size),
                    ) { Text(mode.label) }
                }
            }

            OutlinedTextField(
                value = state.text,
                onValueChange = onTextChange,
                label = { Text("Text") },
                enabled = !state.isRunning,
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.voiceId,
                    onValueChange = onVoiceIdChange,
                    label = { Text("Voice ID") },
                    singleLine = true,
                    enabled = !state.isRunning,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.modelId,
                    onValueChange = onModelIdChange,
                    label = { Text("Model ID") },
                    singleLine = true,
                    enabled = !state.isRunning,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.mode == StreamMode.Http) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Low-latency buffer", style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (state.lowLatencyBuffer) "Start after 250 ms of audio"
                            else "ExoPlayer default (~2.5 s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.lowLatencyBuffer,
                        onCheckedChange = onLowLatencyBufferChange,
                        enabled = !state.isRunning,
                    )
                }
            }

            if (state.mode == StreamMode.WebSocket) {
                Column {
                    Text(
                        "Simulated LLM speed: ${state.tokenDelayMs} ms per word",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Slider(
                        value = state.tokenDelayMs.toFloat(),
                        onValueChange = { onTokenDelayChange(it.toInt()) },
                        valueRange = 0f..200f,
                        enabled = !state.isRunning,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSpeak,
                    enabled = !state.isRunning && state.hasApiKey && state.text.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text("Speak") }
                OutlinedButton(
                    onClick = onStop,
                    enabled = state.isRunning,
                    modifier = Modifier.weight(1f),
                ) { Text("Stop") }
            }

            MetricsCard(state)
            if (state.history.isNotEmpty()) HistoryCard(state.history)
            if (state.log.isNotEmpty()) LogCard(state.log)
        }
    }
}

@Composable
private fun MetricsCard(state: DemoUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Metric("First byte", state.firstByteMs)
            Metric("First audio", state.firstAudioMs)
        }
    }
}

@Composable
private fun Metric(label: String, valueMs: Long?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            valueMs?.let { "$it ms" } ?: "—",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun HistoryCard(history: List<RunResult>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Recent runs", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            history.forEachIndexed { index, run ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text(
                    "${run.mode.label} · ${run.variant} · ${run.modelId}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "first byte ${run.firstByteMs ?: "—"} ms · first audio ${run.firstAudioMs ?: "—"} ms · ${run.chars} chars",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LogCard(lines: List<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Log", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            lines.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
