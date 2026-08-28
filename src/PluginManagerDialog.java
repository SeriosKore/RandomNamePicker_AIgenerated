import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * 插件管理界面：
 * - 查看已安装/已识别插件列表（名称、版本、状态：启用中/已禁用/未安装）；
 * - 启用/禁用插件（即时生效，设置界面的相关选项自动联动显示/隐藏）；
 * - 卸载插件（删除 extensions/ 下的 JAR）；
 * - 安装新插件（选择 JAR 文件复制到 extensions/）。
 */
public class PluginManagerDialog extends JDialog {
    private NamePickerApp mainApp;
    private PluginManager pluginManager;
    private DefaultTableModel tableModel;
    private JTable pluginTable;
    private JButton toggleButton;
    private JButton uninstallButton;
    private JButton installButton;

    public PluginManagerDialog(NamePickerApp mainApp, PluginManager pluginManager) {
        super(mainApp, "插件管理", true);
        this.mainApp = mainApp;
        this.pluginManager = pluginManager;
        setSize(560, 400);
        setLocationRelativeTo(mainApp);
        initializeComponents();
        reloadTable();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    private void initializeComponents() {
        setLayout(new BorderLayout(10, 10));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel tipLabel = new JLabel("提示：启用/禁用即时生效；系统托盘菜单项需重启程序后刷新。");
        tipLabel.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        tipLabel.setForeground(Color.GRAY);
        topPanel.add(tipLabel);
        add(topPanel, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(new Object[]{"插件名称", "版本", "状态"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        pluginTable = new JTable(tableModel);
        pluginTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        pluginTable.setRowHeight(26);
        pluginTable.getSelectionModel().addListSelectionListener(e -> updateButtons());
        JScrollPane scrollPane = new JScrollPane(pluginTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder("插件列表"));
        add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        toggleButton = new JButton("启用/禁用");
        uninstallButton = new JButton("卸载");
        installButton = new JButton("安装插件...");
        JButton closeButton = new JButton("关闭");
        toggleButton.addActionListener(e -> toggleSelected());
        uninstallButton.addActionListener(e -> uninstallSelected());
        installButton.addActionListener(e -> installPlugin());
        closeButton.addActionListener(e -> dispose());
        buttonPanel.add(toggleButton);
        buttonPanel.add(uninstallButton);
        buttonPanel.add(installButton);
        buttonPanel.add(closeButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void reloadTable() {
        tableModel.setRowCount(0);
        for (PluginManager.PluginInfo info : pluginManager.getPluginInfos()) {
            String status;
            if (!info.installed) {
                status = "未安装";
            } else if (info.enabled && info.loaded) {
                status = "启用中";
            } else if (info.enabled) {
                status = "启用（重启生效）";
            } else {
                status = "已禁用";
            }
            tableModel.addRow(new Object[]{info.name, info.version, status});
        }
        updateButtons();
    }

    private PluginManager.PluginInfo selectedInfo() {
        int row = pluginTable.getSelectedRow();
        if (row < 0) {
            return null;
        }
        List<PluginManager.PluginInfo> infos = pluginManager.getPluginInfos();
        if (row >= infos.size()) {
            return null;
        }
        return infos.get(row);
    }

    private void updateButtons() {
        PluginManager.PluginInfo info = selectedInfo();
        boolean installed = info != null && info.installed;
        toggleButton.setEnabled(installed);
        uninstallButton.setEnabled(installed);
    }

    private void toggleSelected() {
        PluginManager.PluginInfo info = selectedInfo();
        if (info == null || !info.installed) {
            return;
        }
        if (info.enabled) {
            pluginManager.disablePlugin(info.id);
            JOptionPane.showMessageDialog(this, "插件已禁用：" + info.name, "提示", JOptionPane.INFORMATION_MESSAGE);
        } else {
            pluginManager.enablePlugin(info.id);
            JOptionPane.showMessageDialog(this, "插件已启用：" + info.name, "提示", JOptionPane.INFORMATION_MESSAGE);
        }
        mainApp.refreshPluginUI();
        reloadTable();
    }

    private void uninstallSelected() {
        PluginManager.PluginInfo info = selectedInfo();
        if (info == null || !info.installed) {
            return;
        }
        int result = JOptionPane.showConfirmDialog(this,
                "确定要卸载插件 \"" + info.name + "\" 吗？\n卸载将删除其文件（" + info.jarName + "）。",
                "确认卸载",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.YES_OPTION) {
            return;
        }
        pluginManager.uninstallPlugin(info.id);
        mainApp.refreshPluginUI();
        reloadTable();
        JOptionPane.showMessageDialog(this, "插件已卸载：" + info.name, "提示", JOptionPane.INFORMATION_MESSAGE);
    }

    private void installPlugin() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("插件文件 (*.jar)", "jar"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            String error = pluginManager.installPlugin(file);
            if (error == null) {
                JOptionPane.showMessageDialog(this, "插件安装成功！", "提示", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, error, "错误", JOptionPane.ERROR_MESSAGE);
            }
            mainApp.refreshPluginUI();
            reloadTable();
        }
    }
}
