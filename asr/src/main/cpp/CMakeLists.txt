cmake_minimum_required(VERSION 3.18.1)
project("whisper-jni")

set(CMAKE_C_VISIBILITY_PRESET hidden)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN 1)

set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

# Enable 16KB page alignment for Android 15+ compatibility
if(ANDROID)
    add_link_options("-Wl,-z,max-page-size=16384")
endif()

# ggml core sources
file(GLOB GGML_SOURCES CONFIGURE_DEPENDS
    "${WHISPER_DIR}/ggml/src/*.c"
    "${WHISPER_DIR}/ggml/src/*.cpp"
    "${WHISPER_DIR}/ggml/src/ggml-cpu/*.c"
    "${WHISPER_DIR}/ggml/src/ggml-cpu/*.cpp"
)
if(NOT GGML_SOURCES)
    message(FATAL_ERROR "No ggml sources found — did you clone whisper.cpp?")
endif()

add_library(ggml_whisper STATIC ${GGML_SOURCES})
target_include_directories(ggml_whisper PUBLIC
    ${WHISPER_DIR}/ggml/include
    ${WHISPER_DIR}/ggml/src
    ${WHISPER_DIR}/ggml/src/ggml-cpu
)
target_compile_options(ggml_whisper PRIVATE -O3)
target_compile_definitions(ggml_whisper PRIVATE GGML_VERSION="0.0.0" GGML_COMMIT="unknown" GGML_BUILD_NUMBER=0)

# whisper core sources
file(GLOB WHISPER_SOURCES CONFIGURE_DEPENDS
    "${WHISPER_DIR}/src/*.c"
    "${WHISPER_DIR}/src/*.cpp"
)
if(NOT WHISPER_SOURCES)
    message(FATAL_ERROR "No whisper sources found — did you clone whisper.cpp?")
endif()

add_library(whisper STATIC ${WHISPER_SOURCES})
target_include_directories(whisper PUBLIC
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}/src
    ${WHISPER_DIR}/ggml/include
    ${WHISPER_DIR}/ggml/src
    ${WHISPER_DIR}/ggml/src/ggml-cpu
)
target_compile_options(whisper PRIVATE -O3)
target_compile_definitions(whisper PRIVATE WHISPER_VERSION="0.0.0" WHISPER_COMMIT="unknown")
target_link_libraries(whisper ggml_whisper)

# JNI bridge
add_library(whisper-jni SHARED whisper-jni.cpp)
target_include_directories(whisper-jni PRIVATE
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}/src
    ${WHISPER_DIR}/ggml/include
)
target_link_libraries(whisper-jni whisper ggml_whisper log android)

# Explicitly apply 16KB alignment to the shared library
if(ANDROID)
    target_link_options(whisper-jni PRIVATE "-Wl,-z,max-page-size=16384")
endif()
