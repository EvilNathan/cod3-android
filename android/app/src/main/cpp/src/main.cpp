/**
 * Project 1944 - Android native entry point.
 *
 * M0b: drives the ReXGlue WindowedApp lifecycle on SDLActivity's native
 * thread. The app opens an SDL3 window, creates the Vulkan provider and
 * presenter, and presents continuously through VK_KHR_android_surface.
 *
 * @added Project 1944 Android port, 2026
 */

#include <SDL3/SDL_main.h>
#include <SDL3/SDL_system.h>

#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>

#include <atomic>
#include <chrono>
#include <cstdlib>
#include <memory>
#include <string>
#include <thread>

#include <rex/cvar.h>
#include <rex/logging.h>
#include <rex/main_android.h>
#include <rex/platform.h>
#include <rex/ui/vulkan/presenter.h>
#include <rex/ui/vulkan/provider.h>
#include <rex/ui/window_sdl.h>
#include <rex/ui/windowed_app.h>
#include <rex/ui/windowed_app_context_sdl.h>

#if REX_PLATFORM_ANDROID

namespace {

constexpr char kAppIdentifier[] = "project1944";
constexpr char kLogTag[] = "project1944";

#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)

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

class Project1944App final : public rex::ui::WindowedApp {
 public:
  explicit Project1944App(rex::ui::WindowedAppContext& app_context)
      : WindowedApp(app_context, kAppIdentifier) {}
  ~Project1944App() override = default;

  bool OnInitialize() override {
    window_ = std::make_unique<rex::ui::WindowSDL>(app_context(), "Project 1944", 1280, 720);
    if (!window_->Open()) {
      REXLOG_ERROR("Project1944App: window could not be opened");
      return false;
    }

    provider_ = rex::ui::vulkan::VulkanProvider::Create(false, true);
    if (!provider_) {
      REXLOG_ERROR("Project1944App: Vulkan provider creation failed");
      return false;
    }

    presenter_ = provider_->CreatePresenter();
    if (!presenter_) {
      REXLOG_ERROR("Project1944App: Vulkan presenter creation failed");
      return false;
    }

    window_->SetPresenter(presenter_.get());

    paint_thread_ = std::thread([this]() { PaintLoop(); });
    REXLOG_INFO("Project1944App: initialized, presenting");
    return true;
  }

 protected:
  void OnDestroy() override {
    paint_stop_.store(true, std::memory_order_release);
    if (paint_thread_.joinable()) {
      paint_thread_.join();
    }
    if (window_) {
      window_->SetPresenter(nullptr);
    }
    presenter_.reset();
    window_.reset();
    provider_.reset();
  }

 private:
  void PaintLoop() {
    using clock = std::chrono::steady_clock;
    const auto start = clock::now();
    auto second_mark = start;
    uint32_t frames = 0;
    while (!paint_stop_.load(std::memory_order_acquire)) {
      if (window_) {
        window_->RequestPaint();
      }
      ++frames;
      const auto now = clock::now();
      if (now - second_mark >= std::chrono::seconds(2)) {
        const double seconds = std::chrono::duration<double>(now - second_mark).count();
        REXLOG_INFO("Project1944App: {:.1f} paint requests/s", frames / seconds);
        frames = 0;
        second_mark = now;
      }
      std::this_thread::sleep_for(std::chrono::milliseconds(16));
    }
    (void)start;
  }

  std::unique_ptr<rex::ui::WindowSDL> window_;
  std::unique_ptr<rex::ui::vulkan::VulkanProvider> provider_;
  std::unique_ptr<rex::ui::Presenter> presenter_;
  std::thread paint_thread_;
  std::atomic<bool> paint_stop_{false};
};

std::unique_ptr<rex::ui::WindowedApp> CreateProject1944App(
    rex::ui::WindowedAppContext& app_context) {
  return std::make_unique<Project1944App>(app_context);
}

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

  auto remaining = rex::cvar::Init(argc, argv);
  (void)remaining;
  rex::cvar::ApplyEnvironment();
  rex::InitLoggingEarly();

  REXLOG_INFO("android_main: app={} lib_dir={}", kAppIdentifier, lib_dir);

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
  return result;
}

}  // namespace

REX_DEFINE_APP(project1944, CreateProject1944App);

int main(int argc, char* argv[]) {
  return RunAndroidApp(argc, argv);
}

#endif  // REX_PLATFORM_ANDROID
