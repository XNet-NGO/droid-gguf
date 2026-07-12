// Stub OpenCL library for Android cross-compilation linking.
// At runtime, the device's actual libOpenCL.so will be loaded.
// This file just provides weak symbol stubs so the linker succeeds.

#include <stddef.h>

// We only need to export the symbols used by ggml-opencl.
// Using weak definitions ensures they get overridden by the real lib at runtime.

typedef int cl_int;
typedef unsigned int cl_uint;
typedef void* cl_platform_id;
typedef void* cl_device_id;
typedef void* cl_context;
typedef void* cl_command_queue;
typedef void* cl_mem;
typedef void* cl_program;
typedef void* cl_kernel;
typedef void* cl_event;
typedef unsigned long cl_ulong;
typedef unsigned long cl_bitfield;

#define CL_STUB __attribute__((weak, visibility("default")))

CL_STUB cl_int clGetPlatformIDs(cl_uint a, cl_platform_id* b, cl_uint* c) { return -1; }
CL_STUB cl_int clGetPlatformInfo(cl_platform_id a, cl_uint b, size_t c, void* d, size_t* e) { return -1; }
CL_STUB cl_int clGetDeviceIDs(cl_platform_id a, cl_bitfield b, cl_uint c, cl_device_id* d, cl_uint* e) { return -1; }
CL_STUB cl_int clGetDeviceInfo(cl_device_id a, cl_uint b, size_t c, void* d, size_t* e) { return -1; }
CL_STUB cl_context clCreateContext(const void* a, cl_uint b, const cl_device_id* c, void* d, void* e, cl_int* f) { return NULL; }
CL_STUB cl_int clReleaseContext(cl_context a) { return -1; }
CL_STUB cl_command_queue clCreateCommandQueueWithProperties(cl_context a, cl_device_id b, const cl_ulong* c, cl_int* d) { return NULL; }
CL_STUB cl_int clReleaseCommandQueue(cl_command_queue a) { return -1; }
CL_STUB cl_mem clCreateBuffer(cl_context a, cl_bitfield b, size_t c, void* d, cl_int* e) { return NULL; }
CL_STUB cl_int clReleaseMemObject(cl_mem a) { return -1; }
CL_STUB cl_program clCreateProgramWithSource(cl_context a, cl_uint b, const char** c, const size_t* d, cl_int* e) { return NULL; }
CL_STUB cl_program clCreateProgramWithBinary(cl_context a, cl_uint b, const cl_device_id* c, const size_t* d, const unsigned char** e, cl_int* f, cl_int* g) { return NULL; }
CL_STUB cl_int clBuildProgram(cl_program a, cl_uint b, const cl_device_id* c, const char* d, void* e, void* f) { return -1; }
CL_STUB cl_int clGetProgramBuildInfo(cl_program a, cl_device_id b, cl_uint c, size_t d, void* e, size_t* f) { return -1; }
CL_STUB cl_int clGetProgramInfo(cl_program a, cl_uint b, size_t c, void* d, size_t* e) { return -1; }
CL_STUB cl_int clReleaseProgram(cl_program a) { return -1; }
CL_STUB cl_kernel clCreateKernel(cl_program a, const char* b, cl_int* c) { return NULL; }
CL_STUB cl_int clReleaseKernel(cl_kernel a) { return -1; }
CL_STUB cl_int clSetKernelArg(cl_kernel a, cl_uint b, size_t c, const void* d) { return -1; }
CL_STUB cl_int clEnqueueNDRangeKernel(cl_command_queue a, cl_kernel b, cl_uint c, const size_t* d, const size_t* e, const size_t* f, cl_uint g, const cl_event* h, cl_event* i) { return -1; }
CL_STUB cl_int clEnqueueReadBuffer(cl_command_queue a, cl_mem b, cl_uint c, size_t d, size_t e, void* f, cl_uint g, const cl_event* h, cl_event* i) { return -1; }
CL_STUB cl_int clEnqueueWriteBuffer(cl_command_queue a, cl_mem b, cl_uint c, size_t d, size_t e, const void* f, cl_uint g, const cl_event* h, cl_event* i) { return -1; }
CL_STUB cl_int clEnqueueCopyBuffer(cl_command_queue a, cl_mem b, cl_mem c, size_t d, size_t e, size_t f, cl_uint g, const cl_event* h, cl_event* i) { return -1; }
CL_STUB cl_int clFinish(cl_command_queue a) { return -1; }
CL_STUB cl_int clFlush(cl_command_queue a) { return -1; }
CL_STUB cl_int clWaitForEvents(cl_uint a, const cl_event* b) { return -1; }
CL_STUB cl_int clReleaseEvent(cl_event a) { return -1; }
CL_STUB cl_int clRetainEvent(cl_event a) { return -1; }
CL_STUB cl_int clGetEventProfilingInfo(cl_event a, cl_uint b, size_t c, void* d, size_t* e) { return -1; }
CL_STUB cl_int clEnqueueMapBuffer(cl_command_queue a, cl_mem b, cl_uint c, cl_bitfield d, size_t e, size_t f, cl_uint g, const cl_event* h, cl_event* i, cl_int* j) { return -1; }
CL_STUB cl_int clEnqueueUnmapMemObject(cl_command_queue a, cl_mem b, void* c, cl_uint d, const cl_event* e, cl_event* f) { return -1; }
CL_STUB cl_int clEnqueueFillBuffer(cl_command_queue a, cl_mem b, const void* c, size_t d, size_t e, size_t f, cl_uint g, const cl_event* h, cl_event* i) { return -1; }
CL_STUB cl_mem clCreateSubBuffer(cl_mem a, cl_bitfield b, cl_uint c, const void* d, cl_int* e) { return NULL; }
CL_STUB cl_int clRetainMemObject(cl_mem a) { return -1; }
CL_STUB cl_int clGetMemObjectInfo(cl_mem a, cl_uint b, size_t c, void* d, size_t* e) { return -1; }
CL_STUB cl_int clGetKernelWorkGroupInfo(cl_kernel a, cl_device_id b, cl_uint c, size_t d, void* e, size_t* f) { return -1; }
CL_STUB cl_int clEnqueueMarkerWithWaitList(cl_command_queue a, cl_uint b, const cl_event* c, cl_event* d) { return -1; }
CL_STUB cl_int clEnqueueBarrierWithWaitList(cl_command_queue a, cl_uint b, const cl_event* c, cl_event* d) { return -1; }
CL_STUB void* clSVMAlloc(cl_context a, cl_bitfield b, size_t c, cl_uint d) { return NULL; }
CL_STUB void clSVMFree(cl_context a, void* b) {}
CL_STUB cl_int clEnqueueSVMMap(cl_command_queue a, cl_uint b, cl_bitfield c, void* d, size_t e, cl_uint f, const cl_event* g, cl_event* h) { return -1; }
CL_STUB cl_int clEnqueueSVMUnmap(cl_command_queue a, void* b, cl_uint c, const cl_event* d, cl_event* e) { return -1; }
CL_STUB cl_int clSetKernelArgSVMPointer(cl_kernel a, cl_uint b, const void* c) { return -1; }
CL_STUB cl_int clEnqueueSVMMemcpy(cl_command_queue a, cl_uint b, void* c, const void* d, size_t e, cl_uint f, const cl_event* g, cl_event* h) { return -1; }
CL_STUB cl_int clRetainContext(cl_context a) { return -1; }
CL_STUB cl_int clRetainCommandQueue(cl_command_queue a) { return -1; }
CL_STUB cl_int clRetainProgram(cl_program a) { return -1; }
CL_STUB cl_int clRetainKernel(cl_kernel a) { return -1; }
CL_STUB cl_int clGetContextInfo(cl_context a, cl_uint b, size_t c, void* d, size_t* e) { return -1; }
CL_STUB cl_int clGetCommandQueueInfo(cl_command_queue a, cl_uint b, size_t c, void* d, size_t* e) { return -1; }
CL_STUB cl_int clGetKernelInfo(cl_kernel a, cl_uint b, size_t c, void* d, size_t* e) { return -1; }
CL_STUB cl_int clGetEventInfo(cl_event a, cl_uint b, size_t c, void* d, size_t* e) { return -1; }
CL_STUB cl_command_queue clCreateCommandQueue(cl_context a, cl_device_id b, cl_bitfield c, cl_int* d) { return NULL; }
CL_STUB cl_mem clCreateImage(cl_context a, cl_bitfield b, const void* c, const void* d, void* e, cl_int* f) { return NULL; }
CL_STUB cl_mem clCreateBufferWithProperties(cl_context a, const cl_ulong* b, cl_bitfield c, size_t d, void* e, cl_int* f) { return NULL; }
CL_STUB cl_int clEnqueueReadImage(cl_command_queue a, cl_mem b, cl_uint c, const size_t* d, const size_t* e, size_t f, size_t g, void* h, cl_uint i, const cl_event* j, cl_event* k) { return -1; }
CL_STUB cl_int clEnqueueWriteImage(cl_command_queue a, cl_mem b, cl_uint c, const size_t* d, const size_t* e, size_t f, size_t g, const void* h, cl_uint i, const cl_event* j, cl_event* k) { return -1; }
CL_STUB cl_int clGetImageInfo(cl_mem a, cl_uint b, size_t c, void* d, size_t* e) { return -1; }
CL_STUB cl_mem clCreateImage2D(cl_context a, cl_bitfield b, const void* c, size_t d, size_t e, size_t f, void* g, cl_int* h) { return NULL; }
CL_STUB cl_int clSetEventCallback(cl_event a, cl_int b, void (*c)(cl_event, cl_int, void*), void* d) { return -1; }
CL_STUB cl_event clCreateUserEvent(cl_context a, cl_int* b) { return NULL; }
CL_STUB cl_int clSetUserEventStatus(cl_event a, cl_int b) { return -1; }
CL_STUB cl_int clEnqueueSVMMemFill(cl_command_queue a, void* b, const void* c, size_t d, size_t e, cl_uint f, const cl_event* g, cl_event* h) { return -1; }
