# Model compatibility verification

This is a verification checklist, not a claim that these candidates already run on Android.

| Candidate | Evaluation target | Required verification |
|---|---|---|
| LFM3-Omni-2B | Text and multimodal/audio paths | Official model card, artifact format, tokenizer/processor, license, Android runtime support, actual audio I/O, peak memory |
| Gemma 4 E2B | Lightweight general assistant | Confirm exact official model/version, artifact and quantization, license, Android runtime support, peak memory |
| MiniCPM-V 4.6 | Vision and document understanding | Official release/artifact, vision processor/projector support, license, Android runtime compatibility, peak memory |

## Acceptance criteria
- Link the official model card and license.
- Record artifact filename, format, quantization and exact byte size.
- Verify a published checksum or calculate SHA-256 after download.
- Run on a physical Android device and record device model, Android version, RAM, peak memory, latency and failures.
- Test offline mode with network disabled.
- Test each modality using real inputs. Image support does not imply video support.
- Do not mark a candidate supported until all relevant checks pass.
