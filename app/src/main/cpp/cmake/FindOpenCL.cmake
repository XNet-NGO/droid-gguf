# FindOpenCL for Android - libOpenCL.so is a public vendor library
# Listed in /vendor/etc/public.libraries.txt, accessible to apps

find_library(OpenCL_LIBRARY OpenCL)

if(NOT OpenCL_LIBRARY)
    # Fallback: explicitly point to vendor path
    set(OpenCL_LIBRARY "/vendor/lib64/libOpenCL.so")
endif()

set(OpenCL_FOUND TRUE)
set(OpenCL_INCLUDE_DIRS ${CMAKE_CURRENT_LIST_DIR}/../opencl-headers)
set(OpenCL_LIBRARIES ${OpenCL_LIBRARY})

message(STATUS "OpenCL found: ${OpenCL_LIBRARY}")
message(STATUS "OpenCL headers: ${OpenCL_INCLUDE_DIRS}")
