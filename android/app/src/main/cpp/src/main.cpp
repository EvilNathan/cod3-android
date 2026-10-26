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
#include <SDL3/SDL_hints.h>
#include <SDL3/SDL_system.h>

#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>

#include <array>
#include <cstdlib>
#include <filesystem>
#include <memory>
#include <mutex>
#include <span>
#include <string>
#include <utility>

#include <rex/cvar.h>
#include <rex/filesystem.h>
#include <rex/logging.h>
#include <rex/main_android.h>
#include <rex/platform.h>
#include <rex/rex_app.h>
#include <rex/ui/windowed_app.h>
#include <rex/ui/windowed_app_context_sdl.h>

#include "cod3_pc_init.h"
#include "sha256.h"
#include "touch_pad.h"

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

    // Mobile GPUs (Mali in particular) omit optional Vulkan features that the
    // desktop backend gates device selection on. Each one has a documented
    // fallback; leaving the requirements on rejects the only device and the
    // app exits before the guest starts.
    rex::cvar::SetFlagByName(
        "vulkan_require_vertex_pipeline_stores_and_atomics", "false");
    rex::cvar::SetFlagByName("vulkan_require_geometry_shader", "false");
    rex::cvar::SetFlagByName("vulkan_require_fill_mode_non_solid", "false");
  }

  void OnConfigurePaths(rex::PathConfig& paths) override {
    const auto [log_file, game_data] = GetHostPaths();
    if (!game_data.empty()) {
      paths.game_data_root = game_data;
    }
    // Keep saves, shader caches and config inside the app's external files
    // directory, next to the session log.
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

  // Runs after Runtime::Setup: the VFS is mounted. Read the entrypoint XEX
  // through the VFS (host folder or disc image - the same path the guest will
  // load) and log its digest so a wrong disc revision is visible in logs.
  void OnPostSetup() override {
    // X_STATUS_END_OF_FILE expands to (X_STATUS)0xC0000011L, which spells the
    // type unqualified; make it visible in this scope before the comparison.
    using rex::X_STATUS;
    rex::filesystem::VirtualFileSystem* vfs = runtime()->file_system();
    if (!vfs) {
      return;
    }
    rex::filesystem::Entry* entry = vfs->ResolvePath("game:\\default.xex");
    if (!entry) {
      REXLOG_ERROR("OnPostSetup: game:\\default.xex not found on the mounted game data");
      return;
    }
    rex::filesystem::File* file = nullptr;
    if (XFAILED(entry->Open(rex::filesystem::FileAccess::kGenericRead, &file)) || !file) {
      REXLOG_ERROR("OnPostSetup: failed to open game:\\default.xex");
      return;
    }
    project1944::Sha256 hasher;
    std::array<uint8_t, 64 * 1024> buffer;
    // Entry::size() is the XEX file length on the mounted folder or disc
    // image. Reading past it returns X_STATUS_END_OF_FILE, which must be
    // treated as a clean end: the old read-until-failure loop reported a
    // bogus error at the exact end of default.xex and skipped the digest.
    const size_t total_size = entry->size();
    size_t offset = 0;
    bool read_failed = false;
    while (total_size == 0 || offset < total_size) {
      size_t chunk = buffer.size();
      if (total_size != 0 && chunk > total_size - offset) {
        chunk = total_size - offset;
      }
      size_t bytes_read = 0;
      const auto status = file->ReadSync(std::span<uint8_t>(buffer.data(), chunk), offset,
                                         &bytes_read);
      if (status == X_STATUS_END_OF_FILE) {
        break;
      }
      if (XFAILED(status)) {
        REXLOG_ERROR("OnPostSetup: default.xex read failed at offset {}: {:08X}", offset,
                     static_cast<uint32_t>(status));
        read_failed = true;
        break;
      }
      if (!bytes_read) {
        break;
      }
      hasher.update(buffer.data(), bytes_read);
      offset += bytes_read;
    }
    file->Destroy();
    if (read_failed) {
      return;
    }
    const std::string digest = hasher.hexdigest();
    REXLOG_INFO("OnPostSetup: default.xex {} bytes (entry size {}), SHA-256 {}", offset,
                total_size, digest);
    if (digest != kSupportedXexSha256) {
      REXLOG_WARN(
          "OnPostSetup: default.xex SHA-256 differs from the supported build "
          "({}); this disc revision is untested", kSupportedXexSha256);
    }
  }

 private:
  static constexpr char kSupportedXexSha256[] =
      "2944EEC7D1231AD6798B5F9FADF8855F5E489296B22EAB45B27A577CEE23692";
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

  // The first Android surface can be portrait even when the manifest says
  // sensorLandscape. SDL uses the window width/height to pick SENSOR_PORTRAIT
  // in that case; restricting the allowed orientations makes SDL's own
  // request landscape too. GameActivity.setOrientationBis() is the final
  // guard for requests that still arrive with a portrait size.
  SDL_SetHint(SDL_HINT_ORIENTATIONS, "LandscapeLeft LandscapeRight");

  rex::SetAndroidApplicationContext(java_vm, SDL_GetAndroidActivity(), lib_dir.c_str());

  const auto [log_file, game_data] = GetHostPaths();

  auto remaining = rex::cvar::Init(argc, argv);
  (void)remaining;
  rex::cvar::ApplyEnvironment();
  rex::InitLoggingEarly();

  // The Java host truncates this single absolute file at the start of every
  // game session and appends its own lines to it. Native lines use the same
  // file with a non-rotating sink, so no native-log.1.txt/.2.txt copies are
  // left behind. log_file is owned by this function for the runtime's life.
  if (!log_file.empty()) {
    std::error_code ec;
    std::filesystem::create_directories(std::filesystem::path(log_file).parent_path(), ec);
    try {
      rex::LogConfig log_config;
      log_config.log_file = log_file.c_str();
      log_config.log_file_single = true;
      log_config.log_to_console = false;
      log_config.default_level =
          rex::ParseLogLevelOr(REXCVAR_GET(log_level), spdlog::level::info);
      log_config.flush_level = spdlog::level::info;
      rex::InitLogging(log_config);
    } catch (const std::exception& e) {
      ALOGE("file logging unavailable (%s): %s", log_file.c_str(), e.what());
    }
  }

  REXLOG_INFO("android_main: app={} lib_dir={} log={}", kAppIdentifier, lib_dir, log_file);
  REXLOG_INFO("android_main: game data dir={}", game_data);

  if (game_data.empty()) {
    REXLOG_ERROR("android_main: no game data directory selected");
  } else {
    std::error_code ec;
    const bool is_file = std::filesystem::is_regular_file(game_data, ec);
    if (is_file) {
      const auto iso_size = std::filesystem::file_size(game_data, ec);
      REXLOG_INFO("android_main: disc image selected ({} bytes) - mounted directly, no extraction",
                  ec ? 0 : iso_size);
    } else {
      const auto xex = std::filesystem::path(game_data) / "default.xex";
      const auto size = std::filesystem::file_size(xex, ec);
      if (ec) {
        std::error_code dir_ec;
        const bool exists = std::filesystem::exists(game_data, dir_ec);
        const bool is_dir = std::filesystem::is_directory(game_data, dir_ec);
        REXLOG_ERROR("android_main: default.xex missing in {} (dir exists={} is_dir={})",
                     game_data, exists, is_dir);
      } else {
        REXLOG_INFO("android_main: default.xex found ({} bytes)", size);
      }
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

    // The window exists now, but the input driver's SDL event watch is
    // installed during OnInitialize (RexApp::Setup -> InputSystem::AttachWindow
    // -> SDLInputDriver::OnWindowAvailable), so the virtual pad must be
    // attached after OnInitialize succeeds: its GAMEPAD_ADDED event would
    // otherwise be emitted before anyone is listening. The Java touch overlay
    // feeds the pad through nativePadAxis/nativePadButton.
    const auto creator = rex::ui::WindowedApp::GetCreator(kAppIdentifier);
    if (!creator) {
      REXLOG_ERROR("android_main: app '{}' is not registered", kAppIdentifier);
      return EXIT_FAILURE;
    }

    std::unique_ptr<rex::ui::WindowedApp> app = creator(app_context);
    if (!app->OnInitialize()) {
      REXLOG_ERROR("android_main: OnInitialize failed");
      return EXIT_FAILURE;
    }

    project1944::InitVirtualPad();

    result = app_context.RunMainMessageLoop();
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
