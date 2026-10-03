
> Created by **Avash Matrix** · **Avash Studio** — source-available, see LICENSE (no redistribution / rebranding).
# Jarvis (Android, Kotlin + Jetpack Compose)

Private on-device assistant. Gemma models run locally with llama.cpp (GGUF). Gold particle hologram,
Siri-style summon at the bottom of the screen, Persian/English UI.

## Run
1. Android Studio (Ladybug or newer) -> File > Open -> this folder -> wait for Gradle sync
   (if asked about Gradle, use 8.11.1; or run `gradle wrapper` once in the folder).
2. Plug in a REAL arm64 phone (Android 12+). Emulators are far too slow for LLMs.
3. Run. First launch: the app reads RAM / chip / free storage, recommends a level and downloads it.

## Where things are
- `data/Catalog.kt`      the 6 levels, Gemma model + download sources (edit here to add models, e.g. Gemma 4)
- `data/Downloader.kt`   resumable download: Hugging Face public GGUF + registry.ollama.ai (sizes read live)
- `engine/LlmEngine.kt`  the ONLY file that touches the llama.cpp wrapper (Llamatik 0.12.x)
- `engine/Persona.kt`    system prompt + humor slider mapping + "hard question" / "needs web" rules
- `search/WebSearch.kt`  Brave API (if key set) or keyless DuckDuckGo HTML
- `search/CloudAi.kt`    free cloud fallback for hard questions (asks first by default)
- `ui/HoloOrb.kt`        Compose port of the approved hologram (big + small variants)

## Known risks (this project was written without a compiler)
- Not compiled/tested yet. Expect a few small compile fixes on first sync.
- `LlmEngine.kt` uses the Llamatik API as documented for 0.12 (`initGenerateModel`, `generateStreamWithContext`).
  If your version differs, change only that file. Stop is soft (tokens are ignored after Stop).
- Ollama's Gemma blobs may not load in llama.cpp; that's why Hugging Face is tried first by default.
- Free keyless cloud endpoints change often. Put your own OpenAI-compatible URL + key in Settings if they stop working.
- DuckDuckGo HTML scraping can break; a Brave key is more reliable.
- No always-on "Jarvis" wake word in v1 (tap the hologram or mic).
- Prompts sent to the cloud leave the phone. Default mode is "ask first".
