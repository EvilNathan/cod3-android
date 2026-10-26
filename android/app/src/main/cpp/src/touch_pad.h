/**
 * Project 1944 Android - virtual gamepad bridge (M4).
 *
 * The Java TouchOverlay drives one SDL virtual joystick; ReXGlue's SDL input
 * driver already listens for SDL_EVENT_GAMEPAD_* and feeds XAM/XInput, so a
 * virtual pad with a standard gamepad mapping turns overlay touches into real
 * Xbox-360-style controller state without touching SDK code.
 */
#pragma once

namespace project1944 {

// Called from the SDL main thread after the window exists and the input
// driver's SDL event watch is installed. Attaching earlier would emit
// SDL_EVENT_GAMEPAD_ADDED before anyone is listening.
void InitVirtualPad();

}  // namespace project1944
