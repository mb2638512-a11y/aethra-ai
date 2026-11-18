# AETHRA AI

**Intelligence, without boundaries.**

A local-first native Android AI workspace. The goal is to combine private on-device inference with optional online providers while keeping model files under the user's control.

## Current implementation

- Kotlin + Jetpack Compose Android application.
- Dark AETHRA chat interface and model catalogue.
- Local chat history persisted in app-private storage.
- Three model candidates with upstream artifact, runtime and memory metadata.
- LiteRT-LM Android dependency and a coroutine-based Gemma 4 E2B inference-session adapter, wired to the chat composer for streaming responses and cancellation.
- Android DownloadManager-based model downloads for all three catalogue entries, including the Gemma artifact SHA-256 reference and progress/cancel UI.
- GitHub Actions workflow that builds and uploads a debug APK.

## Not yet complete

Gemma inference and the model-download UI are implemented in source, but the latest Android build must pass before this revision can be distributed. Gemma still needs physical-device inference validation and completed-file checksum enforcement. LFM3-Omni-2B and MiniCPM-V 4.6 can be downloaded from their official repositories, but still need llama.cpp Android inference integration. Also outstanding: multiple conversation management, verified model downloads, image/file import, PDF parsing, camera, microphone, speech output, video processing, online provider settings, export/share, and physical-device performance testing.

The app must not simulate AI output. Until a real model is installed and loaded, it must clearly report that inference is unavailable.

## Build

Requirements: JDK 17, Android SDK Platform 36 and Gradle 8.13.

```bash
gradle --no-daemon assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Model notes

Model weights are separate downloads and are not embedded in the APK. The three model packages together require several gigabytes of storage, and runtime memory requirements vary. Check [Model compatibility](docs/MODEL_COMPATIBILITY.md) before attempting installation.

## License

No project license has been selected yet. Model licenses are separate from the AETHRA source license.
