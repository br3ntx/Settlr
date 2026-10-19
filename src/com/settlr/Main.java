package com.settlr;

import com.settlr.ui.screens.LoginScreen;

import javax.swing.*;
import java.awt.Dimension;

/**
 * Entry point of Settlr.
 * For now it opens a plain window showing the login screen.
 * TODO (M1): load fonts, run CatchUpService.runOnStartup(), open MainFrame instead.
 */
public class Main {
    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on"); // smoother text

        // Swing code must run on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Settlr");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1100, 700);
            frame.setMinimumSize(new Dimension(800, 600));
            frame.setLocationRelativeTo(null); // center on screen
            frame.setContentPane(new LoginScreen());
            frame.setVisible(true);
        });
    }
}
