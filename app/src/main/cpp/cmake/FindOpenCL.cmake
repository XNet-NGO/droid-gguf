# FindOpenCL.cmake — Android-compatible OpenCL finder
#
# On Android, builds a minimal stub libOpenCL.so for link-time resolution.
# At runtime, the device's actual libOpenCL.so is used.

set(OPENCL_HEADERS_DIR ${CMAKE_CURRENT_LIST_DIR}/../opencl-headers)

if(EXISTS "${OPENCL_HEADERS_DIR}/CL/cl.h")
    set(OpenCL_INCLUDE_DIRS "${OPENCL_HEADERS_DIR}")
else()
    find_path(OpenCL_INCLUDE_DIRS CL/cl.h)
endif()

if(ANDROID AND NOT TARGET OpenCL)
    # Build the stub .so at configure time using the NDK compiler
    set(_OPENCL_STUB_DIR "${CMAKE_CURRENT_BINARY_DIR}/_opencl_stub")
    set(_OPENCL_STUB_SRC "${CMAKE_CURRENT_LIST_DIR}/../opencl-stub/opencl_stub.c")
    set(_OPENCL_STUB_LIB "${_OPENCL_STUB_DIR}/libOpenCL.so")

    file(MAKE_DIRECTORY "${_OPENCL_STUB_DIR}")

    if(NOT EXISTS "${_OPENCL_STUB_LIB}")
        # Get the NDK compiler — CMAKE_C_COMPILER is already set by the toolchain
        execute_process(
            COMMAND ${CMAKE_C_COMPILER}
                --target=${CMAKE_C_COMPILER_TARGET}
                --sysroot=${CMAKE_SYSROOT}
                -shared
                -o "${_OPENCL_STUB_LIB}"
                "${_OPENCL_STUB_SRC}"
            RESULT_VARIABLE _OPENCL_STUB_RESULT
            ERROR_VARIABLE _OPENCL_STUB_ERROR
        )
        if(NOT _OPENCL_STUB_RESULT EQUAL 0)
            message(WARNING "Failed to build OpenCL stub: ${_OPENCL_STUB_ERROR}")
        endif()
    endif()

    if(EXISTS "${_OPENCL_STUB_LIB}")
        add_library(OpenCL SHARED IMPORTED GLOBAL)
        set_target_properties(OpenCL PROPERTIES
            IMPORTED_LOCATION "${_OPENCL_STUB_LIB}"
            IMPORTED_NO_SONAME TRUE
            INTERFACE_INCLUDE_DIRECTORIES "${OpenCL_INCLUDE_DIRS}"
        )
    else()
        # Fallback: interface library (may fail at link)
        add_library(OpenCL INTERFACE IMPORTED GLOBAL)
        target_include_directories(OpenCL INTERFACE "${OpenCL_INCLUDE_DIRS}")
    endif()
    set(OpenCL_LIBRARIES OpenCL)
elseif(NOT ANDROID)
    find_library(OpenCL_LIBRARIES NAMES OpenCL)
endif()

set(OpenCL_FOUND TRUE)

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(OpenCL
    REQUIRED_VARS OpenCL_INCLUDE_DIRS
)

mark_as_advanced(OpenCL_INCLUDE_DIRS OpenCL_LIBRARIES)
