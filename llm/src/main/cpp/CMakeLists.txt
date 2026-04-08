cmake_minimum_required(VERSION 3.18.1)
project("llama-jni")

set(CMAKE_C_VISIBILITY_PRESET hidden)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN 1)

set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)

# Enable 16KB page alignment for Android 15+ compatibility
if(ANDROID)
    add_link_options("-Wl,-z,max-page-size=16384")
endif()

# ggml core sources
file(GLOB GGML_SOURCES CONFIGURE_DEPENDS
    "${LLAMA_DIR}/ggml/src/*.c"
    "${LLAMA_DIR}/ggml/src/*.cpp"
    "${LLAMA_DIR}/ggml/src/ggml-cpu/*.c"
    "${LLAMA_DIR}/ggml/src/ggml-cpu/*.cpp"
)
if(NOT GGML_SOURCES)
    message(FATAL_ERROR "No ggml sources found — did you clone llama.cpp?")
endif()

add_library(ggml_llama STATIC ${GGML_SOURCES})
target_include_directories(ggml_llama PUBLIC
    ${LLAMA_DIR}/ggml/include
    ${LLAMA_DIR}/ggml/src
    ${LLAMA_DIR}/ggml/src/ggml-cpu
)
target_compile_options(ggml_llama PRIVATE -O3)
target_compile_definitions(ggml_llama PRIVATE GGML_VERSION="0.0.0" GGML_COMMIT="unknown" GGML_BUILD_NUMBER=0)

# llama core sources
file(GLOB LLAMA_SOURCES CONFIGURE_DEPENDS
    "${LLAMA_DIR}/src/*.c"
    "${LLAMA_DIR}/src/*.cpp"
    "${LLAMA_DIR}/src/models/*.cpp"
)
if(NOT LLAMA_SOURCES)
    message(FATAL_ERROR "No llama sources found — did you clone llama.cpp?")
endif()

add_library(llama STATIC ${LLAMA_SOURCES})
target_include_directories(llama PUBLIC
    ${LLAMA_DIR}/include
    ${LLAMA_DIR}/src
    ${LLAMA_DIR}/src/models
    ${LLAMA_DIR}/ggml/include
    ${LLAMA_DIR}/ggml/src
    ${LLAMA_DIR}/ggml/src/ggml-cpu
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

# Explicitly apply 16KB alignment to the shared library
if(ANDROID)
    target_link_options(llama-jni PRIVATE "-Wl,-z,max-page-size=16384")
endif()
