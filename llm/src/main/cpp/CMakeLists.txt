cmake_minimum_required(VERSION 3.18.1)
project("llama-jni")

set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)

# ggml core sources (shared with llama)
add_library(ggml_llama STATIC
    ${LLAMA_DIR}/ggml/src/ggml.c
    ${LLAMA_DIR}/ggml/src/ggml-alloc.c
    ${LLAMA_DIR}/ggml/src/ggml-backend.cpp
    ${LLAMA_DIR}/ggml/src/ggml-backend-reg.cpp
    ${LLAMA_DIR}/ggml/src/ggml-backend-dl.cpp
    ${LLAMA_DIR}/ggml/src/ggml-quants.c
    ${LLAMA_DIR}/ggml/src/ggml-threading.cpp
    ${LLAMA_DIR}/ggml/src/ggml-opt.cpp
    ${LLAMA_DIR}/ggml/src/gguf.cpp
    ${LLAMA_DIR}/ggml/src/ggml.cpp
)

target_include_directories(ggml_llama PUBLIC
    ${LLAMA_DIR}/ggml/include
    ${LLAMA_DIR}/ggml/src
)
target_compile_options(ggml_llama PRIVATE -O3)
target_compile_definitions(ggml_llama PRIVATE
    GGML_VERSION="0.0.0"
    GGML_COMMIT="unknown"
    GGML_BUILD_NUMBER=0
)

# llama core sources (in src/)
add_library(llama STATIC
    ${LLAMA_DIR}/src/llama.cpp
    ${LLAMA_DIR}/src/llama-model.cpp
    ${LLAMA_DIR}/src/llama-model-loader.cpp
    ${LLAMA_DIR}/src/llama-context.cpp
    ${LLAMA_DIR}/src/llama-kv-cache.cpp
    ${LLAMA_DIR}/src/llama-graph.cpp
    ${LLAMA_DIR}/src/llama-adapter.cpp
    ${LLAMA_DIR}/src/llama-arch.cpp
    ${LLAMA_DIR}/src/llama-batch.cpp
    ${LLAMA_DIR}/src/llama-chat.cpp
    ${LLAMA_DIR}/src/llama-grammar.cpp
    ${LLAMA_DIR}/src/llama-hparams.cpp
    ${LLAMA_DIR}/src/llama-impl.cpp
    ${LLAMA_DIR}/src/llama-io.cpp
    ${LLAMA_DIR}/src/llama-memory.cpp
    ${LLAMA_DIR}/src/llama-mmap.cpp
    ${LLAMA_DIR}/src/llama-quant.cpp
    ${LLAMA_DIR}/src/llama-sampler.cpp
    ${LLAMA_DIR}/src/llama-vocab.cpp
    ${LLAMA_DIR}/src/llama-cparams.cpp
    ${LLAMA_DIR}/src/llama-model-saver.cpp
    ${LLAMA_DIR}/src/llama-memory-hybrid.cpp
    ${LLAMA_DIR}/src/llama-memory-hybrid-iswa.cpp
    ${LLAMA_DIR}/src/llama-memory-recurrent.cpp
    ${LLAMA_DIR}/src/llama-kv-cache-iswa.cpp
    ${LLAMA_DIR}/src/unicode.cpp
    ${LLAMA_DIR}/src/unicode-data.cpp
)

target_include_directories(llama PUBLIC
    ${LLAMA_DIR}/include
    ${LLAMA_DIR}/src
    ${LLAMA_DIR}/ggml/include
    ${LLAMA_DIR}/ggml/src
)
target_compile_options(llama PRIVATE -O3)
set_target_properties(llama PROPERTIES LINKER_LANGUAGE CXX)
target_link_libraries(llama ggml_llama)

# JNI bridge
add_library(llama-jni SHARED llama-jni.cpp)
target_include_directories(llama-jni PRIVATE
    ${LLAMA_DIR}/include
    ${LLAMA_DIR}/src
    ${LLAMA_DIR}/ggml/include
)
target_link_libraries(llama-jni llama ggml_llama log android)
