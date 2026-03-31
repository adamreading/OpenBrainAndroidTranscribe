cmake_minimum_required(VERSION 3.18.1)
project("whisper-jni")

set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

# whisper.cpp source files — use file(GLOB) to handle both old and new ggml structures.
# Developer must clone whisper.cpp into asr/src/main/cpp/whisper.cpp/ before building.
# See BUILDING.md for instructions.
file(GLOB GGML_SOURCES
    "${WHISPER_DIR}/ggml*.c"
    "${WHISPER_DIR}/ggml*.cpp"
    "${WHISPER_DIR}/ggml/src/*.c"
    "${WHISPER_DIR}/ggml/src/*.cpp"
)

add_library(whisper STATIC
    ${WHISPER_DIR}/whisper.cpp
    ${GGML_SOURCES}
)

# Include both old-style (flat) and new-style (ggml/include) header locations
target_include_directories(whisper PUBLIC
    ${WHISPER_DIR}
    ${WHISPER_DIR}/ggml/include
)
target_compile_options(whisper PRIVATE -O3)
set_target_properties(whisper PROPERTIES LINKER_LANGUAGE CXX)

add_library(whisper-jni SHARED whisper-jni.cpp)
target_link_libraries(whisper-jni whisper log android)
