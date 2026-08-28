import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * 全屏透明画布（画笔插件）。
 * 输入优先走原生 WM_POINTER 指针消息（笔/触摸/鼠标统一，含触点面积与压感）；
 * JNA 不可用时自动降级为 AWT 鼠标事件（鼠标左键画、右键橡皮）。
 * 墨水绘制在离屏缓冲中，绘制过程增量渲染 + 局部重绘，保证流畅不卡手。
 */
public class PenOverlayWindow extends JWindow implements PenNative.PenInputBridge {

    private CanvasPanel panel;
    private BufferedImage inkBuffer;
    private Map<Integer, PenStroke> activeStrokes = new HashMap<>();
    private PenNative.PenWndProc wndProc;   // 强引用，防止 JNA 回调被回收
    private boolean nativeAvailable;
    private Color currentColor = Color.BLACK;
    private PenToolbar toolbar;

    public PenOverlayWindow() {
        super();
        setBackground(new Color(0, 0, 0, 0));
        setAlwaysOnTop(true);

        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        Rectangle bounds = device.getDefaultConfiguration().getBounds();
        setBounds(bounds);

        panel = new CanvasPanel();
        setContentPane(panel);
        inkBuffer = new BufferedImage(Math.max(1, bounds.width), Math.max(1, bounds.height),
                BufferedImage.TYPE_INT_ARGB);

        // Esc 退出画笔
        setFocusableWindowState(true);
        panel.setFocusable(true);
        panel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    dispose();
                }
            }
        });
    }

    /** 显示画布并安装输入（原生优先）。 */
    public void showOverlay() {
        setVisible(true);
        toFront();
        panel.requestFocusInWindow();

        nativeAvailable = false;
        wndProc = PenNative.installWndProc(this, this);
        if (wndProc != null) {
            nativeAvailable = true;
            LogManager.log("画笔插件：已启用原生指针输入（笔/触摸/鼠标）", "PEN_NATIVE_ON");
        } else {
            installFallbackListeners();
            LogManager.log("画笔插件：原生输入不可用，降级为鼠标输入", "PEN_FALLBACK");
        }

        if (toolbar != null) {
            toolbar.setVisible(true);
        }
    }

    /** AWT 降级输入：左键画、右键橡皮。 */
    private void installFallbackListeners() {
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                boolean eraser = SwingUtilities.isRightMouseButton(e);
                PenStroke stroke = new PenStroke(eraser);
                stroke.addPoint(e.getX(), e.getY(), -1, 3.2);
                activeStrokes.put(-1, stroke);
                paintStroke(stroke, true);
                repaintArea(e.getX(), e.getY(), 8);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                PenStroke stroke = activeStrokes.remove(-1);
                if (stroke != null) {
                    stroke.addPoint(e.getX(), e.getY(), -1, 3.2);
                    stroke.finished = true;
                    paintStroke(stroke, true);
                    repaintArea(e.getX(), e.getY(), 20);
                }
            }
        });
        panel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                PenStroke stroke = activeStrokes.get(-1);
                if (stroke != null) {
                    stroke.addPoint(e.getX(), e.getY(), -1, 3.2);
                    paintStroke(stroke, false);
                    repaintArea(e.getX(), e.getY(), 12);
                }
            }
        });
    }

    // ---------- 原生指针回调 ----------

    @Override
    public boolean onPointerDown(int pointerId, int type, boolean eraser, double x, double y) {
        PenStroke stroke = new PenStroke(eraser);
        stroke.pressure = pressureOf(pointerId, type);
        stroke.baseWidth = baseWidth(type);
        stroke.addPoint(x, y, stroke.pressure, stroke.baseWidth);
        activeStrokes.put(pointerId, stroke);
        paintStroke(stroke, true);
        repaintArea(x, y, 8);
        return true;
    }

    @Override
    public boolean onPointerUpdate(int pointerId, double x, double y) {
        PenStroke stroke = activeStrokes.get(pointerId);
        if (stroke == null) {
            return true;
        }
        stroke.addPoint(x, y, stroke.pressure, stroke.baseWidth);
        paintStroke(stroke, false);
        repaintArea(x, y, 14);
        return true;
    }

    @Override
    public boolean onPointerUp(int pointerId, double x, double y) {
        PenStroke stroke = activeStrokes.remove(pointerId);
        if (stroke == null) {
            return true;
        }
        stroke.addPoint(x, y, stroke.pressure, stroke.baseWidth);
        stroke.finished = true;
        paintStroke(stroke, true);   // 整条重绘，应用收笔渐细
        repaintArea(x, y, 20);
        return true;
    }

    // ---------- 绘制 ----------

    private void paintStroke(PenStroke stroke, boolean whole) {
        Graphics2D g = inkBuffer.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (stroke.isEraser()) {
            g.setComposite(AlphaComposite.Clear);
        } else {
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(currentColor);
        }
        PenStrokeRenderer.render(g, stroke, currentColor, whole);
        g.dispose();
    }

    private void repaintArea(double x, double y, int pad) {
        panel.repaint((int) x - pad - 12, (int) y - pad - 12, (pad + 12) * 2, (pad + 12) * 2);
    }

    private int typeOf(int pointerId) {
        com.sun.jna.ptr.IntByReference ref = new com.sun.jna.ptr.IntByReference();
        if (PenNative.User32Touch.INSTANCE.GetPointerType(pointerId, ref)) {
            return ref.getValue();
        }
        return PenNative.PT_MOUSE;
    }

    private double baseWidth(int type) {
        if (type == PenNative.PT_PEN) {
            return 3.6;   // 压感再乘
        }
        if (type == PenNative.PT_TOUCH) {
            return 3.0;
        }
        return 3.2;
    }

    private double pressureOf(int pointerId, int type) {
        if (type != PenNative.PT_PEN) {
            return -1;
        }
        try {
            PenNative.PenPointerPenInfo info = new PenNative.PenPointerPenInfo();
            if (PenNative.User32Touch.INSTANCE.GetPointerPenInfo(pointerId, info)) {
                double p = info.pressure / 1024.0;
                return Math.max(0.05, Math.min(1.0, p));
            }
        } catch (Throwable t) {
            // 压感读取失败时退回速度模型
        }
        return -1;
    }

    /** 清屏。 */
    public void clearScreen() {
        Graphics2D g = inkBuffer.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, inkBuffer.getWidth(), inkBuffer.getHeight());
        g.dispose();
        panel.repaint();
    }

    public void setCurrentColor(Color color) {
        this.currentColor = color;
    }

    public void setToolbar(PenToolbar toolbar) {
        this.toolbar = toolbar;
    }

    @Override
    public void dispose() {
        if (toolbar != null) {
            toolbar.dispose();
        }
        activeStrokes.clear();
        super.dispose();
    }

    private class CanvasPanel extends JPanel {
        CanvasPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.drawImage(inkBuffer, 0, 0, null);
        }
    }
}
