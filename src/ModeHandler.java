import javax.swing.*;
import java.awt.*;
import java.io.File;

public abstract class ModeHandler {
    protected NamePickerApp app;
    protected SchemeManager schemeManager;
    protected NameManager nameManager;

    public ModeHandler(NamePickerApp app) {
        this.app = app;
        this.schemeManager = app.getSchemeManager();
        this.nameManager = app.getNameManager();
    }

    // 获取模式特定的按钮1
    public abstract JButton getModeButton1();

    // 获取模式特定的按钮2
    public abstract JButton getModeButton2();

    // 处理按钮1点击事件
    public abstract void handleButton1Click();

    // 处理按钮2点击事件
    public abstract void handleButton2Click();

    // 获取按钮1文本
    public abstract String getButton1Text();

    // 获取按钮2文本
    public abstract String getButton2Text();
}
