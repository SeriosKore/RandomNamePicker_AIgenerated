import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class SchemeManagerDialog extends JDialog {
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
        add(bottomPanel, BorderLayout.SOUTH);
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
}
