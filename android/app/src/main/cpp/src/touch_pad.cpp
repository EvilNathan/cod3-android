/**
 * Project 1944 Android - virtual gamepad bridge (M4).
 *
 * The Java TouchOverlay drives one SDL virtual joystick; ReXGlue's SDL input
 * driver already listens for SDL_EVENT_GAMEPAD_* and feeds XAM/XInput, so a
 * virtual pad with a standard gamepad mapping turns overlay touches into real
 * Xbox-360-style controller state without touching SDK code.
 *
 * Axis layout:  0 LX, 1 LY, 2 RX, 3 RY, 4 LT, 5 RT   (signed 16-bit)
 * Button layout: SDL_GAMEPAD_BUTTON_* indices (0..14), the same values the
 * gamepad mapping string below refers to.
 */

#include "touch_pad.h"

#include <SDL3/SDL.h>

#include <android/log.h>
#include <jni.h>

#include <algorithm>
#include <atomic>
#include <cstring>
#include <string>

#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, "project1944", __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, "project1944", __VA_ARGS__)

namespace project1944 {

namespace {

constexpr int kAxisCount = 6;
constexpr int kButtonCount = 15;

SDL_Joystick* g_pad = nullptr;
std::atomic<Sint16> g_axis_state[kAxisCount];
std::atomic<bool> g_button_state[kButtonCount];

std::string GuidHexForId(SDL_JoystickID instance_id) {
  char buffer[33];
  const SDL_GUID guid = SDL_GetJoystickGUIDForID(instance_id);
  SDL_GUIDToString(guid, buffer, sizeof(buffer));
  return std::string(buffer);
}

}  // namespace

void InitVirtualPad() {
  if (g_pad) {
    return;
  }
  // The input driver subscribes to gamepad events, so the gamepad subsystem
  // must be up for SDL_AddGamepadMapping to emit GAMEPAD_ADDED.
  if (!SDL_InitSubSystem(SDL_INIT_GAMEPAD)) {
    ALOGE("virtual pad: SDL_InitSubSystem(SDL_INIT_GAMEPAD) failed: %s", SDL_GetError());
    return;
  }

  SDL_VirtualJoystickDesc desc{};
  desc.version = sizeof(SDL_VirtualJoystickDesc);
  desc.type = SDL_JOYSTICK_TYPE_GAMEPAD;
  desc.naxes = kAxisCount;
  desc.nbuttons = kButtonCount;
  desc.vendor_id = 0x1944;
  desc.product_id = 0x0003;
  desc.name = "Project 1944 Touch Pad";
  desc.button_mask =
      (1u << SDL_GAMEPAD_BUTTON_SOUTH) | (1u << SDL_GAMEPAD_BUTTON_EAST) |
      (1u << SDL_GAMEPAD_BUTTON_WEST) | (1u << SDL_GAMEPAD_BUTTON_NORTH) |
      (1u << SDL_GAMEPAD_BUTTON_BACK) | (1u << SDL_GAMEPAD_BUTTON_GUIDE) |
      (1u << SDL_GAMEPAD_BUTTON_START) | (1u << SDL_GAMEPAD_BUTTON_LEFT_STICK) |
      (1u << SDL_GAMEPAD_BUTTON_RIGHT_STICK) | (1u << SDL_GAMEPAD_BUTTON_LEFT_SHOULDER) |
      (1u << SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER) | (1u << SDL_GAMEPAD_BUTTON_DPAD_UP) |
      (1u << SDL_GAMEPAD_BUTTON_DPAD_DOWN) | (1u << SDL_GAMEPAD_BUTTON_DPAD_LEFT) |
      (1u << SDL_GAMEPAD_BUTTON_DPAD_RIGHT);
  desc.axis_mask = (1u << SDL_GAMEPAD_AXIS_LEFTX) | (1u << SDL_GAMEPAD_AXIS_LEFTY) |
                   (1u << SDL_GAMEPAD_AXIS_RIGHTX) | (1u << SDL_GAMEPAD_AXIS_RIGHTY) |
                   (1u << SDL_GAMEPAD_AXIS_LEFT_TRIGGER) |
                   (1u << SDL_GAMEPAD_AXIS_RIGHT_TRIGGER);

  const SDL_JoystickID instance_id = SDL_AttachVirtualJoystick(&desc);
  if (!instance_id) {
    ALOGE("virtual pad: SDL_AttachVirtualJoystick failed: %s", SDL_GetError());
    return;
  }

  // Register the standard Xbox-style mapping for the computed GUID. SDL emits
  // GAMEPAD_ADDED once a mapping matches; the input driver's event watch picks
  // it up and opens the pad on an XInput port.
  const std::string guid = GuidHexForId(instance_id);
  const std::string mapping =
      guid + ",Project 1944 Touch Pad,platform:Android," +
      "a:b0,b:b1,x:b2,y:b3,back:b4,guide:b5,start:b6," +
      "leftstick:b7,rightstick:b8,leftshoulder:b9,rightshoulder:b10," +
      "dpup:b11,dpdown:b12,dpleft:b13,dpright:b14," +
      "leftx:a0,lefty:a1,rightx:a2,righty:a3," +
      "lefttrigger:a4,righttrigger:a5,";
  const int added = SDL_AddGamepadMapping(mapping.c_str());
  if (added < 0) {
    ALOGE("virtual pad: mapping failed: %s", SDL_GetError());
  }

  g_pad = SDL_GetJoystickFromID(instance_id);
  if (!g_pad) {
    ALOGE("virtual pad: SDL_GetJoystickFromID failed: %s", SDL_GetError());
    return;
  }
  for (auto& axis : g_axis_state) {
    axis.store(0);
  }
  for (auto& button : g_button_state) {
    button.store(false);
  }
  ALOGI("virtual pad: attached '%s' (id=%u, guid=%s)", desc.name,
        static_cast<unsigned>(instance_id), guid.c_str());
}

// JNI bridge. extern "C" inside the namespace keeps the exported symbols
// unmangled while still seeing g_pad/kAxisCount/... above.
extern "C" {

// Java side (TouchOverlay via GameActivity). Safe to call before the pad is
// attached: every call is ignored until InitVirtualPad() has run on the SDL
// main thread.
JNIEXPORT void JNICALL
Java_com_project1944_cod3_GameActivity_nativePadAxis(JNIEnv* /*env*/, jclass, jint axis,
                                                     jfloat value) {
  if (!g_pad || axis < 0 || axis >= kAxisCount) {
    return;
  }
  const float clamped = std::clamp(value, -1.0f, 1.0f);
  const Sint16 v = static_cast<Sint16>(clamped * 32767.0f);
  if (g_axis_state[axis].exchange(v) == v) {
    return;
  }
  SDL_SetJoystickVirtualAxis(g_pad, axis, v);
}

JNIEXPORT void JNICALL
Java_com_project1944_cod3_GameActivity_nativePadButton(JNIEnv* /*env*/, jclass, jint button,
                                                       jboolean down) {
  if (!g_pad || button < 0 || button >= kButtonCount) {
    return;
  }
  if (g_button_state[button].exchange(down) == down) {
    return;
  }
  SDL_SetJoystickVirtualButton(g_pad, button, down);
}

}  // extern "C"

}  // namespace project1944
