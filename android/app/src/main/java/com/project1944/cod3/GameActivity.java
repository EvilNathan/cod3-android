package com.project1944.cod3;

import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

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

    /**
     * Resolves a content:// URI for the native filesystem bridge. Called via
     * JNI from rex::OpenContentUriFdJavaBridge; the returned descriptor is
     * detached into a raw fd and owned by native code.
     */
    public static ParcelFileDescriptor openContentFd(String uri, String mode) {
        try {
            return getContext().getContentResolver().openFileDescriptor(Uri.parse(uri), mode);
        } catch (Exception e) {
            Log.e("project1944", "openContentFd failed: " + uri, e);
            return null;
        }
    }
}
