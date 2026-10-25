package com.gbegovic.voicedemo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import com.gbegovic.voicedemo.bench.BenchConfig
import com.gbegovic.voicedemo.ui.DemoRoute
import com.gbegovic.voicedemo.ui.DemoViewModel

class MainActivity : ComponentActivity() {

    // Same instance DemoRoute gets from viewModel(): both use the activity's store and default key.
    private val viewModel: DemoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            val context = LocalContext.current
            val colors = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = colors) {
                DemoRoute(viewModel)
            }
        }
        if (savedInstanceState == null) startBenchmarkFrom(intent)
    }

    /**
     * Debug-only: starts a benchmark batch from adb intent extras (see [BenchConfig]).
     * Launch with `am start -S` so every batch gets a fresh process and a cold start; a second
     * start of the running activity is just brought to front and its extras are dropped.
     */
    private fun startBenchmarkFrom(intent: Intent) {
        if (!BuildConfig.DEBUG) return
        val config = try {
            BenchConfig.fromExtras(
                mode = intent.getStringExtra(BenchConfig.EXTRA_MODE),
                lowLatency = intent.getBooleanExtra(BenchConfig.EXTRA_LOW_LATENCY, false),
                tokenDelayMs = intent.getIntExtra(BenchConfig.EXTRA_TOKEN_DELAY_MS, BenchConfig.DEFAULT_TOKEN_DELAY_MS),
                runs = intent.getIntExtra(BenchConfig.EXTRA_RUNS, BenchConfig.DEFAULT_RUNS),
                chunkSchedule = intent.getIntArrayExtra(BenchConfig.EXTRA_CHUNK_SCHEDULE),
            )
        } catch (e: IllegalArgumentException) {
            Log.e(BenchConfig.LOG_TAG, "aborted: ${e.message}")
            return
        } ?: return
        // A batch takes a few minutes; keep the screen on so the device doesn't doze mid-run.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        viewModel.runBatch(config)
    }
}
