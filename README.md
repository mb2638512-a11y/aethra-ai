# AETHRA AI

**Intelligence, without boundaries.**

A local-first native Android AI workspace. The goal is to combine private on-device inference with optional online providers while keeping model files under the user's control.

## Implemented in this development branch

- Kotlin + Jetpack Compose Android application with dark AETHRA chat interface.
- Local chat history persisted in app-private storage.
- Gemma 4 E2B LiteRT-LM session adapter with streamed text generation and cancellation.
- Android DownloadManager-based model downloads for Gemma 4 E2B, LFM3-Omni-2B (GGUF + projector) and MiniCPM-V 4.6 (GGUF + projector).
- Device readiness checks for RAM, free storage, low-memory state and thermal status.
- Single-active-model policy; Run selector is gated by the Android runtime registry and locally available model assets.
- Model catalogue search and category filtering, with explicit input/output modality labels.
- System speech recognition as voice-to-text input.
- Android system TextToSpeech playback for assistant text responses.
- Generic document/image/audio/video attachment picker with file name and MIME preview.
- Functional workspace menu for Model Library, runtime settings and chat reset.
- Multimodal runtime architecture and delivery plan.
- GitHub Actions Android debug build workflow.

## Important runtime boundaries

A file attachment picker is not the same as model inference. The current Gemma chat adapter accepts text only. Image, audio, video and document attachments are currently selected and previewed, but are not yet passed into model inference.

The model catalogue describes each model's expected input/output modalities. A catalogue entry or downloaded weight file does not mean that an Android runtime is integrated. At present, Gemma 4 E2B is the only model registered as runnable in the Android app. LFM3-Omni-2B and MiniCPM-V 4.6 still need the native llama.cpp Android integration and multimodal projector support. The image, audio and video generation workflows need their own dedicated runtimes and component dependencies.

Android system TextToSpeech and speech recognition are device services, not a locally loaded AETHRA generative audio model. Online speech recognition may depend on the device's configured service.

## Remaining end-to-end work

- Native llama.cpp Android build and JNI bridge, including multimodal libmtmd support.
- Actual LFM3-Omni-2B text/image/audio/video inference and MiniCPM-V vision inference.
- Media preprocessing: image scaling, audio decoding/transcription, video frame/audio sampling, PDF text extraction and document chunking.
- Dedicated image-generation/editing, audio-generation/TTS model adapters, and video-generation pipelines.
- Integrity verification for every downloaded artifact, resumable/retryable downloads, storage cleanup and explicit download state recovery.
- Multiple chats, export/share, camera capture, full audio recording controls and media-aware conversation history.
- Physical-device testing for model load, inference, offline use, cancellation, model switching, thermal throttling and memory pressure.

The app must not simulate AI output or claim a modality works until the exact model/runtime combination passes tests on a real Android device.

## Build

Requirements: JDK 17, Android SDK Platform 36 and Gradle 8.13.

    gradle --no-daemon assembleDebug

APK output: app/build/outputs/apk/debug/app-debug.apk.

## Model notes

Model weights are separate downloads and are not embedded in the APK. Model files may require several gigabytes of storage; runtime memory requirements vary by model, quantization and context size. Check docs/MODEL_COMPATIBILITY.md and docs/MULTIMODAL_RUNTIME_PLAN.md.

## License

No project license has been selected yet. Model licenses are separate from the AETHRA source license.