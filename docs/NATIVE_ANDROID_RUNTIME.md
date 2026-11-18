# Native Android GGUF runtime milestone

## Pin

- Upstream: https://github.com/ggml-org/llama.cpp
- Tag: \`b11960\` (llama.cpp v0.8.0)
- Resolved commit: \`8642541b255bfb5dc39cd351a178be09013a55d6\`
- Android ABI: \`arm64-v8a\`
- Minimum native platform: Android API 28
- Libraries to validate: \`libllama.so\` and \`libmtmd.so\`

The pin is intentional: model files and the native ABI must be tested against one reproducible upstream revision. Upgrade only after model-specific regression tests pass.

## Native build

The \`.github/workflows/llama-android-native.yml\` workflow checks out the pinned upstream source and builds the CPU libraries with the Android NDK. It disables subprocess/video helper dependencies; AETHRA performs video frame sampling and soundtrack decoding with Android MediaExtractor/MediaCodec, then sends image/audio files as separate media parts.

CMake options:
- \`ANDROID_ABI=arm64-v8a\`, \`ANDROID_PLATFORM=android-28\`
- \`GGML_NATIVE=OFF\`, \`GGML_OPENMP=OFF\`, \`GGML_LLAMAFILE=OFF\`
- \`LLAMA_OPENSSL=OFF\`, \`LLAMA_SUBPROCESS=OFF\`
- \`LLAMA_BUILD_COMMON=OFF\`, \`LLAMA_BUILD_TOOLS=OFF\`, \`LLAMA_BUILD_TESTS=OFF\`
- \`LLAMA_BUILD_MTMD=ON\`, \`MTMD_VIDEO=OFF\`, \`BUILD_SHARED_LIBS=ON\`

## JNI contract to implement after native build validation

1. One process-wide native session; opening a new model must unload the prior session first.
2. Load a GGUF text model and its matching projector (\`mmproj\`) as a single session. Reject missing/mismatched projector files before allocating the model.
3. Expose native capabilities from \`mtmd_helper_get_capabilities\`; catalogue metadata alone must never enable Run.
4. Use \`mtmd_helper_bitmap_init_from_file\` for image and supported WAV/audio files; use \`mtmd_tokenize_from_parts\` and \`mtmd_helper_eval_chunks\` for multimodal prefill.
5. Stream decoded text to Kotlin, support cooperative cancellation, and release chunks, bitmaps, contexts, projector context and model in reverse allocation order.
6. Start with text + image for MiniCPM-V, then validate LFM3-Omni text/image/audio. LFM3-Omni video is a synchronized frame/soundtrack format and must be tested against its own prompt template; do not treat a generic list of images as full native video support.

## Safety gates

- Do not mark LFM3-Omni or MiniCPM-V runnable until JNI, native build, projector pairing, inference and physical Android device tests pass.
- Do not bundle native libraries for an ABI that has not been built and tested.
- Do not infer phone compatibility from model download size. Check available RAM, free storage, thermal state and model/projector size before loading.
- \`libmtmd\` explicitly documents its API as experimental and subject to breaking changes. Keep the upstream pin fixed while the Kotlin/JNI adapter is under test.

## Current state

The native build workflow is a separate validation step. It does not yet package a GGUF runtime in the AETHRA APK, and the two GGUF catalogue entries remain download-only. Gemma 4 E2B continues to use its existing LiteRT-LM adapter.
