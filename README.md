# Voice Stream Demo

A small Android app (Kotlin, Jetpack Compose, Media3) that streams ElevenLabs text-to-speech two ways and measures **time to first audio** for each.

| Mode | How it works | Fits |
|---|---|---|
| **HTTP + Media3** | POSTs the full text to `/v1/text-to-speech/{voice_id}/stream` and plays the MP3 progressively in ExoPlayer. A `ResolvingDataSource` rewrites ExoPlayer's GET into an authenticated POST. | Long-form reading where the full text is known up front (Reader-style) |
| **WebSocket + PCM** | Opens `/stream-input`, sends text word by word with a configurable delay to imitate an LLM, and writes the returned 16 kHz PCM straight into an `AudioTrack`. | Conversational agents where the answer is still being generated |

## Results

Time to first audio on a Sony Xperia (Android 14, Wi-Fi), `eleven_flash_v2_5`, the same 141-character text, 5 measured runs per setup after a warm-up:

| Setup | Median (ms) | Min–max (ms) |
|---|---|---|
| HTTP + Media3, default buffer | 502 | 417–553 |
| HTTP + Media3, 250 ms buffer | 461 | 331–550 |
| WebSocket + PCM, 0 ms/word | 552 | 533–589 |
| WebSocket + PCM, 40 ms/word | 1103 | 1099–1126 |
| WebSocket + PCM, 40 ms/word, `chunk_length_schedule: [50]` | 542 | 519–585 |

Lowering the server's first chunk threshold to 50 characters brought 40 ms/word back to the 0 ms/word level, which suggests most of that delay is the server waiting for enough text before it starts generating. The two modes stop the clock at slightly different points, so compare within a mode. Raw runs, how each number is measured, caveats and how to reproduce: [bench/SUMMARY.md](bench/SUMMARY.md).

## Setup

1. Open the folder in Android Studio and let it sync. You need an Android Studio version that supports AGP 9.4, JDK 17 or newer, and Android SDK Platform 37 (the project compiles against SDK 37).
2. Copy `local.properties.example` to `local.properties` (or add to the one Android Studio creates):
   ```
   ELEVENLABS_API_KEY=your_api_key_here
   ELEVENLABS_VOICE_ID=Xb7hH8MSUJpSbSDYk0k2   # optional
   ```
3. Run on a device (min SDK 26). Voice ID and model ID can also be changed in the app.

Default model is `eleven_flash_v2_5`. The WebSocket endpoint doesn't support v3/v4 models; the HTTP path accepts any model ID you type in.

## Code map

- `tts/HttpStreamingPlayer.kt` – Media3 path, POST via `ResolvingDataSource`, first-byte timing via `TransferListener`
- `tts/WebSocketTtsClient.kt` – OkHttp WebSocket wrapped in a `callbackFlow`
- `audio/PcmStreamPlayer.kt` – `AudioTrack` in stream mode, keeps 16-bit samples aligned across chunks
- `ui/DemoViewModel.kt` – runs both modes, records first byte / first audio, keeps recent runs, and runs benchmark batches
- `ui/DemoScreen.kt` – Compose UI
- `bench/BenchConfig.kt` – benchmark batch settings, read from adb intent extras (debug builds only)
- `bench/BenchCsv.kt` – the per-run `TTFA_BENCH` logcat line
- `app/src/test/.../bench/` – unit tests for the benchmark code, plus `BenchReportTest`, which builds the results table from `bench/results.csv`
- `bench/run_matrix.sh` (repo root) – runs every benchmark setup on a connected device and collects `bench/results.csv`

## Deliberate demo shortcuts

- **API key in BuildConfig.** Fine for a local demo, not for a shipped app. In production the app would ask its own backend for a short-lived token, like the ElevenLabs Agents SDK does with conversation tokens.
- **No caching on the HTTP path.** If ExoPlayer retries, it re-POSTs and regenerates (and re-bills) the audio. Production would put a `CacheDataSource` in front so retries and seeks hit the cache.
- **No background playback.** A real reader would wrap the player in a `MediaSessionService` for notification controls and lock screen support. The HTTP path already requests audio focus; the PCM path doesn't.

## Ideas for next steps

- Use the `alignment` timestamps from the WebSocket to highlight words as they're spoken
- Add the official ElevenLabs Agents SDK (`io.elevenlabs:elevenlabs-android`) as a third tab with a client tool
