cmake_minimum_required(VERSION 3.22.1)
project(aethra_llama_jni LANGUAGES C CXX)

include(FetchContent)

# Keep the native ABI reproducible while model/projector compatibility is validated.
set(AETHRA_LLAMA_CPP_COMMIT "8642541b255bfb5dc39cd351a178be09013a55d6" CACHE STRING "Pinned llama.cpp commit")

set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_SUBPROCESS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_MTMD ON CACHE BOOL "" FORCE)
set(MTMD_VIDEO OFF CACHE BOOL "" FORCE)

if(DEFINED LLAMA_CPP_SOURCE_DIR AND EXISTS "\${LLAMA_CPP_SOURCE_DIR}/CMakeLists.txt")
    add_subdirectory("\${LLAMA_CPP_SOURCE_DIR}" "\${CMAKE_BINARY_DIR}/llama.cpp")
else()
    FetchContent_Declare(
        llama_cpp
        GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
        GIT_TAG \${AETHRA_LLAMA_CPP_COMMIT}
        GIT_SHALLOW FALSE
    )
    FetchContent_MakeAvailable(llama_cpp)
endif()

add_library(aethra_llama_jni SHARED native_runtime.cpp)

target_compile_features(aethra_llama_jni PRIVATE cxx_std_17)
target_include_directories(aethra_llama_jni PRIVATE
    "\${LLAMA_CPP_SOURCE_DIR}/include"
    "\${LLAMA_CPP_SOURCE_DIR}/tools/mtmd"
)

if(NOT DEFINED LLAMA_CPP_SOURCE_DIR)
    target_include_directories(aethra_llama_jni PRIVATE
        "\${llama_cpp_SOURCE_DIR}/include"
        "\${llama_cpp_SOURCE_DIR}/tools/mtmd"
    )
endif()

target_link_libraries(aethra_llama_jni PRIVATE llama mtmd android log)
