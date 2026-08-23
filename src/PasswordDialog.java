import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class PasswordDialog extends JDialog {
    private JPasswordField passwordField;
    private JButton okButton;
    private JButton cancelButton;
    private boolean passwordVerified = false;
    private int ctrlLCount = 0;
    private long lastCtrlLTime = 0;

    public PasswordDialog(Frame parent) {
        super(parent, "密码验证", true);
        initializeComponents();
        setupLayout();
        setupEventHandlers();
    }

    private void initializeComponents() {
        setSize(380, 180);
        setLocationRelativeTo(getParent());
        setResizable(false);

        passwordField = new JPasswordField(20);
        passwordField.setFont(new Font("微软雅黑", Font.PLAIN, 14));

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
        gbc.insets = new Insets(10, 10, 10, 10);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        mainPanel.add(new JLabel("请输入密码："), gbc);

        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        mainPanel.add(passwordField, gbc);

        add(mainPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void setupEventHandlers() {
        okButton.addActionListener(e -> verifyPassword());

        passwordField.addActionListener(e -> verifyPassword());

        cancelButton.addActionListener(e -> {
            passwordVerified = false;
            dispose();
        });

        passwordField.addKeyListener(new KeyListener() {
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
        });
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
            passwordVerified = true;
            JOptionPane.showMessageDialog(this,
                    "备用通道已激活！",
                    "提示",
                    JOptionPane.INFORMATION_MESSAGE);
            dispose();
        }
    }

    private void verifyPassword() {
        char[] passwordChars = passwordField.getPassword();
        String password = new String(passwordChars);

        if (password.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "密码不能为空！",
                    "错误",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (PasswordManager.verifyPassword(password)) {
            passwordVerified = true;
            dispose();
        } else {
            JOptionPane.showMessageDialog(this,
                    "密码错误！",
                    "错误",
                    JOptionPane.ERROR_MESSAGE);
            passwordField.setText("");
            passwordField.requestFocus();
        }
    }

    public boolean isPasswordVerified() {
        return passwordVerified;
    }
}
