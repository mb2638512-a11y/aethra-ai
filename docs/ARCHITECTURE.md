# AETHRA AI architecture

## Product direction
A local-first Android AI assistant with optional online providers. The app must clearly distinguish local inference from remote inference and never present placeholder text as a model output.

## Android foundation
- Kotlin + Jetpack Compose native interface.
- Local chat persistence is implemented for the MVP using app-private SharedPreferences with JSON serialization. A future Room migration is planned for multi-conversation history and search.
- WorkManager is planned for resumable model downloads and integrity checks.
- Storage Access Framework is planned for user-selected model storage.
- A provider interface will isolate UI from inference engines.
- Runtime adapters are selected only after verifying exact model artifacts, licenses, Android support and device memory needs.

## Runtime integration sequence
1. Define model discovery, load/unload, generation, cancellation and capability reporting.
2. Evaluate LiteRT-LM and llama.cpp against exact official model artifacts.
3. Integrate one verified text model end-to-end.
4. Add vision, audio and video only when the runtime supports those exact modalities on Android.
5. Add downloads, SHA-256 checks, storage management and safe cancellation.
6. Migrate chat storage to Room when multiple conversations, search and metadata are introduced.

## Device safeguards
Never silently download multi-gigabyte models. Show exact size, free-storage requirement, license, supported modalities and device-memory guidance before installation. Block unsafe loads and provide a clear fallback.

## Current status
Implemented: Compose chat UI foundation, model candidate catalogue metadata, and local persistence for the current chat. Not implemented: model inference, model downloads, multiple conversation management, camera, microphone, document parsing and video understanding. These features must not be represented as working until tested.
