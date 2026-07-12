# FindOpenCL.cmake - Android runtime linking via stub library
#
# On Android, libOpenCL.so is provided by the device vendor at runtime.
# We cannot link against it at build time because the file doesn't exist
# on the build machine. Instead, we build a tiny stub shared library that
# provides weak symbols to satisfy the linker. At runtime on the device,
# the real libOpenCL.so from /vendor/lib64 is loaded instead.
#
# The stub is placed in a separate directory so that Gradle does NOT
# package it into the APK.

set(OpenCL_FOUND TRUE)
set(OpenCL_INCLUDE_DIRS "${CMAKE_CURRENT_LIST_DIR}/../opencl-headers")

# Build stub shared library to satisfy the linker at build time.
# Output it to a dedicated stubs/ directory that Gradle won't pick up.
if(NOT TARGET OpenCL_stub)
    add_library(OpenCL_stub SHARED "${CMAKE_CURRENT_LIST_DIR}/../opencl-stub/opencl_stub.c")
    set_target_properties(OpenCL_stub PROPERTIES
        OUTPUT_NAME "OpenCL"
        LIBRARY_OUTPUT_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/opencl-stub-lib"
    )
    target_include_directories(OpenCL_stub PUBLIC "${OpenCL_INCLUDE_DIRS}")
endif()

# Create an INTERFACE library that links against the stub at build time
# but does not carry the stub into the final APK.
if(NOT TARGET OpenCL)
    add_library(OpenCL INTERFACE)
    target_include_directories(OpenCL INTERFACE "${OpenCL_INCLUDE_DIRS}")
    # Link against the stub .so by full path so the linker resolves symbols,
    # but set the SONAME to just "libOpenCL.so" so the runtime loader finds
    # the device vendor library instead.
    target_link_libraries(OpenCL INTERFACE OpenCL_stub)
endif()

set(OpenCL_LIBRARIES OpenCL)

message(STATUS "FindOpenCL: using stub library for build-time linking")
message(STATUS "FindOpenCL: headers at ${OpenCL_INCLUDE_DIRS}")
