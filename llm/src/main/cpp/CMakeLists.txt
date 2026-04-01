cmake_minimum_required(VERSION 3.18.1)
project("llama-jni")

set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)

# ggml core sources — GLOB to automatically pick up all source files.
# When updating llama.cpp, new ggml source files are included automatically.
file(GLOB GGML_SOURCES CONFIGURE_DEPENDS
    "${LLAMA_DIR}/ggml/src/*.c"
    "${LLAMA_DIR}/ggml/src/*.cpp"
)
if(NOT GGML_SOURCES)
    message(FATAL_ERROR "No ggml sources found in ${LLAMA_DIR}/ggml/src/ — did you clone llama.cpp?")
endif()

add_library(ggml_llama STATIC ${GGML_SOURCES})

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

# llama core sources — GLOB to automatically pick up all source files.
# This includes per-architecture model builders (llm_build_bert, llm_build_llama, etc.)
# that previously caused linker errors when listed individually.
file(GLOB LLAMA_SOURCES CONFIGURE_DEPENDS
    "${LLAMA_DIR}/src/*.c"
    "${LLAMA_DIR}/src/*.cpp"
)
if(NOT LLAMA_SOURCES)
    message(FATAL_ERROR "No llama sources found in ${LLAMA_DIR}/src/ — did you clone llama.cpp?")
endif()

add_library(llama STATIC ${LLAMA_SOURCES})

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
