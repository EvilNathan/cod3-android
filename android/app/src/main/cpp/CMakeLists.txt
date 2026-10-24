cmake_minimum_required(VERSION 3.25)
project(project1944_android LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Gradle passes the repository root. The fallback keeps direct CMake use sane.
set(REX_PROJECT_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../.." CACHE PATH
    "Project 1944 Android repository root")
get_filename_component(REX_PROJECT_ROOT "${REX_PROJECT_ROOT}" ABSOLUTE)

set(REXSDK_DIR "${REX_PROJECT_ROOT}/tools/rexglue-source" CACHE PATH
    "Pinned ReXGlue SDK source tree")

if(NOT EXISTS "${REXSDK_DIR}/CMakeLists.txt")
    message(FATAL_ERROR
        "ReXGlue SDK not found at ${REXSDK_DIR}\n"
        "Run: git submodule update --init --recursive tools/rexglue-source")
endif()

# ReXGlue Android build options. SDL3 is built statically by the SDK's
# thirdparty/CMakeLists.txt into libmain.so.
set(REXGLUE_USE_VULKAN ON CACHE BOOL "" FORCE)
set(REXGLUE_ENABLE_TRACY OFF CACHE BOOL "" FORCE)
set(REXGLUE_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(REXGLUE_ENABLE_PERF_COUNTERS OFF CACHE BOOL "" FORCE)
set(SDL_SHARED OFF CACHE BOOL "" FORCE)
set(SDL_STATIC ON CACHE BOOL "" FORCE)
set(SDL_TEST OFF CACHE BOOL "" FORCE)
set(SDL_TESTS OFF CACHE BOOL "" FORCE)
set(SDL_INSTALL OFF CACHE BOOL "" FORCE)
set(SDL_VIDEO ON CACHE BOOL "" FORCE)
set(SDL_AUDIO ON CACHE BOOL "" FORCE)
set(SDL_JOYSTICK ON CACHE BOOL "" FORCE)
set(SDL_SENSOR ON CACHE BOOL "" FORCE)
set(SDL_POWER ON CACHE BOOL "" FORCE)

add_subdirectory("${REXSDK_DIR}" rexglue EXCLUDE_FROM_ALL)

add_library(main SHARED
    src/main.cpp
)

target_compile_definitions(main PRIVATE
    PROJECT1944_ANDROID=1
)
target_compile_options(main PRIVATE
    -Wall -Wextra -Wno-unused-parameter
)

# rex::ui is an OBJECT library: its objects are embedded into libmain.so.
# rex::runtime is shared and is packaged alongside libmain.so by Gradle.
target_link_libraries(main PRIVATE
    rex::ui
    rex::runtime
    SDL3::SDL3
)

target_link_options(main PRIVATE
    -Wl,--export-dynamic
)

set_target_properties(main PROPERTIES
    OUTPUT_NAME "main"
    CXX_VISIBILITY_PRESET default
    VISIBILITY_INLINES_HIDDEN OFF
)
