#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <mutex>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>

#include <unistd.h>

#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

namespace {

constexpr const char * LOG_TAG = "AETHRA-GGUF";
std::mutex g_runtime_mutex;
std::atomic<bool> g_cancelled{false};
bool g_backend_initialized = false;
llama_model * g_model = nullptr;
llama_context * g_context = nullptr;
mtmd_context * g_mtmd = nullptr;

void log_error(const std::string & message) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s", message.c_str());
}

void throw_illegal_state(JNIEnv * env, const std::string & message) {
    jclass exception = env->FindClass("java/lang/IllegalStateException");
    if (exception != nullptr) {
        env->ThrowNew(exception, message.c_str());
    }
}

std::string from_java(JNIEnv * env, jstring value) {
    if (value == nullptr) return {};
    const jsize length = env->GetStringLength(value);
    const jchar * chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr) return {};

    std::string result;
    result.reserve(static_cast<size_t>(length) * 3);
    for (jsize index = 0; index < length; ++index) {
        uint32_t codepoint = chars[index];
        if (codepoint >= 0xD800 && codepoint <= 0xDBFF) {
            if (index + 1 < length) {
                const uint32_t low = chars[index + 1];
                if (low >= 0xDC00 && low <= 0xDFFF) {
                    codepoint = 0x10000 + ((codepoint - 0xD800) << 10) + (low - 0xDC00);
                    ++index;
                } else {
                    codepoint = 0xFFFD;
                }
            } else {
                codepoint = 0xFFFD;
            }
        } else if (codepoint >= 0xDC00 && codepoint <= 0xDFFF) {
            codepoint = 0xFFFD;
        }

        if (codepoint <= 0x7F) {
            result.push_back(static_cast<char>(codepoint));
        } else if (codepoint <= 0x7FF) {
            result.push_back(static_cast<char>(0xC0 | (codepoint >> 6)));
            result.push_back(static_cast<char>(0x80 | (codepoint & 0x3F)));
        } else if (codepoint <= 0xFFFF) {
            result.push_back(static_cast<char>(0xE0 | (codepoint >> 12)));
            result.push_back(static_cast<char>(0x80 | ((codepoint >> 6) & 0x3F)));
            result.push_back(static_cast<char>(0x80 | (codepoint & 0x3F)));
        } else {
            result.push_back(static_cast<char>(0xF0 | (codepoint >> 18)));
            result.push_back(static_cast<char>(0x80 | ((codepoint >> 12) & 0x3F)));
            result.push_back(static_cast<char>(0x80 | ((codepoint >> 6) & 0x3F)));
            result.push_back(static_cast<char>(0x80 | (codepoint & 0x3F)));
        }
    }
    env->ReleaseStringChars(value, chars);
    return result;
}

jstring to_java(JNIEnv * env, const std::string & value) {
    std::vector<jchar> utf16;
    utf16.reserve(value.size());
    const auto * bytes = reinterpret_cast<const uint8_t *>(value.data());
    size_t index = 0;
    while (index < value.size()) {
        uint32_t codepoint = 0xFFFD;
        const uint8_t first = bytes[index++];
        if (first <= 0x7F) {
            codepoint = first;
        } else if ((first & 0xE0) == 0xC0 && index < value.size() &&
                   (bytes[index] & 0xC0) == 0x80) {
            codepoint = ((first & 0x1F) << 6) | (bytes[index++] & 0x3F);
            if (codepoint < 0x80) codepoint = 0xFFFD;
        } else if ((first & 0xF0) == 0xE0 && index + 1 < value.size() &&
                   (bytes[index] & 0xC0) == 0x80 && (bytes[index + 1] & 0xC0) == 0x80) {
            codepoint = ((first & 0x0F) << 12) |
                        ((bytes[index] & 0x3F) << 6) | (bytes[index + 1] & 0x3F);
            index += 2;
            if (codepoint < 0x800 || (codepoint >= 0xD800 && codepoint <= 0xDFFF)) codepoint = 0xFFFD;
        } else if ((first & 0xF8) == 0xF0 && index + 2 < value.size() &&
                   (bytes[index] & 0xC0) == 0x80 &&
                   (bytes[index + 1] & 0xC0) == 0x80 &&
                   (bytes[index + 2] & 0xC0) == 0x80) {
            codepoint = ((first & 0x07) << 18) |
                        ((bytes[index] & 0x3F) << 12) |
                        ((bytes[index + 1] & 0x3F) << 6) | (bytes[index + 2] & 0x3F);
            index += 3;
            if (codepoint < 0x10000 || codepoint > 0x10FFFF) codepoint = 0xFFFD;
        }

        if (codepoint <= 0xFFFF) {
            utf16.push_back(static_cast<jchar>(codepoint));
        } else {
            codepoint -= 0x10000;
            utf16.push_back(static_cast<jchar>(0xD800 + (codepoint >> 10)));
            utf16.push_back(static_cast<jchar>(0xDC00 + (codepoint & 0x3FF)));
        }
    }
    static const jchar empty = 0;
    const jchar * data = utf16.empty() ? &empty : utf16.data();
    return env->NewString(data, static_cast<jsize>(utf16.size()));
}

void unload_locked() {
    if (g_mtmd != nullptr) {
        mtmd_free(g_mtmd);
        g_mtmd = nullptr;
    }
    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

int thread_count() {
    const long available = sysconf(_SC_NPROCESSORS_ONLN);
    return static_cast<int>(std::max(2L, std::min(4L, available > 2 ? available - 1 : 2)));
}

std::string format_user_prompt(const std::string & prompt) {
    if (g_model == nullptr) return prompt;
    const char * chat_template = llama_model_chat_template(g_model, nullptr);
    if (chat_template == nullptr || chat_template[0] == '\0') return prompt;

    llama_chat_message message{ "user", prompt.c_str() };
    const int32_t required = llama_chat_apply_template(chat_template, &message, 1, true, nullptr, 0);
    if (required <= 0 || required > 1'000'000) return prompt;

    std::vector<char> formatted(static_cast<size_t>(required) + 1);
    const int32_t written = llama_chat_apply_template(
        chat_template, &message, 1, true, formatted.data(), static_cast<int32_t>(formatted.size())
    );
    if (written <= 0 || written > required) return prompt;
    return std::string(formatted.data(), static_cast<size_t>(written));
}

struct MediaBundle {
    mtmd_input_chunks * chunks = nullptr;
    std::vector<mtmd_helper_bitmap_wrapper> wrappers;

    ~MediaBundle() {
        if (chunks != nullptr) mtmd_input_chunks_free(chunks);
        for (auto & wrapper : wrappers) {
            if (wrapper.video_ctx != nullptr) mtmd_helper_video_free(wrapper.video_ctx);
            if (wrapper.bitmap != nullptr) mtmd_bitmap_free(wrapper.bitmap);
        }
    }
};

void evaluate_prompt(const std::string & prompt, const std::vector<std::string> & media_paths) {
    llama_memory_clear(llama_get_memory(g_context), true);

    if (g_mtmd != nullptr) {
        MediaBundle media;
        media.chunks = mtmd_input_chunks_init();
        if (media.chunks == nullptr) throw std::runtime_error("Could not allocate multimodal input chunks.");

        mtmd_input_text text_input{
            prompt.c_str(),
            prompt.size(),
            true,
            true
        };

        std::vector<mtmd_input_part> parts;
        parts.reserve(media_paths.size() + 1);
        parts.push_back(mtmd_input_part{ &text_input, nullptr });
        media.wrappers.reserve(media_paths.size());

        for (const auto & path : media_paths) {
            auto wrapper = mtmd_helper_bitmap_init_from_file(
                g_mtmd, path.c_str(), false, mtmd_helper_init_opt_default()
            );
            if (wrapper.bitmap == nullptr) {
                if (wrapper.video_ctx != nullptr) mtmd_helper_video_free(wrapper.video_ctx);
                throw std::runtime_error("libmtmd could not decode media file: " + path);
            }
            media.wrappers.push_back(wrapper);
            parts.push_back(mtmd_input_part{ nullptr, media.wrappers.back().bitmap });
        }

        std::vector<const mtmd_input_part *> part_pointers;
        part_pointers.reserve(parts.size());
        for (const auto & part : parts) part_pointers.push_back(&part);

        const int32_t tokenized = mtmd_tokenize_from_parts(
            g_mtmd, media.chunks, part_pointers.data(), part_pointers.size(), true
        );
        if (tokenized != 0) {
            throw std::runtime_error("libmtmd could not tokenize the selected image/audio inputs (code " +
                                     std::to_string(tokenized) + ").");
        }

        llama_pos new_position = 0;
        const int32_t evaluated = mtmd_helper_eval_chunks(
            g_mtmd, g_context, media.chunks, 0, 0, 512, true, &new_position
        );
        if (evaluated != 0) {
            throw std::runtime_error("libmtmd multimodal prefill failed (code " +
                                     std::to_string(evaluated) + ").");
        }
        return;
    }

    if (!media_paths.empty()) {
        throw std::runtime_error("This GGUF model was loaded without a multimodal projector.");
    }

    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    if (prompt.size() > static_cast<size_t>(INT32_MAX)) {
        throw std::runtime_error("Prompt is too large.");
    }
    int32_t token_count = llama_tokenize(
        vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()), nullptr, 0, true, true
    );
    if (token_count < 0) token_count = -token_count;
    if (token_count <= 0) throw std::runtime_error("Could not tokenize the prompt.");

    std::vector<llama_token> tokens(static_cast<size_t>(token_count));
    token_count = llama_tokenize(
        vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
        tokens.data(), static_cast<int32_t>(tokens.size()), true, true
    );
    if (token_count <= 0) throw std::runtime_error("Prompt tokenization failed.");

    llama_batch batch = llama_batch_get_one(tokens.data(), token_count);
    if (llama_decode(g_context, batch) != 0) {
        throw std::runtime_error("llama.cpp prompt evaluation failed.");
    }
}

std::string generate_locked(const std::string & prompt,
                            const std::vector<std::string> & media_paths,
                            int max_tokens) {
    if (g_model == nullptr || g_context == nullptr) {
        throw std::runtime_error("No GGUF model is loaded.");
    }

    g_cancelled.store(false);
    const std::string formatted_prompt = format_user_prompt(prompt);
    evaluate_prompt(formatted_prompt, media_paths);

    auto sampler_params = llama_sampler_chain_default_params();
    llama_sampler * sampler = llama_sampler_chain_init(sampler_params);
    if (sampler == nullptr) throw std::runtime_error("Could not create llama.cpp sampler.");

    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    std::string output;
    max_tokens = std::max(1, std::min(1024, max_tokens));

    try {
        for (int index = 0; index < max_tokens && !g_cancelled.load(); ++index) {
            const llama_token token = llama_sampler_sample(sampler, g_context, -1);
            if (llama_vocab_is_eog(vocab, token)) break;

            llama_sampler_accept(sampler, token);
            char piece[256];
            int32_t piece_size = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, false);
            if (piece_size < 0) {
                std::vector<char> expanded(static_cast<size_t>(-piece_size));
                piece_size = llama_token_to_piece(
                    vocab, token, expanded.data(), static_cast<int32_t>(expanded.size()), 0, false
                );
                if (piece_size > 0) output.append(expanded.data(), static_cast<size_t>(piece_size));
            } else if (piece_size > 0) {
                output.append(piece, static_cast<size_t>(piece_size));
            }

            llama_batch batch = llama_batch_get_one(const_cast<llama_token *>(&token), 1);
            if (llama_decode(g_context, batch) != 0) {
                throw std::runtime_error("llama.cpp token decode failed.");
            }
        }
    } catch (...) {
        llama_sampler_free(sampler);
        throw;
    }

    llama_sampler_free(sampler);
    return output;
}

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_ai_aethra_mobile_NativeGgufRuntime_nativeLoadModel(
    JNIEnv * env, jobject, jstring model_path, jstring projector_path
) {
    std::lock_guard<std::mutex> lock(g_runtime_mutex);
    unload_locked();

    try {
        const std::string model = from_java(env, model_path);
        const std::string projector = from_java(env, projector_path);
        if (model.empty()) throw std::runtime_error("A GGUF model path is required.");

        if (!g_backend_initialized) {
            llama_backend_init();
            g_backend_initialized = true;
        }

        auto model_params = llama_model_default_params();
        model_params.n_gpu_layers = 0;
        g_model = llama_model_load_from_file(model.c_str(), model_params);
        if (g_model == nullptr) throw std::runtime_error("llama.cpp could not load this GGUF model.");

        if (!projector.empty()) {
            auto mtmd_params = mtmd_context_params_default();
            mtmd_params.use_gpu = false;
            mtmd_params.n_threads = thread_count();
            mtmd_params.print_timings = false;
            mtmd_params.warmup = false;
            g_mtmd = mtmd_init_from_file(projector.c_str(), g_model, mtmd_params);
            if (g_mtmd == nullptr) {
                throw std::runtime_error("Could not load the matching multimodal projector.");
            }
        }

        auto context_params = llama_context_default_params();
        context_params.n_ctx = 4096;
        context_params.n_batch = 512;
        context_params.n_ubatch = 512;
        context_params.n_threads = thread_count();
        context_params.n_threads_batch = thread_count();
        g_context = llama_init_from_model(g_model, context_params);
        if (g_context == nullptr) throw std::runtime_error("Could not allocate the GGUF inference context.");

        __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Loaded GGUF model with libmtmd=%s",
                            g_mtmd == nullptr ? "false" : "true");
        return JNI_TRUE;
    } catch (const std::exception & error) {
        log_error(error.what());
        unload_locked();
        throw_illegal_state(env, error.what());
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_ai_aethra_mobile_NativeGgufRuntime_nativeGenerate(
    JNIEnv * env, jobject, jstring prompt, jobjectArray media_paths, jint max_tokens
) {
    std::lock_guard<std::mutex> lock(g_runtime_mutex);
    try {
        const std::string prompt_text = from_java(env, prompt);
        std::vector<std::string> paths;
        if (media_paths != nullptr) {
            const jsize count = env->GetArrayLength(media_paths);
            paths.reserve(static_cast<size_t>(count));
            for (jsize index = 0; index < count; ++index) {
                auto item = static_cast<jstring>(env->GetObjectArrayElement(media_paths, index));
                paths.push_back(from_java(env, item));
                env->DeleteLocalRef(item);
            }
        }
        const std::string result = generate_locked(prompt_text, paths, max_tokens);
        return to_java(env, result);
    } catch (const std::exception & error) {
        log_error(error.what());
        throw_illegal_state(env, error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_ai_aethra_mobile_NativeGgufRuntime_nativeCancel(JNIEnv *, jobject) {
    g_cancelled.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_ai_aethra_mobile_NativeGgufRuntime_nativeUnload(JNIEnv *, jobject) {
    // Request cancellation before waiting for the generation mutex.
    g_cancelled.store(true);
    std::lock_guard<std::mutex> lock(g_runtime_mutex);
    unload_locked();
}

extern "C" JNIEXPORT jstring JNICALL
Java_ai_aethra_mobile_NativeGgufRuntime_nativeCapabilities(JNIEnv * env, jobject) {
    std::lock_guard<std::mutex> lock(g_runtime_mutex);
    if (g_model == nullptr) return env->NewStringUTF("{}");
    const auto caps = mtmd_helper_get_capabilities(g_model, g_mtmd);
    std::ostringstream json;
    json << "{"
         << "\"chat\":" << (caps.chat ? "true" : "false") << ","
         << "\"vision\":" << (caps.vision ? "true" : "false") << ","
         << "\"audio\":" << (caps.audio ? "true" : "false") << ","
         << "\"video\":" << (caps.video ? "true" : "false") << ","
         << "\"audioStream\":" << (caps.audio_stream ? "true" : "false") << ","
         << "\"textGeneration\":" << (caps.text_gen ? "true" : "false") << ","
         << "\"audioGeneration\":" << (caps.audio_gen ? "true" : "false") << ","
         << "\"speakerReference\":" << (caps.audio_gen_spk_ref ? "true" : "false")
         << "}";
    return to_java(env, json.str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_ai_aethra_mobile_NativeGgufRuntime_nativeSystemInfo(JNIEnv * env, jobject) {
    return to_java(env, llama_print_system_info());
}
