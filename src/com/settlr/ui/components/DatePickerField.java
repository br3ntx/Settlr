package com.settlr.ui.components;

import com.settlr.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Pill that shows a date (mm/dd/yyyy). Clicking it opens a small month calendar.
 * Swing has no built-in date picker, so this one is drawn by hand.
 */
public class DatePickerField extends JComponent {

    private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private LocalDate date;
    private boolean hover;

    public DatePickerField(LocalDate initial) {
        this.date = initial;
        setFont(Theme.font(Font.BOLD, 12));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFocusable(true);
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { openCalendar(); }
            @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
        });
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g2.setColor(Theme.GLASS_FILL);
        g2.fill(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, h, h));
        g2.setColor(hover ? Theme.PRIMARY : Theme.PRIMARY_DARK);
        g2.setStroke(new BasicStroke(2f));
        g2.draw(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, h - 2, h - 2));

        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(date == null ? Theme.MUTED_TEXT : Theme.TEXT);
        g2.drawString(date == null ? "mm/dd/yyyy" : SHOWN.format(date), 14, (h - fm.getHeight()) / 2 + fm.getAscent());

        g2.setColor(Theme.TEXT);
        g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int cx = w - 18, cy = h / 2;
        g2.drawPolyline(new int[]{cx - 4, cx, cx + 4}, new int[]{cy - 2, cy + 2, cy - 2}, 3);
        g2.dispose();
    }

    private void openCalendar() {
        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(new LineBorder(Theme.BORDER, 1));
        popup.add(new CalendarPanel(popup));
        popup.show(this, 0, getHeight() + 4);
    }

    /** One month of day buttons, with < and > to change the month. */
    private class CalendarPanel extends JPanel {
        private final JPopupMenu popup;
        private YearMonth month;

        CalendarPanel(JPopupMenu popup) {
            this.popup = popup;
            this.month = YearMonth.from(date == null ? LocalDate.now() : date);
            setBackground(Theme.INPUT_BACKGROUND);
            setBorder(new EmptyBorder(8, 8, 8, 8));
            build();
        }

        private void build() {
            removeAll();
            setLayout(new BorderLayout(0, 6));

            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            JLabel title = new JLabel(month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                    + " " + month.getYear(), SwingConstants.CENTER);
            title.setFont(Theme.font(Font.BOLD, 13));
            title.setForeground(Theme.TEXT);
            header.add(arrow("\u2039", -1), BorderLayout.WEST);
            header.add(title, BorderLayout.CENTER);
            header.add(arrow("\u203A", 1), BorderLayout.EAST);
            add(header, BorderLayout.NORTH);

            JPanel grid = new JPanel(new GridLayout(0, 7, 2, 2));
            grid.setOpaque(false);
            for (DayOfWeek d : new DayOfWeek[]{DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY}) {
                JLabel l = new JLabel(d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).substring(0, 2),
                        SwingConstants.CENTER);
                l.setFont(Theme.font(Font.PLAIN, 11));
                l.setForeground(Theme.MUTED_TEXT);
                grid.add(l);
            }
            int blanks = month.atDay(1).getDayOfWeek().getValue() % 7; // Sunday = 0
            for (int i = 0; i < blanks; i++) {
                grid.add(new JLabel());
            }
            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                LocalDate d = month.atDay(day);
                grid.add(dayButton(d));
            }
            add(grid, BorderLayout.CENTER);
            revalidate();
            repaint();
            popup.pack();
        }

        private JButton arrow(String text, int step) {
            JButton b = flatButton(text);
            b.setFont(Theme.font(Font.BOLD, 16));
            b.addActionListener(e -> {
                month = month.plusMonths(step);
                build();
            });
            return b;
        }

        private JButton dayButton(LocalDate d) {
            JButton b = flatButton(String.valueOf(d.getDayOfMonth()));
            boolean chosen = d.equals(date);
            boolean today = d.equals(LocalDate.now());
            if (chosen) {
                b.setOpaque(true);
                b.setBackground(Theme.PRIMARY);
                b.setForeground(Theme.ON_PRIMARY);
            } else if (today) {
                b.setForeground(Theme.MINT);
            }
            b.addActionListener(e -> {
                setDate(d);
                popup.setVisible(false);
            });
            return b;
        }

        private JButton flatButton(String text) {
            JButton b = new JButton(text);
            b.setFont(Theme.font(Font.PLAIN, 12));
            b.setForeground(Theme.TEXT);
            b.setContentAreaFilled(false);
            b.setBorderPainted(false);
            b.setFocusPainted(false);
            b.setOpaque(false);
            b.setMargin(new Insets(2, 2, 2, 2));
            b.setPreferredSize(new Dimension(30, 24));
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return b;
        }
    }
}
