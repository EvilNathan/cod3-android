/**
 * Project 1944 - Android native entry point.
 *
 * M2: boots the recompiled Call of Duty 3 guest on SDLActivity's native
 * thread. The ReXGlue runtime mounts the user-selected game folder, loads
 * default.xex, and runs the recompiled guest code inside libmain.so.
 *
 * @added Project 1944 Android port, 2026
 */

#include <SDL3/SDL_main.h>
#include <SDL3/SDL_system.h>

#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>

#include <cstdlib>
#include <filesystem>
#include <memory>
#include <mutex>
#include <string>
#include <utility>

#include <rex/cvar.h>
#include <rex/logging.h>
#include <rex/main_android.h>
#include <rex/platform.h>
#include <rex/rex_app.h>
#include <rex/ui/windowed_app.h>
#include <rex/ui/windowed_app_context_sdl.h>

#include "cod3_pc_init.h"

#if REX_PLATFORM_ANDROID

namespace {

constexpr char kAppIdentifier[] = "cod3_pc";
constexpr char kLogTag[] = "project1944";

#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)

// Paths supplied by GameActivity before SDLActivity starts the native thread.
// They are deliberately plain strings: the JNI setter may run on the UI thread
// while RunAndroidApp reads them on the SDL thread.
std::mutex g_host_paths_mutex;
std::string g_log_file_path;
std::string g_game_data_path;

void SetHostPaths(std::string log_file, std::string game_data) {
  std::lock_guard lock(g_host_paths_mutex);
  g_log_file_path = std::move(log_file);
  g_game_data_path = std::move(game_data);
}

std::pair<std::string, std::string> GetHostPaths() {
  std::lock_guard lock(g_host_paths_mutex);
  return {g_log_file_path, g_game_data_path};
}

// ApplicationInfo.nativeLibraryDir without JNI: the loader path of this
// library is the native library directory.
std::string QueryNativeLibraryDir() {
  Dl_info info{};
  if (dladdr(reinterpret_cast<void*>(&QueryNativeLibraryDir), &info) && info.dli_fname) {
    std::string path(info.dli_fname);
    const size_t slash = path.find_last_of('/');
    if (slash != std::string::npos) {
      return path.substr(0, slash);
    }
  }
  return {};
}

JavaVM* QueryJavaVm() {
  auto* env = static_cast<JNIEnv*>(SDL_GetAndroidJNIEnv());
  JavaVM* vm = nullptr;
  if (env && env->GetJavaVM(&vm) == JNI_OK) {
    return vm;
  }
  return nullptr;
}

// Boots the recompiled guest. ReXGlue owns the window, the Vulkan presenter
// and the Xenos GPU plugin; the Android layer only supplies paths.
class Cod3AndroidApp final : public rex::ReXApp {
 public:
  using rex::ReXApp::ReXApp;

  static std::unique_ptr<rex::ui::WindowedApp> Create(
      rex::ui::WindowedAppContext& app_context) {
    return std::unique_ptr<Cod3AndroidApp>(
        new Cod3AndroidApp(app_context, "cod3_pc", PPCImageConfig));
  }

  void OnPreSetup(rex::RuntimeConfig& config) override {
    // The SDK default is empty (headless). Android always wants the Xenos
    // Vulkan plugin, packaged next to libmain.so.
    config.gpu_plugin = "xenos";
  }

  void OnConfigurePaths(rex::PathConfig& paths) override {
    const auto [log_file, game_data] = GetHostPaths();
    if (!game_data.empty()) {
      paths.game_data_root = game_data;
    }
    // Keep saves, shader caches and config inside the app's external files
    // directory, next to logfile.txt.
    if (!log_file.empty()) {
      const auto user_root =
          std::filesystem::path(log_file).parent_path() / "data";
      paths.user_data_root = user_root;
      paths.update_data_root.clear();
      paths.cache_root = user_root / "cache";
      paths.metadata_root = user_root / "metadata";
      paths.config_path = user_root / "cod3_pc.toml";
    }
  }
};

int RunAndroidApp(int argc, char** argv) {
  const std::string lib_dir = QueryNativeLibraryDir();
  JavaVM* java_vm = QueryJavaVm();
  if (!java_vm) {
    ALOGE("JavaVM unavailable; the runtime cannot bridge to Java");
    return EXIT_FAILURE;
  }
  if (lib_dir.empty()) {
    ALOGE("nativeLibraryDir unresolved; GPU plugin staging will fail");
  }

  rex::SetAndroidApplicationContext(java_vm, SDL_GetAndroidActivity(), lib_dir.c_str());

  const auto [log_file, game_data] = GetHostPaths();

  auto remaining = rex::cvar::Init(argc, argv);
  (void)remaining;
  rex::cvar::ApplyEnvironment();
  rex::InitLoggingEarly();

  // The Java layer owns the log location (app external files dir) so the user
  // can pull one predictable logfile.txt. Keep logcat as the early sink.
  if (!log_file.empty()) {
    std::error_code ec;
    std::filesystem::create_directories(
        std::filesystem::path(log_file).parent_path(), ec);
    try {
      rex::LogConfig log_config;
      log_config.log_file = log_file.c_str();
      log_config.log_to_console = false;
      log_config.default_level =
          rex::ParseLogLevelOr(REXCVAR_GET(log_level), spdlog::level::info);
      log_config.flush_level = spdlog::level::info;
      rex::InitLogging(log_config);
    } catch (const std::exception& e) {
      ALOGE("file logging unavailable (%s): %s", log_file.c_str(), e.what());
    }
  }

  REXLOG_INFO("android_main: app={} lib_dir={} log_file={}", kAppIdentifier, lib_dir,
              log_file);
  REXLOG_INFO("android_main: game data dir={}", game_data);

  if (game_data.empty()) {
    REXLOG_ERROR("android_main: no game data directory selected");
  } else {
    const auto xex = std::filesystem::path(game_data) / "default.xex";
    std::error_code ec;
    const auto size = std::filesystem::file_size(xex, ec);
    if (ec) {
      REXLOG_ERROR("android_main: default.xex missing in {}", game_data);
    } else {
      REXLOG_INFO("android_main: default.xex found ({} bytes)", size);
    }
  }

  if (!rex::InitializeAndroidSystemForApplicationContext()) {
    return EXIT_FAILURE;
  }

  int result = EXIT_FAILURE;
  {
    rex::ui::SDLWindowedAppContext app_context;
    if (!app_context.Initialize()) {
      REXLOG_ERROR("android_main: SDLWindowedAppContext::Initialize failed: {}", SDL_GetError());
      return EXIT_FAILURE;
    }

    const auto creator = rex::ui::WindowedApp::GetCreator(kAppIdentifier);
    if (!creator) {
      REXLOG_ERROR("android_main: app '{}' is not registered", kAppIdentifier);
      return EXIT_FAILURE;
    }

    std::unique_ptr<rex::ui::WindowedApp> app = creator(app_context);
    result = app->OnInitialize() ? app_context.RunMainMessageLoop() : EXIT_FAILURE;
    app->InvokeOnDestroy();
  }

  rex::ShutdownAndroidSystem();
  REXLOG_INFO("android_main: exiting with code {}", result);
  rex::FlushLogging();
  return result;
}

}  // namespace

// Called from GameActivity.onCreate() before SDLActivity starts SDL_main.
// Both strings are copied immediately; the Java side may recycle them.
extern "C" JNIEXPORT void JNICALL
Java_com_project1944_cod3_GameActivity_nativeSetPaths(JNIEnv* env, jclass, jstring log_file,
                                                      jstring game_data) {
  auto to_string = [env](jstring value) {
    if (!value) {
      return std::string();
    }
    const char* utf = env->GetStringUTFChars(value, nullptr);
    if (!utf) {
      return std::string();
    }
    std::string result(utf);
    env->ReleaseStringUTFChars(value, utf);
    return result;
  };
  SetHostPaths(to_string(log_file), to_string(game_data));
}

REX_DEFINE_APP(cod3_pc, Cod3AndroidApp::Create);

int main(int argc, char* argv[]) {
  return RunAndroidApp(argc, argv);
}

#endif  // REX_PLATFORM_ANDROID
