package com.settlr.ui.screens;

import com.settlr.exception.DataAccessException;
import com.settlr.exception.SettlrException;
import com.settlr.ui.ModalPopup;
import com.settlr.ui.components.GlassCard;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.components.StyledTextField;
import com.settlr.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import static com.settlr.ui.components.Ui.*;

/**
 * A small popup form: a title, a few labelled fields, and Cancel / Save. The Profile page
 * uses it for display name, username, password and "How to pay me".
 *
 *     FormDialog d = new FormDialog("Edit display name", "This is what your housemates see.");
 *     StyledTextField name = d.addField("Display name", new StyledTextField("e.g. Ben"));
 *     d.onSave("Save", () -> profileService.changeDisplayName(name.getText()), () -> reload());
 *     d.showOver(this);
 *
 * If the save throws a SettlrException, its message shows in red and the popup stays open.
 */
public class FormDialog extends ModalPopup {

    /** What Save does. Throw a SettlrException with a readable message when something is wrong. */
    @FunctionalInterface
    public interface SaveAction {
        void save() throws SettlrException;
    }

    private static final int FORM_WIDTH = 360;

    private final GlassCard card = new GlassCard(null);
    private final JPanel fields = new JPanel();
    private final JLabel errorLabel = new JLabel(" ");
    private final RoundedButton saveButton = new RoundedButton("Save");
    private final Map<JComponent, JPanel> rows = new HashMap<>();
    private final Map<JComponent, JLabel> labels = new HashMap<>();
    private JComponent firstField;
    private SaveAction action;
    private Runnable onSaved;

    public FormDialog(String title, String subtitle) {
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(22, 26, 20, 26));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(label(title, Font.BOLD, 20, Theme.TEXT), BorderLayout.CENTER);
        JPanel closeHolder = new JPanel(new BorderLayout());
        closeHolder.setOpaque(false);
        closeHolder.add(createCloseButton(), BorderLayout.NORTH);
        header.add(closeHolder, BorderLayout.EAST);
        fixed(header, 30);
        card.add(header);
        if (subtitle != null) {
            JLabel sub = label("<html>" + subtitle + "</html>", Font.PLAIN, 12, Theme.MUTED_TEXT);
            card.add(Box.createVerticalStrut(4));
            fixed(sub, wrappedHeight(sub));
            card.add(sub);
        }

        fields.setOpaque(false);
        fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
        fields.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(fields);

        errorLabel.setFont(Theme.font(Font.PLAIN, 12));
        errorLabel.setForeground(Theme.DANGER);
        card.add(Box.createVerticalStrut(6));
        fixed(errorLabel, 16);
        card.add(errorLabel);

        RoundedButton cancel = RoundedButton.outline("Cancel");
        cancel.setArc(RoundedButton.PILL);
        cancel.setFont(Theme.font(Font.BOLD, 14));
        cancel.addActionListener(e -> close());
        saveButton.setArc(RoundedButton.PILL);
        saveButton.setFont(Theme.font(Font.BOLD, 14));
        saveButton.addActionListener(e -> save());
        JPanel buttons = new JPanel(new GridBagLayout());
        buttons.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 14);
        buttons.add(cancel, c);
        c.weightx = 1.5;
        c.insets = new Insets(0, 0, 0, 0);
        buttons.add(saveButton, c);
        card.add(Box.createVerticalStrut(6));
        fixed(buttons, 42);
        card.add(buttons);

        add(card);
    }

    /** Adds a labelled field and returns it. Text fields get the pill style; Enter in them saves. */
    public <T extends JComponent> T addField(String labelText, T field) {
        JLabel l = label(labelText, Font.BOLD, 12, Theme.PRIMARY);
        l.setBorder(new EmptyBorder(0, 6, 0, 0));
        if (field instanceof JTextField) {
            StyledTextField.usePillStyle((JTextField) field);
            field.setFont(Theme.font(Font.PLAIN, 13));
            ((JTextField) field).addActionListener(e -> save());
        }
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(Box.createVerticalStrut(14));
        fixed(l, 16);
        row.add(l);
        row.add(Box.createVerticalStrut(5));
        fixed(field, 36);
        row.add(field);
        rows.put(field, row);
        labels.put(field, l);
        fields.add(row);
        if (firstField == null) {
            firstField = field;
        }
        return field;
    }

    /** A grey line of help under the fields, e.g. "Leave it blank if you only take cash." */
    public JLabel addHint(String text) {
        JLabel hint = label(text, Font.PLAIN, 11, Theme.MUTED_TEXT);
        hint.setBorder(new EmptyBorder(8, 6, 0, 0));
        fixed(hint, 22);
        fields.add(hint);
        return hint;
    }

    /** Renames a field's label, e.g. "Number" to "Bank and account number". */
    public void setFieldLabel(JComponent field, String text) {
        JLabel l = labels.get(field);
        if (l != null) {
            l.setText(text);
        }
    }

    /** Shows or hides a field together with its label. */
    public void setFieldVisible(JComponent field, boolean visible) {
        JPanel row = rows.get(field);
        if (row != null) {
            row.setVisible(visible);
            card.revalidate();
            repaint();
        }
    }

    /** What the Save button says and does; onSaved runs after the popup closes. */
    public void onSave(String buttonText, SaveAction action, Runnable onSaved) {
        saveButton.setText(buttonText);
        this.action = action;
        this.onSaved = onSaved;
    }

    @Override
    public void showOver(Component anyComponent) {
        super.showOver(anyComponent);
        if (firstField != null) {
            SwingUtilities.invokeLater(firstField::requestFocusInWindow);
        }
    }

    private void save() {
        if (action == null || !saveButton.isEnabled()) {
            return;
        }
        errorLabel.setText(" ");
        saveButton.setEnabled(false); // stops a double click from saving twice
        try {
            action.save();
        } catch (DataAccessException e) {
            errorLabel.setText("Can't save right now. Is MySQL running?");
            saveButton.setEnabled(true);
            e.printStackTrace();
            return;
        } catch (SettlrException e) {
            errorLabel.setText(e.getMessage());
            saveButton.setEnabled(true);
            return;
        }
        close();
        if (onSaved != null) {
            onSaved.run();
        }
    }

    /** How tall an HTML label is when it wraps inside the form's width. */
    private static int wrappedHeight(JLabel htmlLabel) {
        View view = (View) htmlLabel.getClientProperty(BasicHTML.propertyKey);
        if (view == null) {
            return htmlLabel.getPreferredSize().height;
        }
        view.setSize(FORM_WIDTH, 0);
        return (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS));
    }

    private static void fixed(JComponent c, int height) {
        Dimension d = new Dimension(FORM_WIDTH, height);
        c.setPreferredSize(d);
        c.setMinimumSize(d);
        c.setMaximumSize(d);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

}
