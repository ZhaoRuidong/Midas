package org.flymars.devtools.midas.ui.component;

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * A date picker component using IntelliJ native UI components.
 * Compatible with both light and dark themes.
 */
public class DatePickerComponent extends JPanel {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int CELL_SIZE = 28;
    private static final int GRID_ROWS = 6;

    private final JBTextField textField;
    private LocalDate currentDate;
    private YearMonth displayedMonth;
    private JBPopup popup;
    private JPanel calendarGrid;
    private JLabel monthYearLabel;

    public DatePickerComponent() {
        super(new BorderLayout(0, 0));
        setOpaque(false);

        textField = new JBTextField(10);
        textField.addActionListener(e -> commitText());

        JButton dropBtn = new JButton(new ArrowIcon());
        dropBtn.setBorderPainted(false);
        dropBtn.setContentAreaFilled(false);
        dropBtn.setFocusable(false);
        dropBtn.setMargin(new Insets(0, 0, 0, 0));
        dropBtn.setPreferredSize(new Dimension(20, textField.getPreferredSize().height));
        dropBtn.addActionListener(e -> togglePopup());

        add(textField, BorderLayout.CENTER);
        add(dropBtn, BorderLayout.EAST);
    }

    public void setDate(LocalDate date) {
        this.currentDate = date;
        this.displayedMonth = (date != null) ? YearMonth.from(date) : YearMonth.now();
        textField.setText(date != null ? date.format(DATE_FORMAT) : "");
    }

    public LocalDate getDate() {
        commitText();
        return currentDate;
    }

    private void commitText() {
        String text = textField.getText().trim();
        if (!text.isEmpty()) {
            try {
                currentDate = LocalDate.parse(text, DATE_FORMAT);
                displayedMonth = YearMonth.from(currentDate);
            } catch (DateTimeParseException ignored) {
            }
        }
    }

    private void togglePopup() {
        if (popup != null && popup.isVisible()) {
            popup.cancel();
            popup = null;
            return;
        }

        commitText();
        displayedMonth = (currentDate != null) ? YearMonth.from(currentDate) : YearMonth.now();

        JPanel content = buildCalendarPanel();

        popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(content, null)
                .setFocusable(true)
                .setRequestFocus(true)
                .setResizable(false)
                .setMovable(false)
                .createPopup();

        popup.showUnderneathOf(this);
    }

    private JPanel buildCalendarPanel() {
        Color bg = UIUtil.getPanelBackground();

        JPanel root = new JPanel(new BorderLayout(4, 4));
        root.setBackground(bg);
        root.setBorder(new EmptyBorder(6, 8, 6, 8));

        // Navigation header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JButton prev = new JButton(new NavArrowIcon(false));
        styleNavButton(prev);
        prev.addActionListener(e -> {
            displayedMonth = displayedMonth.minusMonths(1);
            refreshCalendar();
        });

        JButton next = new JButton(new NavArrowIcon(true));
        styleNavButton(next);
        next.addActionListener(e -> {
            displayedMonth = displayedMonth.plusMonths(1);
            refreshCalendar();
        });

        monthYearLabel = new JLabel(formatMonthYear(displayedMonth), SwingConstants.CENTER);
        monthYearLabel.setFont(monthYearLabel.getFont().deriveFont(Font.BOLD, 12f));

        header.add(prev, BorderLayout.WEST);
        header.add(monthYearLabel, BorderLayout.CENTER);
        header.add(next, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // Calendar grid
        calendarGrid = new JPanel(new GridLayout(0, 7, 1, 1));
        calendarGrid.setOpaque(false);
        root.add(calendarGrid, BorderLayout.CENTER);

        // Today link
        JLabel todayLabel = new JLabel("Today: " + LocalDate.now().format(DATE_FORMAT));
        todayLabel.setFont(todayLabel.getFont().deriveFont(Font.PLAIN, 11f));
        todayLabel.setForeground(UIUtil.getLabelForeground().brighter());
        todayLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        todayLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                selectDate(LocalDate.now());
            }
        });

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        bottomPanel.setOpaque(false);
        bottomPanel.add(todayLabel);
        root.add(bottomPanel, BorderLayout.SOUTH);

        fillCalendar();
        return root;
    }

    private void styleNavButton(JButton btn) {
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusable(false);
        btn.setFont(btn.getFont().deriveFont(Font.PLAIN, 12f));
        btn.setMargin(new Insets(0, 4, 0, 4));
    }

    private void refreshCalendar() {
        monthYearLabel.setText(formatMonthYear(displayedMonth));
        fillCalendar();
    }

    private void fillCalendar() {
        calendarGrid.removeAll();

        // Day-of-week headers (row 0)
        Font headerFont = calendarGrid.getFont().deriveFont(Font.PLAIN, 10f);
        for (int i = 0; i < 7; i++) {
            DayOfWeek dow = DayOfWeek.MONDAY.plus(i);
            JLabel label = new JLabel(dow.getDisplayName(TextStyle.NARROW, Locale.getDefault()), SwingConstants.CENTER);
            label.setFont(headerFont);
            label.setForeground(UIUtil.getLabelForeground().darker());
            label.setPreferredSize(new Dimension(CELL_SIZE, 18));
            calendarGrid.add(label);
        }

        // Day cells — always 6 rows so the popup size stays constant
        LocalDate firstOfMonth = displayedMonth.atDay(1);
        int offset = firstOfMonth.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue();
        int totalCells = GRID_ROWS * 7;
        int daysInMonth = displayedMonth.lengthOfMonth();
        LocalDate today = LocalDate.now();
        Font dayFont = calendarGrid.getFont().deriveFont(Font.PLAIN, 11f);
        Font todayFont = calendarGrid.getFont().deriveFont(Font.BOLD, 11f);

        for (int cell = 0; cell < totalCells; cell++) {
            int day = cell - offset + 1;
            if (day < 1 || day > daysInMonth) {
                JLabel empty = new JLabel("");
                empty.setPreferredSize(new Dimension(CELL_SIZE, CELL_SIZE));
                calendarGrid.add(empty);
                continue;
            }

            LocalDate date = displayedMonth.atDay(day);
            JButton btn = new JButton(String.valueOf(day));
            btn.setMargin(new Insets(0, 0, 0, 0));
            btn.setFocusable(false);
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setPreferredSize(new Dimension(CELL_SIZE, CELL_SIZE));

            if (date.equals(currentDate)) {
                btn.setFont(todayFont);
                btn.setBackground(UIUtil.getTreeSelectionBackground(true));
                btn.setForeground(UIUtil.getTreeSelectionForeground(true));
                btn.setOpaque(true);
            } else if (date.equals(today)) {
                btn.setFont(todayFont);
            } else {
                btn.setFont(dayFont);
            }

            btn.addActionListener(e -> selectDate(date));
            calendarGrid.add(btn);
        }

        calendarGrid.revalidate();
        calendarGrid.repaint();
    }

    private void selectDate(LocalDate date) {
        currentDate = date;
        displayedMonth = YearMonth.from(date);
        textField.setText(date.format(DATE_FORMAT));
        if (popup != null) {
            popup.cancel();
            popup = null;
        }
    }

    private static String formatMonthYear(YearMonth ym) {
        return ym.format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    /**
     * Dropdown arrow icon for the date picker trigger button.
     */
    private static class ArrowIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(UIUtil.getLabelForeground());
            int midX = x + getIconWidth() / 2;
            g2.fillPolygon(
                    new int[]{midX - 3, midX + 3, midX},
                    new int[]{y + 5, y + 5, y + 11},
                    3
            );
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 10;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }
    }

    /**
     * Left/right arrow icon for calendar month navigation.
     */
    private static class NavArrowIcon implements Icon {
        private final boolean pointingRight;

        NavArrowIcon(boolean pointingRight) {
            this.pointingRight = pointingRight;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(UIUtil.getLabelForeground());
            int midY = y + getIconHeight() / 2;
            if (pointingRight) {
                g2.fillPolygon(
                        new int[]{x + 3, x + 3, x + 12},
                        new int[]{midY - 6, midY + 6, midY},
                        3
                );
            } else {
                g2.fillPolygon(
                        new int[]{x + 10, x + 10, x + 1},
                        new int[]{midY - 6, midY + 6, midY},
                        3
                );
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 14;
        }

        @Override
        public int getIconHeight() {
            return 14;
        }
    }
}
