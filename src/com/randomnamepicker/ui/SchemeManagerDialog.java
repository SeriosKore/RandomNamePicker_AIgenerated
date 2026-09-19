package com.randomnamepicker.ui;

import com.randomnamepicker.core.LogManager;
import com.randomnamepicker.core.PasswordManager;
import com.randomnamepicker.core.SchemeManager;
import com.randomnamepicker.model.Scheme;
import com.randomnamepicker.plugin.UiZone;
import java.awt.*;
import java.awt.event.ActionEvent;
import javax.swing.*;

public class SchemeManagerDialog extends JDialog {

    /** G1：方案名非法字符（逗号会破坏 index.txt 解析；其余会破坏数据文件名清洗与删除路径）。 */
    private static final String INVALID_NAME_CHARS = ",\\/:*?\"<>|";
    /** G1：方案名长度上限。 */
    private static final int MAX_NAME_LENGTH = 30;

    private JTextField schemeNameField;
    private JComboBox<String> typeComboBox;
    private JList<Scheme> schemeList;
    private DefaultListModel<Scheme> listModel;
    private SchemeManager schemeManager;
    private NamePickerApp mainApp;

    public SchemeManagerDialog(NamePickerApp parent, SchemeManager schemeManager) {
        super(parent, "方案管理", true);
        this.mainApp = parent;
        this.schemeManager = schemeManager;
        initializeComponents();
        setupLayout();
        loadSchemes();
        // G1：存量同名冲突提示（不自动改名——改名需“旧盐解密+新盐重加密”，见方法注释）
        javax.swing.SwingUtilities.invokeLater(this::warnReservedNameCollision);
    }

    private void initializeComponents() {
        setSize(500, 400);
        setLocationRelativeTo(getParent());
        setResizable(false);

        schemeNameField = new JTextField(20);
        typeComboBox = new JComboBox<>(new String[]{"name_list", "number", "seat"});

        listModel = new DefaultListModel<>();
        schemeList = new JList<>(listModel);
        schemeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // G1：方案名可重名（内置“默认方案”不在 index.txt 中），列表补类型以便区分
        schemeList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Scheme) {
                    Scheme scheme = (Scheme) value;
                    setText(scheme.getName() + "（" + (scheme.getType() == null ? "" : scheme.getType()) + "）");
                }
                return this;
            }
        });
    }

    private void setupLayout() {
        setLayout(new BorderLayout());

        JPanel createPanel = new JPanel(new GridBagLayout());
        createPanel.setBorder(BorderFactory.createTitledBorder("创建新方案"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.gridx = 0; gbc.gridy = 0;
        createPanel.add(new JLabel("方案名称:"), gbc);
        gbc.gridx = 1;
        createPanel.add(schemeNameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        createPanel.add(new JLabel("方案类型:"), gbc);
        gbc.gridx = 1;
        createPanel.add(typeComboBox, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        JButton createButton = new JButton("创建方案");
        createButton.addActionListener(this::createScheme);
        createPanel.add(createButton, gbc);

        JPanel listPanel = new JPanel(new BorderLayout());
        listPanel.setBorder(BorderFactory.createTitledBorder("现有方案"));
        JScrollPane scrollPane = new JScrollPane(schemeList);
        listPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel listButtonPanel = new JPanel(new FlowLayout());
        JButton deleteButton = new JButton("删除选中方案");
        deleteButton.addActionListener(this::deleteScheme);
        listButtonPanel.add(deleteButton);
        listPanel.add(listButtonPanel, BorderLayout.SOUTH);

        add(createPanel, BorderLayout.NORTH);
        add(listPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout());
        JButton closeButton = new JButton("关闭");
        closeButton.addActionListener(e -> dispose());
        bottomPanel.add(closeButton);

        // 插件 UI 槽二期：方案管理对话框插件按钮区（置于关闭按钮之上；无动作则不渲染）
        JPanel pluginZonePanel = PluginUiSupport.createZonePanel(UiZone.SCHEME_MANAGER_WINDOW);
        if (pluginZonePanel == null) {
            add(bottomPanel, BorderLayout.SOUTH);
        } else {
            JPanel bottomWrap = new JPanel();
            bottomWrap.setLayout(new BoxLayout(bottomWrap, BoxLayout.Y_AXIS));
            bottomWrap.add(pluginZonePanel);
            bottomWrap.add(bottomPanel);
            add(bottomWrap, BorderLayout.SOUTH);
        }
    }

    private void loadSchemes() {
        listModel.clear();
        for (Scheme scheme : schemeManager.getAllSchemes()) {
            listModel.addElement(scheme);
        }
    }

    private void createScheme(ActionEvent e) {
        if (PasswordManager.isLocked()) {
            JOptionPane.showMessageDialog(this, "当前为锁定模式，请先解锁", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String name = schemeNameField.getText().trim();
        String type = (String) typeComboBox.getSelectedItem();

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入方案名称！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (type == null || type.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请选择方案类型！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // G1：方案名合法性——index.txt 以“名,类型”存盘，逗号会破坏解析；
        // 其余非法字符会破坏数据文件命名清洗（删除路径用原始名拼接，见 README §6.3）
        if (name.length() > MAX_NAME_LENGTH) {
            JOptionPane.showMessageDialog(this, "方案名称过长（上限 " + MAX_NAME_LENGTH + " 个字符）！",
                    "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        for (int i = 0; i < INVALID_NAME_CHARS.length(); i++) {
            if (name.indexOf(INVALID_NAME_CHARS.charAt(i)) >= 0) {
                JOptionPane.showMessageDialog(this, "方案名称不能包含以下字符：" + INVALID_NAME_CHARS,
                        "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        // G1：内置“默认方案”名保留——旧实现只比对 index.txt 列表，可创建同名方案，
        // 导致两个同名项共用同一组数据文件（名单/数字/座位），且删除会误删内置方案数据
        if (SchemeManager.BUILTIN_DEFAULT_SCHEME_NAME.equals(name)) {
            JOptionPane.showMessageDialog(this, "“" + name + "”为内置方案保留名称，请换一个名称！",
                    "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        for (int i = 0; i < listModel.getSize(); i++) {
            if (listModel.getElementAt(i).getName().equals(name)) {
                JOptionPane.showMessageDialog(this, "方案名称已存在！", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        schemeManager.addScheme(name, type);
        LogManager.log(name + "-" + type, "创建方案");
        loadSchemes();
        schemeNameField.setText("");
        JOptionPane.showMessageDialog(this, "方案创建成功！", "提示", JOptionPane.INFORMATION_MESSAGE);
    }

    private void deleteScheme(ActionEvent e) {
        if (PasswordManager.isLocked()) {
            JOptionPane.showMessageDialog(this, "当前为锁定模式，请先解锁", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        Scheme selectedScheme = schemeList.getSelectedValue();
        if (selectedScheme == null) {
            JOptionPane.showMessageDialog(this, "请选择要删除的方案！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int result = JOptionPane.showConfirmDialog(this,
                "确定要删除方案 \"" + selectedScheme.getName() + "\" 吗？这将删除所有相关数据。",
                "确认删除",
                JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.YES_OPTION) {
            schemeManager.removeScheme(selectedScheme.getName());
            LogManager.log(selectedScheme.getName(), "删除方案");
            loadSchemes();
            JOptionPane.showMessageDialog(this, "方案删除成功！", "提示", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * G1：存量同名冲突提示（只提示、不自动改名）。
     * <p>
     * 加密盐含方案名，改名必须“旧盐解密 → 新盐重新加密”后落盘，自动改名有丢数据风险；
     * 因此这里只做可见提示与后果说明，处置交给用户。
     * </p>
     */
    private void warnReservedNameCollision() {
        for (Scheme scheme : schemeManager.getAllSchemes()) {
            if (SchemeManager.BUILTIN_DEFAULT_SCHEME_NAME.equals(scheme.getName())) {
                LogManager.log("检测到与内置方案同名的自建方案: " + scheme.getName()
                        + "（类型=" + scheme.getType() + "）", "SCHEME_NAME_COLLISION");
                JOptionPane.showMessageDialog(this,
                        "检测到自建方案“" + scheme.getName() + "”与内置“"
                                + SchemeManager.BUILTIN_DEFAULT_SCHEME_NAME + "”同名：\n"
                                + "• 两者共用同一组数据文件（名单 / 数字范围 / 座位），设置会互相影响；\n"
                                + "• 删除该方案会一并删除内置方案的数据文件（名单可由备份自愈，数字/座位不会）；\n"
                                + "• 本次不自动改名（改名需重新加密，风险高），建议手工另建后删除旧方案。",
                        "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
    }
}
