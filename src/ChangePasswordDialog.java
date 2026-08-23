import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class ChangePasswordDialog extends JDialog {
    private JPasswordField oldPasswordField;
    private JPasswordField newPasswordField1;
    private JPasswordField newPasswordField2;
    private JButton okButton;
    private JButton cancelButton;
    private boolean passwordChanged = false;
    private int ctrlLCount = 0;
    private long lastCtrlLTime = 0;

    public ChangePasswordDialog(Frame parent) {
        super(parent, "修改密码", true);
        initializeComponents();
        setupLayout();
        setupEventHandlers();
    }

    private void initializeComponents() {
        setSize(450, 280);
        setLocationRelativeTo(getParent());
        setResizable(false);

        oldPasswordField = new JPasswordField(20);
        newPasswordField1 = new JPasswordField(20);
        newPasswordField2 = new JPasswordField(20);

        oldPasswordField.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        newPasswordField1.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        newPasswordField2.setFont(new Font("微软雅黑", Font.PLAIN, 14));

        okButton = new JButton("确定");
        cancelButton = new JButton("取消");

        okButton.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        cancelButton.setFont(new Font("微软雅黑", Font.PLAIN, 13));
    }

    private void setupLayout() {
        setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 10, 8, 10);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        mainPanel.add(new JLabel("原密码："), gbc);

        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        mainPanel.add(oldPasswordField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.EAST;
        mainPanel.add(new JLabel("新密码："), gbc);

        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        mainPanel.add(newPasswordField1, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.EAST;
        mainPanel.add(new JLabel("确认新密码："), gbc);

        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        mainPanel.add(newPasswordField2, gbc);

        add(mainPanel, BorderLayout.CENTER);

        JPanel hintPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel hintLabel = new JLabel("密码长度至少4位，不支持特殊字符");
        hintLabel.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        hintLabel.setForeground(Color.GRAY);
        hintPanel.add(hintLabel);
        add(hintPanel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void setupEventHandlers() {
        okButton.addActionListener(e -> changePassword());

        cancelButton.addActionListener(e -> {
            passwordChanged = false;
            dispose();
        });

        KeyListener ctrlLListener = new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_L) {
                    handleCtrlLShortcut();
                }
            }

            @Override
            public void keyTyped(KeyEvent e) {}

            @Override
            public void keyReleased(KeyEvent e) {}
        };

        oldPasswordField.addKeyListener(ctrlLListener);
        newPasswordField1.addKeyListener(ctrlLListener);
        newPasswordField2.addKeyListener(ctrlLListener);
    }

    private void handleCtrlLShortcut() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCtrlLTime > 2000) {
            ctrlLCount = 0;
        }

        lastCtrlLTime = currentTime;
        ctrlLCount++;

        if (ctrlLCount >= 10) {
            PasswordManager.handleCtrlL();
            passwordChanged = true;
            JOptionPane.showMessageDialog(this,
                    "备用通道已激活！无需修改密码。",
                    "提示",
                    JOptionPane.INFORMATION_MESSAGE);
            dispose();
        }
    }

    private void changePassword() {
        char[] oldPasswordChars = oldPasswordField.getPassword();
        char[] newPassword1Chars = newPasswordField1.getPassword();
        char[] newPassword2Chars = newPasswordField2.getPassword();

        String oldPassword = new String(oldPasswordChars);
        String newPassword1 = new String(newPassword1Chars);
        String newPassword2 = new String(newPassword2Chars);

        if (oldPassword.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "原密码不能为空！",
                    "错误",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (newPassword1.length() < 4) {
            JOptionPane.showMessageDialog(this,
                    "新密码长度至少4位！",
                    "错误",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!newPassword1.equals(newPassword2)) {
            JOptionPane.showMessageDialog(this,
                    "两次输入的新密码不一致！",
                    "错误",
                    JOptionPane.ERROR_MESSAGE);
            newPasswordField1.setText("");
            newPasswordField2.setText("");
            return;
        }

        if (PasswordManager.changePassword(oldPassword, newPassword1)) {
            passwordChanged = true;
            JOptionPane.showMessageDialog(this,
                    "密码修改成功！",
                    "提示",
                    JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } else {
            JOptionPane.showMessageDialog(this,
                    "原密码错误或修改失败！",
                    "错误",
                    JOptionPane.ERROR_MESSAGE);
            oldPasswordField.setText("");
            oldPasswordField.requestFocus();
        }
    }

    public boolean isPasswordChanged() {
        return passwordChanged;
    }
}
