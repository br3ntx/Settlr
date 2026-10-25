package com.settlr;

import com.settlr.exception.SettlrException;
import com.settlr.service.CatchUpService;
import com.settlr.ui.MainFrame;
import com.settlr.ui.Navigator;

import javax.swing.SwingUtilities;

/**
 * Entry point of Settlr.
 * 1. Catches up on anything that happened while the app was closed
 *    (missed chores, expired swaps, recurring tasks and bills).
 * 2. Opens the main window on the login screen.
 */
public class Main {
    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on"); // smoother text

        try {
            new CatchUpService().runOnStartup();
        } catch (SettlrException e) {
            // No database yet (or it's down): still open the app so the UI can be worked on
            System.err.println("Startup catch-up skipped: " + e.getMessage());
        }

        // Swing code must run on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            Navigator.getInstance().show(Navigator.LOGIN);
            frame.setVisible(true);
        });
    }
}
