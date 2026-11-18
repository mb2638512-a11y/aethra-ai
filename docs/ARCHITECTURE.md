# AETHRA AI architecture

## Product direction
A local-first Android AI assistant with optional online providers. The app must clearly distinguish local inference from remote inference and never present placeholder text as a model output.

## Android foundation
- Kotlin + Jetpack Compose native interface.
- Room for conversations and message history.
- WorkManager for resumable model downloads and integrity checks.
- Storage Access Framework for user-selected model storage.
- A provider interface to isolate UI from inference engines.
- Runtime adapters selected only after verifying exact model artifacts, licenses, Android support and device memory needs.

## Runtime integration sequence
1. Define model discovery, load/unload, generation, cancellation and capability reporting.
2. Evaluate LiteRT-LM and llama.cpp against exact official model artifacts.
3. Integrate one verified text model end-to-end.
4. Add vision, audio and video only when the runtime supports those exact modalities on Android.
5. Add downloads, SHA-256 checks, storage management and safe cancellation.
6. Add persistent chat history and export.

## Device safeguards
Never silently download multi-gigabyte models. Show exact size, free-storage requirement, license, supported modalities and device-memory guidance before installation. Block unsafe loads and provide a clear fallback.

## Current status
This repository currently contains the Android Compose UI foundation and model candidate metadata. Inference, downloads, persistence, camera, microphone and document parsing are not yet connected.
