package com.settlr.ui.components;

import javax.swing.JPasswordField;
import java.awt.Graphics;

/**
 * Same look as StyledTextField, but hides what you type.
 * Reuses StyledTextField's helpers because JPasswordField extends JTextField.
 */
public class StyledPasswordField extends JPasswordField {

    private final String placeholder;

    public StyledPasswordField(String placeholder) {
        this.placeholder = placeholder;
        StyledTextField.applyStyle(this);
    }

    @Override
    protected void paintComponent(Graphics g) {
        StyledTextField.paintRoundedBackground(this, g);
        super.paintComponent(g);
        StyledTextField.paintPlaceholder(this, g, placeholder);
    }
}
