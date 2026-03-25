cmake_minimum_required(VERSION 3.18.1)
project("whisper-jni")

set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

# Minimal source files required from whisper.cpp
add_library(whisper
    STATIC
    ${WHISPER_DIR}/whisper.cpp
    ${WHISPER_DIR}/ggml.c
    ${WHISPER_DIR}/ggml-alloc.c
    ${WHISPER_DIR}/ggml-backend.c
    ${WHISPER_DIR}/ggml-quants.c
)

target_include_directories(whisper PUBLIC ${WHISPER_DIR})
target_compile_options(whisper PRIVATE -O3)

add_library(whisper-jni SHARED whisper-jni.cpp)
target_link_libraries(whisper-jni whisper log)
