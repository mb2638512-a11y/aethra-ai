# Model compatibility verification

This is an evidence-based evaluation record, not a claim that these candidates already run inside AETHRA AI.

## Verified public facts

| Candidate/runtime | Verified information | AETHRA status |
|---|---|---|
| LFM3-Omni-2B | Official Liquid AI model card describes a 2.17B-parameter multimodal model with text, image, video and audio inputs and text/speech outputs. The official GGUF repository provides llama.cpp-oriented artifacts. | Candidate only. Android integration and all modalities remain untested in this app. |
| Gemma4-E2B | Google AI Edge LiteRT-LM documentation lists Gemma4-E2B as a supported model and documents Kotlin as a stable API for native Android. Published reference size is about 2,583 MB; that is not a promise of runtime RAM use. | Strong Android runtime candidate, but device fit and app integration remain untested. |
| MiniCPM-V 4.6 | Kept as a vision/document research candidate. | Exact official artifact, license, Android runtime and processor compatibility still need verification. |

## Runtime direction

- **LiteRT-LM:** Google documents a stable Kotlin API for native Android and lists Gemma 4 among supported model families. Evaluate this first for Gemma4-E2B.
- **llama.cpp:** Its Android documentation includes a native Android sample using GGUF metadata and an inference engine. The LFM3-Omni GGUF model card points to llama.cpp, but this does not by itself prove that AETHRA's Android build supports LFM3 image/audio/video paths. Pin and test a compatible llama.cpp revision before integration.

## Official references

- Liquid AI LFM3-Omni-2B model card: https://huggingface.co/LiquidAI/LFM3-Omni-2B
- Liquid AI LFM3-Omni-2B GGUF: https://huggingface.co/LiquidAI/LFM3-Omni-2B-GGUF
- Google LiteRT-LM overview and Kotlin API status: https://developers.google.com/edge/litert-lm/overview
- Google LiteRT getting started: https://developers.google.com/edge/litert/overview
- llama.cpp Android guide: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md

## Acceptance criteria before marking any model supported

- Link the official model card and license.
- Record artifact filename, format, quantization and exact byte size.
- Verify a published checksum or calculate SHA-256 after download.
- Record the exact runtime version/commit and Android ABI.
- Run on a physical Android device and record device model, Android version, RAM, peak memory, latency and failures.
- Test offline mode with network disabled.
- Test each modality using real inputs. Image support does not imply video or audio support.
- Do not mark a candidate supported until all relevant checks pass.
