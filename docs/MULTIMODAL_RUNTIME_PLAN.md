# AETHRA multimodal runtime plan

Last reviewed: 2026-11-18.

## Product contract

A model is runnable only when all of the following are true:
1. Every required artifact is present and non-empty.
2. Every artifact has passed a pinned SHA-256 check (or is explicitly marked unverified and blocked from trusted execution).
3. The app has a real Android runtime adapter for that exact model format and version.
4. The device passes memory, storage, ABI and thermal checks.
5. The advertised input/output modality has a passing on-device test.

A catalogue entry or a successful download is not evidence of runtime support.

## User-facing model lifecycle

Catalogue -> Downloading -> Downloaded -> Verifying -> Ready to run -> Loading -> Running -> Unloading.

Failures must show the exact stage and actionable error. The Run selector lists only models in Ready to run state. Selecting another model cancels generation, closes the previous session, releases native resources, then loads the new one. Default active-model count is one.

Settings may let users request a higher concurrency value, but the effective value must never exceed the number of independently validated runtime slots or the device safety limit. Until multi-session stress tests pass, effective concurrency remains one.

## Modality matrix

| Capability | Required UX | Runtime requirement |
|---|---|---|
| Text input/output | Chat, streaming, stop, history | Text-capable model adapter |
| Image input | Gallery/camera, preview, remove, prompt | Vision-capable adapter and image preprocessing |
| Image output | Prompt, aspect/size, seed, save/share | Diffusion/image-generation pipeline |
| Audio input | Record/import, playback, waveform, transcription | Audio-capable adapter or ASR |
| Audio output | Speak response, voice/rate controls, save audio | TTS or speech-generating model |
| Video input | Import, duration/size validation, frame sampling, audio track | Video-capable adapter; decode frames/audio |
| Video output | Prompt/reference, duration/resolution, progress, export | Video-generation pipeline; not a chat-only GGUF runtime |
| Files | PDF/text extraction, attachment list, citations | Parser + model context handling |

## Runtime lanes

- LiteRT-LM: .litertlm models, Kotlin coroutine API, model-specific text/vision/audio content support.
- llama.cpp Android: GGUF models. Multimodal GGUF requires the matching projector and the native libmtmd path; text-only loading is not enough.
- Diffusion/video pipelines: separate engines and model components; not interchangeable with LLM GGUF.
- ASR/TTS/audio generation: dedicated adapters where the selected model does not provide native speech input/output.

## Current verified implementation

- Gemma 4 E2B: LiteRT-LM Kotlin adapter is configured with CPU, vision and audio backends. Text, image and audio inputs are routed into LiteRT-LM Content; video is sampled into up to four image frames; PDF pages are rendered into up to four images; plain-text documents are appended to the prompt. Video audio-track extraction is not implemented. Physical-device modality tests remain pending.
- LFM3-Omni-2B: main GGUF and projector download manifest exists; Android llama.cpp/libmtmd runtime is not integrated.
- MiniCPM-V 4.6: GGUF and projector download manifest exists; Android llama.cpp runtime is not integrated.
- Image generation, audio generation and video generation: catalogue entries only; no dedicated Android generation pipeline is integrated. Android system TextToSpeech is available for reading text responses but is not a generative audio model.
- Physical-device tests: not yet performed.

## Delivery gates

1. Fix model-path selection and lifecycle correctness.
2. Add model state registry, integrity verification for every artifact, resumable downloads, cancellation and storage cleanup.
3. Integrate a pinned llama.cpp Android build with GGUF + projector support and test LFM3-Omni text/image/audio/video inputs.
4. Add LiteRT-LM image/audio content paths only for a model bundle that documents those modalities.
5. Add dedicated image, speech and video generation pipelines with explicit model dependencies.
6. Add capability-driven composer, media previews, progress, export and settings.
7. Test on physical Android devices: cold start, warm start, offline use, cancellation, rotation/backgrounding, thermal throttling, memory pressure and repeated model switching.

## Safety and truthfulness

- Never show Run for a model whose runtime is missing.
- Never label an image/video understanding model as an image/video generator.
- Character/face replacement workflows must be consent-based and must not facilitate non-consensual intimate imagery or impersonation.
- Record model license and source URL before enabling download or commercial use.