import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.W32APIOptions;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinDef.*;

/**
 * 画笔插件 JNA 原生访问层。
 * 通过替换窗口过程（WndProc）接收 WM_POINTER* 指针消息，
 * 从而获得：笔/触摸/鼠标的原始输入、触点面积（橡皮识别）、压感（笔锋）。
 */
public class PenNative {

    public static final int WM_POINTERDOWN = 0x0246;
    public static final int WM_POINTERUP = 0x0247;
    public static final int WM_POINTERUPDATE = 0x0245;

    /** 传统鼠标消息（鼠标绘制的可靠路径）。 */
    public static final int WM_MOUSEMOVE = 0x0200;
    public static final int WM_LBUTTONDOWN = 0x0201;
    public static final int WM_LBUTTONUP = 0x0202;
    public static final int WM_RBUTTONDOWN = 0x0204;
    public static final int WM_RBUTTONUP = 0x0205;

    public static final int MK_LBUTTON = 0x0001;
    public static final int MK_RBUTTON = 0x0002;

    /** 鼠标笔画使用的固定指针 ID。 */
    public static final int MOUSE_POINTER_ID = 0x7F00;

    /** 指针类型（GetPointerType 返回值）。 */
    public static final int PT_POINTER = 1;
    public static final int PT_TOUCH = 2;
    public static final int PT_PEN = 3;
    public static final int PT_MOUSE = 4;

    public static final int VK_RBUTTON = 0x02;

    /** 大触点判定阈值：触屏接触面过大（手掌/拳头）视为橡皮。 */
    public static final int ERASER_TOUCH_MIN_W = 52;
    public static final int ERASER_TOUCH_MIN_H = 40;
    public static final int ERASER_TOUCH_MIN_AREA = 2600;

    /** 扩展 User32：指针相关 API（jna-platform 未收录，自定义声明）。 */
    public interface User32Touch extends User32 {
        User32Touch INSTANCE = Native.load("user32", User32Touch.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean GetPointerType(int pointerId, IntByReference pointerType);

        boolean GetPointerTouchInfo(int pointerId, PenPointerTouchInfo pointerInfo);

        boolean GetPointerPenInfo(int pointerId, PenPointerPenInfo penInfo);

        short GetAsyncKeyState(int vKey);

        /** 以 Callback 形式挂窗口过程（JNA 会把回调转为函数指针）。 */
        Pointer SetWindowLongPtr(HWND hWnd, int nIndex, Callback dwNewLong);
    }

    /** POINTER_INFO：指针基础信息（按 Windows SDK 布局自定义）。 */
    public static class PenPointerInfo extends Structure {
        public int pointerType;
        public int pointerId;
        public int frameId;
        public int pointerFlags;
        public Pointer sourceDevice;
        public HWND hwndTarget;
        public POINT ptPixelLocation = new POINT();
        public POINT ptHimetricLocation = new POINT();
        public POINT ptPixelLocationRaw = new POINT();
        public POINT ptHimetricLocationRaw = new POINT();
        public int dwTime;
        public int historyCount;
        public int inputData;
        public int dwKeyStates;
        public long performanceCount;
        public int buttonChangeType;

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList(
                    "pointerType", "pointerId", "frameId", "pointerFlags",
                    "sourceDevice", "hwndTarget",
                    "ptPixelLocation", "ptHimetricLocation",
                    "ptPixelLocationRaw", "ptHimetricLocationRaw",
                    "dwTime", "historyCount", "inputData", "dwKeyStates",
                    "performanceCount", "buttonChangeType");
        }
    }

    /** POINTER_TOUCH_INFO：含触点矩形（橡皮识别依据）。 */
    public static class PenPointerTouchInfo extends Structure {
        public PenPointerInfo pointerInfo = new PenPointerInfo();
        public int touchFlags;
        public int touchMask;
        public RECT rcContact = new RECT();
        public RECT rcContactRaw = new RECT();
        public int orientation;
        public int pressure;

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("pointerInfo", "touchFlags", "touchMask",
                    "rcContact", "rcContactRaw", "orientation", "pressure");
        }
    }

    /** POINTER_PEN_INFO：含压感（笔锋依据）。 */
    public static class PenPointerPenInfo extends Structure {
        public PenPointerInfo pointerInfo = new PenPointerInfo();
        public int penFlags;
        public int penMask;
        public int pressure;
        public int rotation;
        public int tiltX;
        public int tiltY;

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList("pointerInfo", "penFlags", "penMask",
                    "pressure", "rotation", "tiltX", "tiltY");
        }
    }

    /** 指针输入回调桥（由画布窗口实现）。 */
    public interface PenInputBridge {
        boolean onPointerDown(int pointerId, int type, boolean eraser, double x, double y);

        boolean onPointerUpdate(int pointerId, double x, double y);

        boolean onPointerUp(int pointerId, double x, double y);
    }

    /** 替换后的窗口过程：拦截 WM_POINTER*，其余消息转发给原窗口过程。 */
    public static class PenWndProc implements WinUser.WindowProc {
        private final Pointer prevProc;
        private final PenInputBridge bridge;

        public PenWndProc(Pointer prevProc, PenInputBridge bridge) {
            this.prevProc = prevProc;
            this.bridge = bridge;
        }

        @Override
        public LRESULT callback(HWND hwnd, int uMsg, WPARAM wParam, LPARAM lParam) {
            // ---- 传统鼠标消息：保证鼠标左键按下/拖动/释放可正常绘制 ----
            if (uMsg == WM_LBUTTONDOWN || uMsg == WM_RBUTTONDOWN) {
                try {
                    long lp = lParam.longValue();
                    double x = (short) (lp & 0xFFFF);
                    double y = (short) ((lp >> 16) & 0xFFFF);
                    bridge.onPointerDown(MOUSE_POINTER_ID, PT_MOUSE, uMsg == WM_RBUTTONDOWN, x, y);
                } catch (Throwable t) {
                    // 原生层异常不影响其他消息
                }
                return new LRESULT(0);
            }
            if (uMsg == WM_MOUSEMOVE) {
                try {
                    int keys = wParam.intValue();
                    if ((keys & (MK_LBUTTON | MK_RBUTTON)) != 0) {
                        long lp = lParam.longValue();
                        double x = (short) (lp & 0xFFFF);
                        double y = (short) ((lp >> 16) & 0xFFFF);
                        bridge.onPointerUpdate(MOUSE_POINTER_ID, x, y);
                    }
                } catch (Throwable t) {
                    // 原生层异常不影响其他消息
                }
                return new LRESULT(0);
            }
            if (uMsg == WM_LBUTTONUP || uMsg == WM_RBUTTONUP) {
                try {
                    long lp = lParam.longValue();
                    double x = (short) (lp & 0xFFFF);
                    double y = (short) ((lp >> 16) & 0xFFFF);
                    bridge.onPointerUp(MOUSE_POINTER_ID, x, y);
                } catch (Throwable t) {
                    // 原生层异常不影响其他消息
                }
                return new LRESULT(0);
            }

            // ---- 指针消息：笔/触摸在此处理；鼠标指针消息放行，由上方传统鼠标路径处理 ----
            if (uMsg == WM_POINTERDOWN || uMsg == WM_POINTERUPDATE || uMsg == WM_POINTERUP) {
                try {
                    int pointerId = wParam.intValue();
                    long lp = lParam.longValue();
                    // 低 16 位 X、高 16 位 Y（有符号，屏幕坐标）
                    double x = (short) (lp & 0xFFFF);
                    double y = (short) ((lp >> 16) & 0xFFFF);

                    if (typeOf(pointerId) == PT_MOUSE) {
                        // 鼠标统一走传统鼠标消息路径，避免重复绘制
                        return User32.INSTANCE.CallWindowProc(prevProc, hwnd, uMsg, wParam, lParam);
                    }

                    if (uMsg == WM_POINTERUPDATE) {
                        if (bridge.onPointerUpdate(pointerId, x, y)) {
                            return new LRESULT(0);
                        }
                    } else if (uMsg == WM_POINTERUP) {
                        if (bridge.onPointerUp(pointerId, x, y)) {
                            return new LRESULT(0);
                        }
                    } else {
                        int type = typeOf(pointerId);
                        boolean eraser = isEraser(pointerId, type);
                        if (bridge.onPointerDown(pointerId, type, eraser, x, y)) {
                            return new LRESULT(0);
                        }
                    }
                } catch (Throwable t) {
                    // 原生层异常不影响其他消息
                }
                // 标记为已处理，避免系统再合成鼠标消息干扰绘制
                return new LRESULT(0);
            }
            return User32.INSTANCE.CallWindowProc(prevProc, hwnd, uMsg, wParam, lParam);
        }

        private static int typeOf(int pointerId) {
            IntByReference ref = new IntByReference();
            if (User32Touch.INSTANCE.GetPointerType(pointerId, ref)) {
                return ref.getValue();
            }
            return PT_MOUSE;
        }

        /**
         * 橡皮判定：
         * - 触摸：接触矩形超过阈值（手掌/拳头等大面积接触）→ 橡皮；
         * - 鼠标：右键按下 → 橡皮。
         */
        private static boolean isEraser(int pointerId, int type) {
            if (type == PT_TOUCH) {
                PenPointerTouchInfo info = new PenPointerTouchInfo();
                if (User32Touch.INSTANCE.GetPointerTouchInfo(pointerId, info)) {
                    int w = info.rcContact.right - info.rcContact.left;
                    int h = info.rcContact.bottom - info.rcContact.top;
                    return (w >= ERASER_TOUCH_MIN_W && h >= ERASER_TOUCH_MIN_H)
                            || (w * h >= ERASER_TOUCH_MIN_AREA);
                }
                return false;
            }
            if (type == PT_MOUSE || type == PT_POINTER) {
                return (User32Touch.INSTANCE.GetAsyncKeyState(VK_RBUTTON) & 0x8000) != 0;
            }
            return false;
        }
    }

    /**
     * 把自定义窗口过程挂到 AWT 窗口上。
     * 返回 PenWndProc 实例（调用方必须保持强引用），失败返回 null。
     */
    public static PenWndProc installWndProc(java.awt.Window window, PenInputBridge bridge) {
        try {
            Pointer hwnd = Native.getComponentPointer(window);
            if (hwnd == null) {
                return null;
            }
            HWND hWnd = new HWND(hwnd);
            Pointer old = User32.INSTANCE.SetWindowLongPtr(hWnd, WinUser.GWL_WNDPROC, Pointer.NULL);
            PenWndProc proc = new PenWndProc(old, bridge);
            User32Touch.INSTANCE.SetWindowLongPtr(hWnd, WinUser.GWL_WNDPROC, proc);
            return proc;
        } catch (Throwable t) {
            return null;
        }
    }
}
