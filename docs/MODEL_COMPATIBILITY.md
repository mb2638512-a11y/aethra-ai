# Model compatibility and integration status

Last reviewed: 2026-11-18.

This document separates upstream support from AETHRA implementation status. A model is not considered supported by AETHRA until its runtime is integrated, the exact artifacts are downloaded and verified, and it passes tests on a physical Android device.

## Three requested models

| Model | Official artifact / quantization | Approximate download | Runtime direction | AETHRA status |
|---|---|---:|---|---|
| LFM3-Omni-2B | GGUF Q4_K_M plus matching multimodal projector | 1.1 GB + 292 MB | llama.cpp Android | Catalogue metadata only; Android native runtime not integrated |
| Gemma 4 E2B | `.litertlm` bundle | 2.58 GB | LiteRT-LM Kotlin API | LiteRT-LM dependency and CPU session adapter added; downloader, UI wiring and device test remain |
| MiniCPM-V 4.6 | GGUF Q4_K_M plus vision projector | About 1.6 GB total | llama.cpp Android | Catalogue metadata only; Android native runtime not integrated |

## Runtime integration

### Gemma 4 E2B — first integration target

AETHRA now includes the LiteRT-LM Android dependency and `LiteRtModelSession`, a coroutine-based session adapter that:
- Initializes a model from an app-private filesystem path off the UI thread.
- Creates and retains a conversation.
- Exposes streaming generation as a Kotlin Flow.
- Closes the conversation and engine to release native resources.

The adapter is not yet connected to the chat screen. The model downloader, checksum verification, cancellation, progress UI, GPU/NPU selection and physical-device tests remain outstanding. The model file is not bundled in the APK.

Official model/runtime references:
- https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
- https://developers.google.com/edge/litert-lm/android
- https://developers.google.com/edge/litert-lm/overview

Google's published reference lists the Gemma 4 E2B bundle at about 2.58 GB and an 8 GB minimum-device-memory recommendation. Performance figures are from specific reference devices and must not be extrapolated to other phones.

### LFM3-Omni-2B

The official Liquid AI GGUF repository provides Q4_K_M weights around 1.1 GB and a Q4_K_M multimodal projector around 292 MB. Its model card documents llama.cpp support for text, image, audio and video workflows. That upstream support does not mean AETHRA's Android app has those pathways yet.

References:
- https://huggingface.co/LiquidAI/LFM3-Omni-2B
- https://huggingface.co/LiquidAI/LFM3-Omni-2B-GGUF
- https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md

### MiniCPM-V 4.6

OpenBMB's deployment guide documents llama.cpp and an official GGUF repository. It lists the 1.3B Q4_K_M variant at about 1.6 GB total and recommends at least 6 GB device RAM. AETHRA still needs the Android native integration and modality tests.

References:
- https://openbmb.cn/docs/en/guides/minicpm-v/minicpm-v-4.6/deploy
- https://huggingface.co/openbmb/MiniCPM-V-4.6-gguf
- https://github.com/OpenBMB/MiniCPM-V-Apps

## Acceptance criteria

Before a model is marked installed or supported:
1. Download only from the official model repository and show its license.
2. Record exact filenames, byte sizes, quantization and SHA-256.
3. Pin the runtime version and supported Android ABIs.
4. Check free storage and memory before loading.
5. Test text generation, streaming, cancellation and app lifecycle.
6. Test each advertised modality using real sample inputs.
7. Repeat tests offline and record device, Android version, peak RAM and latency.
8. Never infer video/audio support from image support, or upstream support from AETHRA support.
