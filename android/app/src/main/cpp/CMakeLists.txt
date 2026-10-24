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
set(SDL3_DIR "${REXSDK_DIR}/thirdparty/sdl3" CACHE PATH
    "SDL3 source tree (vendored by the ReXGlue SDK)")

if(NOT EXISTS "${SDL3_DIR}/CMakeLists.txt")
    message(FATAL_ERROR
        "SDL3 not found at ${SDL3_DIR}\n"
        "Run: git submodule update --init --depth 1 tools/rexglue-source\n"
        "     git -C tools/rexglue-source submodule update --init --depth 1 thirdparty/sdl3")
endif()

# M0a builds SDL3 statically into libmain.so. This avoids shipping a separate
# libSDL3.so and matches the eventual single-entry-point design.
set(SDL_SHARED OFF CACHE BOOL "" FORCE)
set(SDL_STATIC ON CACHE BOOL "" FORCE)
set(SDL_TEST OFF CACHE BOOL "" FORCE)
set(SDL_TESTS OFF CACHE BOOL "" FORCE)
set(SDL_INSTALL OFF CACHE BOOL "" FORCE)
set(SDL_VIDEO ON CACHE BOOL "" FORCE)
set(SDL_AUDIO ON CACHE BOOL "" FORCE)
set(SDL_JOYSTICK ON CACHE BOOL "" FORCE)
set(SDL_HAPTIC ON CACHE BOOL "" FORCE)
set(SDL_SENSOR ON CACHE BOOL "" FORCE)
set(SDL_POWER ON CACHE BOOL "" FORCE)

add_subdirectory("${SDL3_DIR}" sdl3 EXCLUDE_FROM_ALL)

add_library(main SHARED
    src/main.cpp
)

target_compile_definitions(main PRIVATE
    PROJECT1944_ANDROID=1
)
target_compile_options(main PRIVATE
    -Wall -Wextra -Wno-unused-parameter
)
target_link_libraries(main PRIVATE SDL3::SDL3)
target_link_options(main PRIVATE
    -Wl,--export-dynamic
)

set_target_properties(main PROPERTIES
    OUTPUT_NAME "main"
    CXX_VISIBILITY_PRESET default
    VISIBILITY_INLINES_HIDDEN OFF
)
