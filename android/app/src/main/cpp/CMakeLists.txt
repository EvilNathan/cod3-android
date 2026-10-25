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

# Generated guest code lives outside the repository. Point COD3_GENERATED_DIR
# at the output of `rexglue codegen` (environment variable or CMake cache).
set(COD3_GENERATED_DIR "$ENV{COD3_GENERATED_DIR}" CACHE PATH
    "Directory containing the generated default/ and mission module sources")
if(NOT COD3_GENERATED_DIR)
    set(COD3_GENERATED_DIR "${REX_PROJECT_ROOT}/cod3-pc/generated")
endif()

add_subdirectory("${REXSDK_DIR}" rexglue EXCLUDE_FROM_ALL)

# The SDK installs ReXApp as a source file for consumers; rexruntime does not
# contain it, so each host application compiles it into its own image.
set(COD3_COROUTINE_DIR "${REX_PROJECT_ROOT}/integration/coroutines")

add_library(main SHARED
    src/main.cpp
    "${REXSDK_DIR}/src/ui/rex_app.cpp"
)

# Recompiled guest code for the entrypoint (default.xex). The generated sources
# are architecture-neutral C++; this target compiles them for arm64-v8a.
if(EXISTS "${COD3_GENERATED_DIR}/default/sources.cmake")
    include("${COD3_GENERATED_DIR}/default/sources.cmake")
    add_library(cod3_default OBJECT ${GENERATED_SOURCES})
    target_include_directories(cod3_default PRIVATE
        "${COD3_GENERATED_DIR}/default"
    )
    target_link_libraries(cod3_default PRIVATE rex::runtime)
    target_precompile_headers(cod3_default PRIVATE
        "${COD3_GENERATED_DIR}/default/cod3_pc_pch.h"
    )
    target_sources(main PRIVATE $<TARGET_OBJECTS:cod3_default>)
    message(STATUS "COD3 guest code: ${COD3_GENERATED_DIR}/default")

    # Coroutine bridge: the generated scheduler calls into it, and the custom
    # PCH interposes setjmp/longjmp, so it must live in the same image.
    target_sources(main PRIVATE
        "${COD3_COROUTINE_DIR}/cod3_coroutines.cpp"
        "${COD3_COROUTINE_DIR}/main_hooks.cpp"
    )
    target_include_directories(main PRIVATE
        "${COD3_GENERATED_DIR}/default"
        "${COD3_COROUTINE_DIR}"
    )

    # The prebuilt SDK's exported targets do not carry its source-tree global
    # FP and aliasing flags. Generated guest code relies on dynamic FP modes
    # and modulo signed-register arithmetic.
    foreach(_cod3_target cod3_default main)
        target_compile_options(${_cod3_target} PRIVATE
            -ffp-model=strict -fno-strict-aliasing -fwrapv)
    endforeach()
else()
    message(WARNING
        "No generated guest code at ${COD3_GENERATED_DIR}/default. "
        "The APK will only present a cleared swapchain. Run rexglue codegen first.")
endif()

target_compile_definitions(main PRIVATE
    PROJECT1944_ANDROID=1
    # rex/version.h builds its title/stamp strings from this macro, which the
    # SDK only defines for its own targets.
    REXGLUE_BUILD_CONFIG="${CMAKE_BUILD_TYPE}"
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

# The Xenos plugin is loaded at runtime by name from the native library
# directory; make sure Gradle's build produces it.
if(TARGET rexgpu-xenos)
    add_dependencies(main rexgpu-xenos)
endif()

target_link_options(main PRIVATE
    -Wl,--export-dynamic
)

set_target_properties(main PROPERTIES
    OUTPUT_NAME "main"
    CXX_VISIBILITY_PRESET default
    VISIBILITY_INLINES_HIDDEN OFF
)
