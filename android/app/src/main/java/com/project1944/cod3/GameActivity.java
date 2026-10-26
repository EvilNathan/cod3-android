package com.project1944.cod3;

import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.ViewGroup;

import org.libsdl.app.SDLActivity;

/**
 * SDL3 host activity. SDLActivity owns the SurfaceView, input dispatch, audio
 * and the native thread that calls SDL_main in libmain.so.
 *
 * Before the SDL thread starts, this class hands the launcher's log file and
 * game-data directory to native code. After the SurfaceView is attached it adds
 * the touch gamepad overlay on top.
 */
public final class GameActivity extends SDLActivity {
    private TouchOverlay overlay;

    static {
        // Load the runtime first: SDL's JNI_OnLoad lives in librexruntime.so and
        // must capture the JavaVM before SDL_main asks for the Android context.
        // Both loads happen before super.onCreate(), so nativeSetPaths() can run
        // before SDLActivity starts the SDL thread.
        System.loadLibrary("rexruntime");
        System.loadLibrary("main");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Logx.init(this);
        String gamePath = GameData.resolve(this);
        String logPath = Logx.file(this).getAbsolutePath();
        Logx.i("GameActivity", "onCreate game=" + gamePath + " log=" + logPath);
        try {
            nativeSetPaths(logPath, gamePath);
            Logx.i("GameActivity", "native runtime lines go to native-log.txt next to this file");
        } catch (UnsatisfiedLinkError e) {
            Logx.e("GameActivity", "nativeSetPaths unavailable", e);
        }

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        super.onCreate(savedInstanceState);

        overlay = new TouchOverlay(this);
        addContentView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Logx.i("GameActivity", "touch overlay attached");
        // The SDL virtual pad behind the overlay is attached by native code
        // (touch_pad.cpp) once the SDL window is up; overlay calls into
        // nativePadAxis/nativePadButton are ignored until then.
    }

    @Override
    protected void onResume() {
        super.onResume();
        Logx.i("GameActivity", "onResume");
    }

    @Override
    protected void onPause() {
        Logx.i("GameActivity", "onPause");
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Logx.i("GameActivity", "onDestroy");
        super.onDestroy();
    }

    @Override
    protected String[] getLibraries() {
        return new String[] {"rexruntime", "main"};
    }

    @Override
    protected String getMainSharedObject() {
        return getApplicationInfo().nativeLibraryDir + "/libmain.so";
    }

    @Override
    protected String getMainFunction() {
        return "SDL_main";
    }

    private static native void nativeSetPaths(String logFile, String gamePath);

    /**
     * Virtual gamepad bridge (touch_pad.cpp). The touch overlay drives one
     * SDL virtual joystick; these setters feed its axes and buttons. The pad
     * itself is attached from native code once the SDL window exists.
     */
    public static native void nativePadAxis(int axis, float value);
    public static native void nativePadButton(int button, boolean down);

    /**
     * Resolves a content:// URI for the native filesystem bridge. Called via
     * JNI from rex::OpenContentUriFdJavaBridge; the returned descriptor is
     * detached into a raw fd and owned by native code.
     */
    public static ParcelFileDescriptor openContentFd(String uri, String mode) {
        try {
            return getContext().getContentResolver().openFileDescriptor(Uri.parse(uri), mode);
        } catch (Exception e) {
            Logx.e("GameActivity", "openContentFd failed: " + uri, e);
            return null;
        }
    }
}
