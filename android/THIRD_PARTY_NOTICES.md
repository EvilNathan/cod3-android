# Third-party notices

## SDL3

The files under `android/app/src/main/java/org/libsdl/app/` are the official
SDL3 Android Java glue, copied from the SDL3 source tree pinned by the ReXGlue
SDK (`thirdparty/sdl3`, commit `147a8ee32dbf9ac02f3794964490687b6bbda1bc`).

SDL3 is licensed under the zlib license. The full notice is kept at
`android/app/src/main/java/org/libsdl/app/LICENSE.txt`.

## ReXGlue / Xenia

The Android runtime work derives from the ReXGlue SDK and Xenia, both under the
BSD 3-Clause license. The runtime is linked into `librexruntime.so`; the UI layer
is compiled into `libmain.so`. Xenia's original notice is kept at
`licenses/xenia/LICENSE`, and upstream bundle notices are in
`integration/release-packaging/THIRD-PARTY-NOTICES.md`.

The Android changes to the pinned SDK are published as
`android/patches/rexglue-android.patch` under the same BSD 3-Clause terms.
