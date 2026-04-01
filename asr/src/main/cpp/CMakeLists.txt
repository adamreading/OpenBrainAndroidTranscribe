cmake_minimum_required(VERSION 3.18.1)
project("whisper-jni")

set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

# ggml core sources — GLOB to automatically pick up all source files.
# When updating whisper.cpp, new ggml source files are included automatically.
file(GLOB GGML_SOURCES
    "${WHISPER_DIR}/ggml/src/*.c"
    "${WHISPER_DIR}/ggml/src/*.cpp"
)

add_library(ggml STATIC ${GGML_SOURCES})

target_include_directories(ggml PUBLIC
    ${WHISPER_DIR}/ggml/include
    ${WHISPER_DIR}/ggml/src
)
target_compile_options(ggml PRIVATE -O3)
target_compile_definitions(ggml PRIVATE
    GGML_VERSION="0.0.0"
    GGML_COMMIT="unknown"
    GGML_BUILD_NUMBER=0
)

# whisper core sources — GLOB to automatically pick up all source files.
# When updating whisper.cpp, new source files are included automatically.
file(GLOB WHISPER_SOURCES
    "${WHISPER_DIR}/src/*.c"
    "${WHISPER_DIR}/src/*.cpp"
)

add_library(whisper STATIC ${WHISPER_SOURCES})

target_include_directories(whisper PUBLIC
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}/src
    ${WHISPER_DIR}/ggml/include
    ${WHISPER_DIR}/ggml/src
)
target_compile_options(whisper PRIVATE -O3)
target_compile_definitions(whisper PRIVATE
    WHISPER_VERSION="0.0.0"
    WHISPER_COMMIT="unknown"
)
target_link_libraries(whisper ggml)

# JNI bridge
add_library(whisper-jni SHARED whisper-jni.cpp)
target_include_directories(whisper-jni PRIVATE
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}/src
    ${WHISPER_DIR}/ggml/include
)
target_link_libraries(whisper-jni whisper ggml log android)
