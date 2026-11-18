# AETHRA AI

**Intelligence, without boundaries.**

A local-first Android AI assistant focused on private, on-device inference with optional online AI providers.

## Current state

The repository is at the native Android foundation stage:
- Kotlin + Jetpack Compose chat UI.
- Candidate model catalogue.
- Architecture and model-verification documentation.
- GitHub Actions workflow to compile and publish a debug APK artifact.

**Not yet implemented:** actual model inference, model downloads, chat persistence, file/document analysis, camera, voice input/output and video understanding. The UI does not fake model responses.

## Build

Requirements: JDK 17, Android SDK Platform 36 and Gradle 8.13.

```bash
gradle assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Model policy

Model names in the catalogue are evaluation candidates, not a promise of Android compatibility. Each candidate must pass artifact, license, runtime, memory and real-device modality checks before being marked supported.

See [Architecture](docs/ARCHITECTURE.md) and [Model compatibility](docs/MODEL_COMPATIBILITY.md).

## License

No project license has been selected yet. Do not assume this repository or bundled model artifacts are open for redistribution.
