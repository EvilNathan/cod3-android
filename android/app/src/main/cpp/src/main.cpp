#include <SDL3/SDL.h>
#include <SDL3/SDL_gpu.h>
#include <SDL3/SDL_main.h>

#include <cmath>
#include <cstdio>

namespace {

constexpr const char* kLogTag = "project1944";

void log_sdl_error(const char* what) {
  SDL_Log("[%s] %s failed: %s", kLogTag, what, SDL_GetError());
}

}  // namespace

int main(int argc, char** argv) {
  (void)argc;
  (void)argv;

  SDL_SetAppMetadata("Project 1944", "0.1.0-m0a", "com.project1944.cod3");
  SDL_SetLogPriorities(SDL_LOG_PRIORITY_INFO);

  if (!SDL_Init(SDL_INIT_VIDEO)) {
    log_sdl_error("SDL_Init(SDL_INIT_VIDEO)");
    return 1;
  }

  SDL_Log("[%s] SDL video driver: %s", kLogTag, SDL_GetCurrentVideoDriver());

  SDL_Window* window = SDL_CreateWindow(
      "Project 1944 - Vulkan smoke test",
      1280,
      720,
      SDL_WINDOW_VULKAN | SDL_WINDOW_HIGH_PIXEL_DENSITY);
  if (!window) {
    log_sdl_error("SDL_CreateWindow");
    SDL_Quit();
    return 1;
  }

  SDL_GPUDevice* device =
      SDL_CreateGPUDevice(SDL_GPU_SHADERFORMAT_SPIRV, true, nullptr);
  if (!device) {
    log_sdl_error("SDL_CreateGPUDevice(SPIR-V)");
    SDL_DestroyWindow(window);
    SDL_Quit();
    return 1;
  }

  const char* driver = SDL_GetGPUDeviceDriver(device);
  SDL_Log("[%s] GPU driver: %s", kLogTag, driver ? driver : "(unknown)");

  if (!SDL_ClaimWindowForGPUDevice(device, window)) {
    log_sdl_error("SDL_ClaimWindowForGPUDevice");
    SDL_DestroyGPUDevice(device);
    SDL_DestroyWindow(window);
    SDL_Quit();
    return 1;
  }

  if (!SDL_SetGPUSwapchainParameters(device, window,
                                     SDL_GPU_SWAPCHAINCOMPOSITION_SDR,
                                     SDL_GPU_PRESENTMODE_VSYNC)) {
    // Not fatal: the default swapchain parameters are still usable.
    SDL_Log("[%s] swapchain parameters not applied: %s", kLogTag, SDL_GetError());
  }

  Uint64 window_start = SDL_GetTicks();
  Uint64 frame_count = 0;
  bool running = true;

  while (running) {
    SDL_Event event;
    while (SDL_PollEvent(&event)) {
      if (event.type == SDL_EVENT_QUIT ||
          event.type == SDL_EVENT_WINDOW_CLOSE_REQUESTED) {
        running = false;
      } else if (event.type == SDL_EVENT_KEY_DOWN) {
        if (event.key.key == SDLK_ESCAPE || event.key.key == SDLK_AC_BACK) {
          running = false;
        }
      }
    }

    SDL_GPUCommandBuffer* command_buffer = SDL_AcquireGPUCommandBuffer(device);
    if (!command_buffer) {
      log_sdl_error("SDL_AcquireGPUCommandBuffer");
      break;
    }

    SDL_GPUTexture* swapchain_texture = nullptr;
    Uint32 swapchain_width = 0;
    Uint32 swapchain_height = 0;
    if (!SDL_WaitAndAcquireGPUSwapchainTexture(
            command_buffer, window, &swapchain_texture, &swapchain_width,
            &swapchain_height)) {
      log_sdl_error("SDL_WaitAndAcquireGPUSwapchainTexture");
      SDL_CancelGPUCommandBuffer(command_buffer);
      break;
    }

    if (!swapchain_texture) {
      SDL_CancelGPUCommandBuffer(command_buffer);
      continue;
    }

    // A slowly pulsing clear colour is enough to prove that the swapchain is
    // presenting continuously. It also makes it obvious when the surface has
    // gone stale (for example after an Android pause/resume cycle).
    const float phase = static_cast<float>(SDL_GetTicks() % 6000) / 6000.0f;
    const float pulse = 0.5f - 0.5f * std::cos(phase * 6.28318530718f);

    SDL_GPUColorTargetInfo color_target{};
    color_target.texture = swapchain_texture;
    color_target.clear_color = SDL_FColor{
        0.035f + 0.10f * pulse,
        0.045f + 0.06f * pulse,
        0.070f + 0.16f * (1.0f - pulse),
        1.0f,
    };
    color_target.load_op = SDL_GPU_LOADOP_CLEAR;
    color_target.store_op = SDL_GPU_STOREOP_STORE;

    SDL_GPURenderPass* render_pass =
        SDL_BeginGPURenderPass(command_buffer, &color_target, 1, nullptr);
    if (!render_pass) {
      log_sdl_error("SDL_BeginGPURenderPass");
      SDL_CancelGPUCommandBuffer(command_buffer);
      break;
    }
    SDL_EndGPURenderPass(render_pass);

    if (!SDL_SubmitGPUCommandBuffer(command_buffer)) {
      log_sdl_error("SDL_SubmitGPUCommandBuffer");
      break;
    }

    ++frame_count;
    const Uint64 now = SDL_GetTicks();
    if (now - window_start >= 2000) {
      const double seconds = static_cast<double>(now - window_start) / 1000.0;
      SDL_Log("[%s] %llu frames in %.2f s (%.1f FPS), swapchain %ux%u",
              kLogTag,
              static_cast<unsigned long long>(frame_count),
              seconds,
              static_cast<double>(frame_count) / seconds,
              swapchain_width,
              swapchain_height);
      frame_count = 0;
      window_start = now;
    }
  }

  SDL_ReleaseWindowFromGPUDevice(device, window);
  SDL_DestroyGPUDevice(device);
  SDL_DestroyWindow(window);
  SDL_Quit();
  return 0;
}
