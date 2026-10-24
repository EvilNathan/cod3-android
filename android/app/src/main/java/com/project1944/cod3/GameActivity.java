package com.project1944.cod3;

import android.content.pm.ActivityInfo;
import android.os.Bundle;

import org.libsdl.app.SDLActivity;

/**
 * SDL3 host activity. SDLActivity owns the SurfaceView, input dispatch, audio
 * and the native thread that calls SDL_main in libmain.so.
 *
 * The SDL3 native library is linked statically into libmain.so for M0a, so
 * only "main" is loaded here. When the ReXGlue runtime and GPU plugin are
 * added, libmain.so remains the entry point and librexgpu-xenos.so is opened
 * by the runtime from nativeLibraryDir.
 */
public final class GameActivity extends SDLActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected String[] getLibraries() {
        return new String[] {"main"};
    }

    @Override
    protected String getMainSharedObject() {
        return getApplicationInfo().nativeLibraryDir + "/libmain.so";
    }

    @Override
    protected String getMainFunction() {
        return "SDL_main";
    }
}
