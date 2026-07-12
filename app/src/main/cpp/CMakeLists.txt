cmake_minimum_required(VERSION 3.22.1)
project(droid-gguf C CXX)

set(LLAMA_CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)

# =============================================================================
# Help CMake find OpenCL on Android
# =============================================================================

# Point to our custom FindOpenCL module
list(APPEND CMAKE_MODULE_PATH ${CMAKE_CURRENT_SOURCE_DIR}/cmake)

# =============================================================================
# llama.cpp build options: single static build with CPU + OpenCL backends
# =============================================================================

# Disable building tools/tests/examples — we only need the library
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP      OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)

# GGML backend options
set(GGML_OPENCL    ON  CACHE BOOL "" FORCE)
set(GGML_OPENMP    ON  CACHE BOOL "" FORCE)
set(GGML_NATIVE    OFF CACHE BOOL "" FORCE)

# Disable backends we don't need
set(GGML_VULKAN    OFF CACHE BOOL "" FORCE)
set(GGML_METAL     OFF CACHE BOOL "" FORCE)
set(GGML_CUDA      OFF CACHE BOOL "" FORCE)
set(GGML_SYCL      OFF CACHE BOOL "" FORCE)
set(GGML_RPC       OFF CACHE BOOL "" FORCE)

# Add llama.cpp as a subdirectory — builds ggml + llama as static libs
add_subdirectory(${LLAMA_CPP_DIR} llama.cpp)

# =============================================================================
# libdroid-gguf.so: single JNI shared library with CPU + OpenCL backends
# =============================================================================

add_library(droid-gguf SHARED jni_bridge.cpp)

target_include_directories(droid-gguf PRIVATE
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/ggml/include
)

# Link against the llama static library (which transitively links ggml backends)
# Also explicitly link OpenCL stub for the final .so (device provides real one)
target_link_libraries(droid-gguf PRIVATE
    llama
    log
    android
)
