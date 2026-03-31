cmake_minimum_required(VERSION 3.18.1)
project("whisper-jni")

set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

# ggml core sources (in ggml/src/)
add_library(ggml STATIC
    ${WHISPER_DIR}/ggml/src/ggml.c
    ${WHISPER_DIR}/ggml/src/ggml-alloc.c
    ${WHISPER_DIR}/ggml/src/ggml-backend.cpp
    ${WHISPER_DIR}/ggml/src/ggml-backend-reg.cpp
    ${WHISPER_DIR}/ggml/src/ggml-backend-dl.cpp
    ${WHISPER_DIR}/ggml/src/ggml-quants.c
    ${WHISPER_DIR}/ggml/src/ggml-threading.cpp
    ${WHISPER_DIR}/ggml/src/ggml-opt.cpp
    ${WHISPER_DIR}/ggml/src/gguf.cpp
    ${WHISPER_DIR}/ggml/src/ggml.cpp
)

target_include_directories(ggml PUBLIC
    ${WHISPER_DIR}/ggml/include
    ${WHISPER_DIR}/ggml/src
)
target_compile_options(ggml PRIVATE -O3)
target_compile_definitions(ggml PRIVATE
    GGML_VERSION="0.0.0"
    GGML_COMMIT="unknown"
)

# whisper core (in src/)
add_library(whisper STATIC
    ${WHISPER_DIR}/src/whisper.cpp
)

target_include_directories(whisper PUBLIC
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}/src
    ${WHISPER_DIR}/ggml/include
)
target_compile_options(whisper PRIVATE -O3)
target_link_libraries(whisper ggml)

# JNI bridge
add_library(whisper-jni SHARED whisper-jni.cpp)
target_include_directories(whisper-jni PRIVATE
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}/ggml/include
)
target_link_libraries(whisper-jni whisper ggml log android)
