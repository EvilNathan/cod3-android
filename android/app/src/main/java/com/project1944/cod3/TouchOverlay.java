package com.project1944.cod3;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Transparent gamepad overlay for the SDL surface.
 *
 * Layout: two sticks, a four-button diamond, two shoulder pairs and
 * Back/Start. Everything is driven through one SDL virtual joystick in native
 * code (touch_pad.cpp); ReXGlue's SDL input driver turns it into XInput state.
 *
 * Any touch that does not land on a button or a visible stick becomes a direct
 * look drag: an invisible stick anchored where the finger went down, so the
 * camera turns by swiping the screen instead of hunting for the right stick.
 * A short tap in that area is translated to A (confirm in menus, jump in
 * play), mirroring the reference touch layer in Black-Android.
 *
 * The whole view consumes the gesture. The game does not need raw touch
 * events: every input is forwarded through the virtual pad.
 */
final class TouchOverlay extends View {
    // SDL_GAMEPAD_BUTTON_* indices; -1 means "use sdAxis instead".
    private static final int BTN_A = 0;
    private static final int BTN_B = 1;
    private static final int BTN_X = 2;
    private static final int BTN_Y = 3;
    private static final int BTN_BACK = 4;
    private static final int BTN_START = 6;
    private static final int BTN_L1 = 9;
    private static final int BTN_R1 = 10;
    // SDL axis indices of the virtual pad (touch_pad.cpp): 0..3 sticks, 4/5 triggers.
    private static final int AXIS_LX = 0;
    private static final int AXIS_LY = 1;
    private static final int AXIS_RX = 2;
    private static final int AXIS_RY = 3;
    private static final int AXIS_LT = 4;
    private static final int AXIS_RT = 5;

    private static final class PadButton {
        final int sdButton;   // SDL_GAMEPAD_BUTTON index, or -1
        final int sdAxis;     // axis index when sdButton == -1, else -1
        final String label;
        final boolean round;
        final RectF bounds = new RectF();
        boolean down;
        int presses;    // fingers currently holding this control (tap pulse not counted)

        PadButton(int sdButton, int sdAxis, String label, boolean round) {
            this.sdButton = sdButton;
            this.sdAxis = sdAxis;
            this.label = label;
            this.round = round;
        }
    }

    private static final int ACCENT = Color.rgb(232, 180, 74);
    private static final int WHITE = Color.rgb(236, 240, 244);
    private static final long TAP_MS = 90;
    private static final long TAP_MAX_MS = 250;
    private static final float LOOK_DEADZONE = 0.08f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<PadButton> buttons = new ArrayList<>();
    private final SparseArray<PadButton> pointerButtons = new SparseArray<>();

    private float leftCx, leftCy, rightCx, rightCy, stickRadius, knobRadius;
    private float lx, ly, rx, ry;
    private int leftPointer = -1;
    private int rightPointer = -1;
    private RectF leftStickZone;
    private RectF rightStickZone;

    // Direct look: invisible stick anchored at the touch-down point.
    private int lookPointer = -1;
    private float lookStartX, lookStartY, lookRadius;
    private long lookDownMs;
    private boolean lookMoved;
    private int tapSequence;

    TouchOverlay(GameActivity activity) {
        super(activity);
        setFocusable(false);
        setClickable(false);
        setWillNotDraw(false);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        paint.setStrokeWidth(dp(1.5f));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        final float u = Math.min(w, h);
        stickRadius = u * 0.115f;
        knobRadius = stickRadius * 0.42f;
        leftCx = u * 0.175f;
        leftCy = h - u * 0.24f;
        rightCx = w - u * 0.175f;
        rightCy = h - u * 0.24f;
        // Full camera speed after dragging about a fifth of the short edge.
        lookRadius = u * 0.20f;
        leftStickZone = new RectF(leftCx - stickRadius * 1.35f, leftCy - stickRadius * 1.35f,
                leftCx + stickRadius * 1.35f, leftCy + stickRadius * 1.35f);
        rightStickZone = new RectF(rightCx - stickRadius * 1.35f, rightCy - stickRadius * 1.35f,
                rightCx + stickRadius * 1.35f, rightCy + stickRadius * 1.35f);

        buttons.clear();
        pointerButtons.clear();
        leftPointer = -1;
        rightPointer = -1;
        lookPointer = -1;

        // Shoulder pairs.
        float triggerW = u * 0.145f;
        float triggerH = u * 0.072f;
        float edge = dp(18);
        addPill(new PadButton(-1, AXIS_LT, "LT", false),
                edge, edge, triggerW, triggerH);
        addPill(new PadButton(BTN_L1, -1, "LB", false),
                edge, edge + triggerH + dp(8), triggerW, triggerH);
        addPill(new PadButton(-1, AXIS_RT, "RT", false),
                w - edge - triggerW, edge, triggerW, triggerH);
        addPill(new PadButton(BTN_R1, -1, "RB", false),
                w - edge - triggerW, edge + triggerH + dp(8), triggerW, triggerH);

        // Back / Start pills at the top centre.
        float metaW = u * 0.125f;
        float metaH = u * 0.060f;
        float metaY = edge;
        addPill(new PadButton(BTN_BACK, -1, "BACK", false),
                w * 0.5f - metaW - dp(7), metaY, metaW, metaH);
        addPill(new PadButton(BTN_START, -1, "START", false),
                w * 0.5f + dp(7), metaY, metaW, metaH);

        // Face buttons: Xbox diamond (Y top, X left, B bottom, A right).
        float faceR = u * 0.052f;
        float faceCx = w - u * 0.195f;
        float faceCy = h * 0.345f;
        float gap = u * 0.112f;
        addRound(new PadButton(BTN_Y, -1, "Y", true), faceCx, faceCy - gap, faceR);
        addRound(new PadButton(BTN_X, -1, "X", true), faceCx - gap, faceCy, faceR);
        addRound(new PadButton(BTN_B, -1, "B", true), faceCx, faceCy + gap, faceR);
        addRound(new PadButton(BTN_A, -1, "A", true), faceCx + gap, faceCy, faceR);
    }

    private void addPill(PadButton button, float left, float top, float width, float height) {
        button.bounds.set(left, top, left + width, top + height);
        buttons.add(button);
    }

    private void addRound(PadButton button, float cx, float cy, float radius) {
        button.bounds.set(cx - radius, cy - radius, cx + radius, cy + radius);
        buttons.add(button);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int index = event.getActionIndex();
                claim(event.getPointerId(index), event.getX(index), event.getY(index));
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int id = event.getPointerId(i);
                    if (pointerButtons.get(id) != null) {
                        continue;
                    }
                    if (id == lookPointer) {
                        updateLook(event.getX(i), event.getY(i));
                    } else if (id == leftPointer) {
                        updateStick(event.getX(i), event.getY(i), true);
                    } else if (id == rightPointer) {
                        updateStick(event.getX(i), event.getY(i), false);
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                release(event.getPointerId(event.getActionIndex()));
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                cancelAll();
                break;
            }
            default:
                break;
        }
        invalidate();
        return true;
    }

    private void claim(int pointerId, float x, float y) {
        PadButton button = hitButton(x, y);
        if (button != null) {
            pointerButtons.put(pointerId, button);
            pressButton(button);
            return;
        }
        if (leftStickZone != null && leftStickZone.contains(x, y)) {
            if (leftPointer < 0) {
                leftPointer = pointerId;
                updateStick(x, y, true);
                Logx.d("TouchOverlay", "left stick engaged");
            }
            return;
        }
        if (rightStickZone != null && rightStickZone.contains(x, y)) {
            if (rightPointer < 0 && lookPointer < 0) {
                rightPointer = pointerId;
                updateStick(x, y, false);
                Logx.d("TouchOverlay", "right stick engaged");
            }
            return;
        }
        if (lookPointer < 0 && rightPointer < 0) {
            lookPointer = pointerId;
            lookStartX = x;
            lookStartY = y;
            lookDownMs = SystemClock.uptimeMillis();
            lookMoved = false;
            Logx.d("TouchOverlay", "direct look engaged");
        }
    }

    private PadButton hitButton(float x, float y) {
        for (PadButton button : buttons) {
            if (button.round) {
                float cx = button.bounds.centerX();
                float cy = button.bounds.centerY();
                float radius = button.bounds.width() * 0.5f + dp(8);
                float dx = x - cx;
                float dy = y - cy;
                if (dx * dx + dy * dy <= radius * radius) {
                    return button;
                }
            } else if (x >= button.bounds.left - dp(6) && x <= button.bounds.right + dp(6)
                    && y >= button.bounds.top - dp(6) && y <= button.bounds.bottom + dp(6)) {
                return button;
            }
        }
        return null;
    }

    private PadButton buttonFor(int sdButton) {
        for (PadButton button : buttons) {
            if (button.sdButton == sdButton) {
                return button;
            }
        }
        return null;
    }

    private void pressButton(PadButton button) {
        button.presses++;
        if (button.presses == 1) {
            button.down = true;
            sendButtonState(button, true);
            Logx.d("TouchOverlay", "pad down " + button.label);
        }
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private void releaseButton(PadButton button) {
        if (button == null || button.presses <= 0) {
            return;
        }
        button.presses--;
        if (button.presses == 0) {
            button.down = false;
            sendButtonState(button, false);
            Logx.d("TouchOverlay", "pad up " + button.label);
        }
    }

    private void sendButtonState(PadButton button, boolean down) {
        if (button.sdAxis >= 0) {
            GameActivity.nativePadAxis(button.sdAxis, down ? 1f : 0f);
        } else {
            GameActivity.nativePadButton(button.sdButton, down);
        }
    }

    private void release(int pointerId) {
        PadButton button = pointerButtons.get(pointerId);
        if (button != null) {
            pointerButtons.remove(pointerId);
            releaseButton(button);
        }
        if (pointerId == leftPointer) {
            leftPointer = -1;
            lx = 0;
            ly = 0;
            GameActivity.nativePadAxis(AXIS_LX, 0f);
            GameActivity.nativePadAxis(AXIS_LY, 0f);
        }
        if (pointerId == rightPointer) {
            rightPointer = -1;
            rx = 0;
            ry = 0;
            GameActivity.nativePadAxis(AXIS_RX, 0f);
            GameActivity.nativePadAxis(AXIS_RY, 0f);
        }
        if (pointerId == lookPointer) {
            boolean tap = !lookMoved
                    && SystemClock.uptimeMillis() - lookDownMs <= TAP_MAX_MS
                    && rx == 0f && ry == 0f;
            lookPointer = -1;
            rx = 0;
            ry = 0;
            GameActivity.nativePadAxis(AXIS_RX, 0f);
            GameActivity.nativePadAxis(AXIS_RY, 0f);
            if (tap) {
                tapA();
            }
        }
    }

    private void cancelAll() {
        pointerButtons.clear();
        for (PadButton button : buttons) {
            button.presses = 0;
            if (button.down) {
                button.down = false;
                sendButtonState(button, false);
            }
        }
        leftPointer = -1;
        rightPointer = -1;
        lookPointer = -1;
        lx = ly = rx = ry = 0;
        for (int axis = AXIS_LX; axis <= AXIS_RY; axis++) {
            GameActivity.nativePadAxis(axis, 0f);
        }
        // A tap pulse may still be in flight; cancel it and make sure A is up.
        tapSequence++;
        GameActivity.nativePadButton(BTN_A, false);
    }

    private void updateStick(float x, float y, boolean left) {
        float cx = left ? leftCx : rightCx;
        float cy = left ? leftCy : rightCy;
        float nx = (x - cx) / stickRadius;
        float ny = (y - cy) / stickRadius;
        float length = (float) Math.sqrt(nx * nx + ny * ny);
        if (length > 1f) {
            nx /= length;
            ny /= length;
        }
        if (left) {
            lx = nx;
            ly = ny;
            GameActivity.nativePadAxis(AXIS_LX, lx);
            GameActivity.nativePadAxis(AXIS_LY, ly);
        } else {
            rx = nx;
            ry = ny;
            GameActivity.nativePadAxis(AXIS_RX, rx);
            GameActivity.nativePadAxis(AXIS_RY, ry);
        }
    }

    /**
     * Direct look: the finger's offset from where it went down is the right
     * stick's deflection. Re-anchor at the edge so a long swipe keeps turning
     * instead of running out of travel; releasing stops the camera.
     */
    private void updateLook(float x, float y) {
        if (lookRadius <= 0f) {
            lookRadius = Math.max(1f, Math.min(getWidth(), getHeight()) * 0.20f);
        }
        float dx = x - lookStartX;
        float dy = y - lookStartY;
        if (!lookMoved) {
            float slop = dp(14);
            if (dx * dx + dy * dy > slop * slop) {
                lookMoved = true;
            }
        }
        if (dx > lookRadius) {
            lookStartX = x - lookRadius;
            dx = lookRadius;
        } else if (dx < -lookRadius) {
            lookStartX = x + lookRadius;
            dx = -lookRadius;
        }
        if (dy > lookRadius) {
            lookStartY = y - lookRadius;
            dy = lookRadius;
        } else if (dy < -lookRadius) {
            lookStartY = y + lookRadius;
            dy = -lookRadius;
        }
        rx = applyDeadzone(dx / lookRadius, LOOK_DEADZONE);
        ry = applyDeadzone(dy / lookRadius, LOOK_DEADZONE);
        GameActivity.nativePadAxis(AXIS_RX, rx);
        GameActivity.nativePadAxis(AXIS_RY, ry);
    }

    private static float applyDeadzone(float value, float deadzone) {
        float magnitude = Math.abs(value);
        if (magnitude <= deadzone) {
            return 0f;
        }
        float scaled = (magnitude - deadzone) / (1f - deadzone);
        return value < 0f ? -scaled : scaled;
    }

    /** A tap is the menu's A press; 90 ms is enough for the guest to see it. */
    private void tapA() {
        PadButton a = buttonFor(BTN_A);
        if (a != null && a.down) {
            return;
        }
        final int sequence = ++tapSequence;
        GameActivity.nativePadButton(BTN_A, true);
        postDelayed(() -> {
            if (sequence == tapSequence && (a == null || !a.down)) {
                GameActivity.nativePadButton(BTN_A, false);
            }
        }, TAP_MS);
        Logx.d("TouchOverlay", "tap -> A");
    }

    // ---------------------------------------------------------------- draw

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawStick(canvas, leftCx, leftCy, lx, ly, "MOVE");
        drawStick(canvas, rightCx, rightCy, rx, ry, "LOOK");
        if (lookPointer >= 0 && rightPointer < 0) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.5f));
            paint.setColor(Color.argb(80, 236, 240, 244));
            canvas.drawCircle(rightCx, rightCy, stickRadius * 1.12f, paint);
        }

        for (PadButton button : buttons) {
            drawButton(canvas, button);
        }
    }

    private void drawStick(Canvas canvas, float cx, float cy, float dx, float dy, String label) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(70, 10, 13, 17));
        canvas.drawCircle(cx, cy, stickRadius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(Color.argb(110, 232, 180, 74));
        canvas.drawCircle(cx, cy, stickRadius, paint);

        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.argb(60, 236, 240, 244));
        canvas.drawLine(cx - stickRadius * 0.55f, cy, cx + stickRadius * 0.55f, cy, paint);
        canvas.drawLine(cx, cy - stickRadius * 0.55f, cx, cy + stickRadius * 0.55f, paint);

        float kx = cx + dx * stickRadius * 0.55f;
        float ky = cy + dy * stickRadius * 0.55f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(150, 24, 30, 37));
        canvas.drawCircle(kx, ky, knobRadius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(Color.argb(210, 232, 180, 74));
        canvas.drawCircle(kx, ky, knobRadius, paint);

        textPaint.setTextSize(stickRadius * 0.26f);
        textPaint.setColor(Color.argb(120, 236, 240, 244));
        canvas.drawText(label, cx, cy - stickRadius - dp(8), textPaint);
    }

    private void drawButton(Canvas canvas, PadButton button) {
        boolean down = button.down;
        float cx = button.bounds.centerX();
        float cy = button.bounds.centerY();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(down ? Color.argb(225, 232, 180, 74) : Color.argb(92, 10, 13, 17));
        if (button.round) {
            canvas.drawCircle(cx, cy, button.bounds.width() * 0.5f, paint);
        } else {
            float radius = dp(10);
            canvas.drawRoundRect(button.bounds, radius, radius, paint);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(down ? Color.WHITE : Color.argb(180, 232, 180, 74));
        if (button.round) {
            canvas.drawCircle(cx, cy, button.bounds.width() * 0.5f, paint);
        } else {
            float radius = dp(10);
            canvas.drawRoundRect(button.bounds, radius, radius, paint);
        }

        textPaint.setTextSize(button.round ? button.bounds.width() * 0.42f
                : button.bounds.height() * 0.42f);
        textPaint.setColor(down ? Color.rgb(12, 12, 12) : WHITE);
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float baseline = cy - (metrics.ascent + metrics.descent) * 0.5f;
        canvas.drawText(button.label, cx, baseline, textPaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
