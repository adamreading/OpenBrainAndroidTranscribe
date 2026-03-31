cmake_minimum_required(VERSION 3.18.1)
project("llama-jni")

# llama.cpp source directory — developer must clone llama.cpp here before building.
# See BUILDING.md for instructions:
#   cd llm/src/main/cpp
#   git clone https://github.com/ggerganov/llama.cpp.git
set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)

# Core llama.cpp files — use file(GLOB) to handle varying source layouts
file(GLOB GGML_SOURCES
    "${LLAMA_DIR}/ggml*.c"
    "${LLAMA_DIR}/ggml*.cpp"
    "${LLAMA_DIR}/ggml/src/*.c"
    "${LLAMA_DIR}/ggml/src/*.cpp"
)

add_library(llama STATIC
    ${LLAMA_DIR}/llama.cpp
    ${GGML_SOURCES}
)

target_include_directories(llama PUBLIC
    ${LLAMA_DIR}
    ${LLAMA_DIR}/ggml/include
)
target_compile_options(llama PRIVATE -O3)
set_target_properties(llama PROPERTIES LINKER_LANGUAGE CXX)

add_library(llama-jni SHARED llama-jni.cpp)
target_link_libraries(llama-jni llama log android)
