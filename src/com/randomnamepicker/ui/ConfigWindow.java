package com.randomnamepicker.ui;

import com.randomnamepicker.core.LogManager;
import com.randomnamepicker.core.NameManager;
import com.randomnamepicker.core.PasswordManager;
import com.randomnamepicker.core.SchemeManager;
import com.randomnamepicker.model.Scheme;
import com.randomnamepicker.plugin.UiZone;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public class ConfigWindow extends JDialog {
    private NameManager nameManager;
    private SchemeManager schemeManager;
    private JTable nameTable;
    private DefaultTableModel tableModel;
    private JTextField nameField;
    private JComboBox<Scheme> schemeComboBox;
    private NamePickerApp mainApp;
    private JButton addButton;
    private JButton deleteButton;
    private JButton importButton;
    private JButton exportButton;
    private JButton saveButton;

    public ConfigWindow(NamePickerApp parent, NameManager nameManager, SchemeManager schemeManager) {
        super(parent, "名单配置", true);
        this.mainApp = parent;
        this.nameManager = nameManager;
        this.schemeManager = schemeManager;
        initializeComponents();
        setupLayout();
        loadData();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    private void initializeComponents() {
        setSize(800, 500);
        setLocationRelativeTo(getParent());
        setResizable(true);

        tableModel = new DefaultTableModel(new Object[]{"姓名"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        nameTable = new JTable(tableModel);
        nameTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        nameField = new JTextField(15);
        schemeComboBox = new JComboBox<>();
        
        addButton = new JButton("添加");
        deleteButton = new JButton("删除");
        importButton = new JButton("导入");
        exportButton = new JButton("导出");
        saveButton = new JButton("保存");
        
        loadSchemes();
    }

    private void loadSchemes() {
        schemeComboBox.removeAllItems();
        schemeComboBox.addItem(new Scheme("默认方案", "name_list"));
        for (Scheme scheme : schemeManager.getAllSchemes()) {
            schemeComboBox.addItem(scheme);
        }
    }

    private void setupLayout() {
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new FlowLayout());
        topPanel.setBorder(BorderFactory.createTitledBorder("方案选择"));
        topPanel.add(new JLabel("选择方案:"));
        topPanel.add(schemeComboBox);
        add(topPanel, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(nameTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder("名单列表"));
        add(scrollPane, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new FlowLayout());
        inputPanel.add(new JLabel("姓名:"));
        inputPanel.add(nameField);
        inputPanel.add(addButton);
        inputPanel.add(deleteButton);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(importButton);
        buttonPanel.add(exportButton);
        buttonPanel.add(saveButton);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        // 修复(2026-09-04)：inputPanel 与 bottomPanel 原先都 add 到 BorderLayout.SOUTH，
        // 后加入的 bottomPanel 会顶掉 inputPanel，导致“姓名输入 + 添加/删除”整行不可见。
        // 改为两行纵向叠放后再整体放入 SOUTH，复原“添加名字/删除名字”的可见可用。
        JPanel southPanel = new JPanel();
        southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));
        southPanel.add(inputPanel);
        southPanel.add(bottomPanel);

        // 插件 UI 槽二期：配置名单窗口插件按钮区（追加为 SOUTH 第三行；无动作则不渲染）
        JPanel pluginZonePanel = PluginUiSupport.createZonePanel(UiZone.CONFIG_WINDOW);
        if (pluginZonePanel != null) {
            southPanel.add(pluginZonePanel);
        }

        add(southPanel, BorderLayout.SOUTH);

        addButton.addActionListener(this::addName);
        deleteButton.addActionListener(this::deleteName);
        importButton.addActionListener(this::importNames);
        exportButton.addActionListener(this::exportNames);
        saveButton.addActionListener(this::saveData);
        schemeComboBox.addActionListener(e -> loadData());

        nameField.addActionListener(this::addName);
    }

    private void loadData() {
        tableModel.setRowCount(0);
        Scheme selectedScheme = (Scheme) schemeComboBox.getSelectedItem();
        if (selectedScheme != null) {
            List<String> names = nameManager.loadNamesForScheme(selectedScheme.getName());
            for (String name : names) {
                tableModel.addRow(new Object[]{name});
            }
        }
    }

    private void addName(ActionEvent e) {
        if (PasswordManager.isLocked()) {
            JOptionPane.showMessageDialog(this, "当前为锁定模式，请先解锁", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String name = nameField.getText().trim();
        if (!name.isEmpty()) {
            tableModel.addRow(new Object[]{name});
            nameField.setText("");
            nameField.requestFocus();
            LogManager.log(name, "添加姓名");
        }
    }

    private void deleteName(ActionEvent e) {
        if (PasswordManager.isLocked()) {
            JOptionPane.showMessageDialog(this, "当前为锁定模式，请先解锁", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        int[] selectedRows = nameTable.getSelectedRows();
        if (selectedRows.length > 0) {
            StringBuilder deletedNames = new StringBuilder();
            for (int i = selectedRows.length - 1; i >= 0; i--) {
                String name = (String) tableModel.getValueAt(selectedRows[i], 0);
                if (name != null) {
                    deletedNames.append(name).append(",");
                }
                tableModel.removeRow(selectedRows[i]);
            }
            
            if (deletedNames.length() > 0) {
                LogManager.log(deletedNames.substring(0, deletedNames.length() - 1), "删除姓名");
            }
        } else {
            JOptionPane.showMessageDialog(this, "请选择要删除的姓名", "提示", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void importNames(ActionEvent e) {
        if (PasswordManager.isLocked()) {
            JOptionPane.showMessageDialog(this, "当前为锁定模式，请先解锁", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("文本文件 (*.txt)", "txt"));

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = fileChooser.getSelectedFile();
                Scheme selectedScheme = (Scheme) schemeComboBox.getSelectedItem();
                if (selectedScheme != null) {
                    nameManager.importFromFile(selectedScheme.getName(), file);
                    loadData();
                    LogManager.log(selectedScheme.getName() + "-" + file.getName(), "导入名单");
                    JOptionPane.showMessageDialog(this, "导入成功！", "提示", JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (Exception ex) {
                LogManager.log("导入失败-" + ex.getMessage(), "错误");
                JOptionPane.showMessageDialog(this, "导入失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }
    }

    private void exportNames(ActionEvent e) {
        if (PasswordManager.isLocked()) {
            JOptionPane.showMessageDialog(this, "当前为锁定模式，请先解锁", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("文本文件 (*.txt)", "txt"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = fileChooser.getSelectedFile();
                if (!file.getName().toLowerCase().endsWith(".txt")) {
                    file = new File(file.getAbsolutePath() + ".txt");
                }

                Scheme selectedScheme = (Scheme) schemeComboBox.getSelectedItem();
                if (selectedScheme != null) {
                    nameManager.exportToFile(selectedScheme.getName(), file);
                    LogManager.log(selectedScheme.getName() + "-" + file.getAbsolutePath(), "导出名单");
                    JOptionPane.showMessageDialog(this, "导出成功！", "提示", JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (Exception ex) {
                LogManager.log("导出失败-" + ex.getMessage(), "错误");
                JOptionPane.showMessageDialog(this, "导出失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }
    }

    private void saveData(ActionEvent e) {
        if (PasswordManager.isLocked()) {
            JOptionPane.showMessageDialog(this, "当前为锁定模式，请先解锁", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        try {
            Scheme selectedScheme = (Scheme) schemeComboBox.getSelectedItem();
            if (selectedScheme != null) {
                java.util.List<String> names = new java.util.ArrayList<>();
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    String name = (String) tableModel.getValueAt(i, 0);
                    if (name != null && !name.trim().isEmpty()) {
                        names.add(name.trim());
                    }
                }
                nameManager.saveNamesForScheme(selectedScheme.getName(), names);
                LogManager.log(selectedScheme.getName() + "-共" + names.size() + "条记录", "保存名单");
                JOptionPane.showMessageDialog(this, "保存成功！", "提示", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            LogManager.log("保存失败-" + ex.getMessage(), "错误");
            JOptionPane.showMessageDialog(this, "保存失败：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }
}
