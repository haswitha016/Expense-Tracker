import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ExpenseTrackerGUI extends JFrame {
    // === MySQL Configuration ===
    private static final String DB_URL = "jdbc:mysql://localhost:3306/expenses_db?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "root";          // replace with your MySQL username
    private static final String DB_PASSWORD = "password";  // replace with your MySQL password
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Transaction class
    static class Transaction {
        int id;
        String type;
        double amount;
        String category;
        LocalDate date;
        String note;

        Transaction(int id, String type, double amount, String category, LocalDate date, String note) {
            this.id = id;
            this.type = type;
            this.amount = amount;
            this.category = category;
            this.date = date;
            this.note = note;
        }

        String[] toRow() {
            return new String[]{String.valueOf(id), type, String.format("%.2f", amount), category, date.format(DF), note};
        }
    }

    private java.util.List<Transaction> transactions = new ArrayList<>();
    private int nextId = 1;

    private DefaultTableModel tableModel;
    private JTable table;

    private JTextField typeField, amountField, categoryField, dateField, noteField;

    public ExpenseTrackerGUI() {
        super("Expense Tracker (MySQL)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 500);
        setLocationRelativeTo(null);

        // === UI Components ===
        JPanel formPanel = new JPanel(new GridLayout(2, 5, 5, 5));
        typeField = new JTextField();
        amountField = new JTextField();
        categoryField = new JTextField();
        dateField = new JTextField(DF.format(LocalDate.now()));
        noteField = new JTextField();

        formPanel.add(labeled("Type (Income/Expense):", typeField));
        formPanel.add(labeled("Amount:", amountField));
        formPanel.add(labeled("Category:", categoryField));
        formPanel.add(labeled("Date (YYYY-MM-DD):", dateField));
        formPanel.add(labeled("Note:", noteField));

        JButton addBtn = new JButton("Add Transaction");
        JButton deleteBtn = new JButton("Delete Selected");
        JButton summaryBtn = new JButton("View Summary");
        JButton exitBtn = new JButton("Exit");

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(addBtn);
        buttonPanel.add(deleteBtn);
        buttonPanel.add(summaryBtn);
        buttonPanel.add(exitBtn);

        tableModel = new DefaultTableModel(new String[]{"ID", "Type", "Amount", "Category", "Date", "Note"}, 0);
        table = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(table);

        add(formPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        // Button actions
        addBtn.addActionListener(e -> addTransaction());
        deleteBtn.addActionListener(e -> deleteTransaction());
        summaryBtn.addActionListener(e -> showSummary());
        exitBtn.addActionListener(e -> System.exit(0));

        // Initialize DB and load data
        createTableIfNotExists();
        loadFromDB();
        refreshTable();
    }

    private JPanel labeled(String label, JTextField field) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(label), BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    private void createTableIfNotExists() {
        String sql = "CREATE TABLE IF NOT EXISTS transactions (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "type VARCHAR(20) NOT NULL, " +
                "amount DOUBLE NOT NULL, " +
                "category VARCHAR(50) NOT NULL, " +
                "date DATE NOT NULL, " +
                "note VARCHAR(255))";
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error creating table: " + e.getMessage());
        }
    }

    private void addTransaction() {
        String type = typeField.getText().trim();
        if (!type.equalsIgnoreCase("Income") && !type.equalsIgnoreCase("Expense")) {
            JOptionPane.showMessageDialog(this, "Type must be 'Income' or 'Expense'.");
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(amountField.getText().trim());
            if (amount <= 0) throw new NumberFormatException();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Invalid amount.");
            return;
        }
        String category = categoryField.getText().trim();
        if (category.isEmpty()) category = "General";

        LocalDate date;
        try {
            date = LocalDate.parse(dateField.getText().trim(), DF);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Invalid date format.");
            return;
        }
        String note = noteField.getText().trim();

        String sql = "INSERT INTO transactions(type, amount, category, date, note) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, capitalize(type));
            ps.setDouble(2, amount);
            ps.setString(3, category);
            ps.setDate(4, java.sql.Date.valueOf(date));
            ps.setString(5, note);
            ps.executeUpdate();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Failed to insert transaction: " + e.getMessage());
            return;
        }

        loadFromDB();
        refreshTable();
        clearForm();
    }

    private void deleteTransaction() {
        int row = table.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a transaction to delete.");
            return;
        }
        int id = Integer.parseInt(tableModel.getValueAt(row, 0).toString());
        String sql = "DELETE FROM transactions WHERE id=?";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Failed to delete: " + e.getMessage());
        }

        loadFromDB();
        refreshTable();
    }

    private void loadFromDB() {
        transactions.clear();
        String sql = "SELECT * FROM transactions ORDER BY id";
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Transaction t = new Transaction(
                        rs.getInt("id"),
                        rs.getString("type"),
                        rs.getDouble("amount"),
                        rs.getString("category"),
                        rs.getDate("date").toLocalDate(),
                        rs.getString("note")
                );
                transactions.add(t);
                nextId = Math.max(nextId, t.id + 1);
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error loading data: " + e.getMessage());
        }
    }

    private void showSummary() {
        double totalIncome = 0, totalExpense = 0;
        Map<String, Double> byCategory = new HashMap<>();

        for (Transaction t : transactions) {
            if (t.type.equalsIgnoreCase("Income")) totalIncome += t.amount;
            else totalExpense += t.amount;
            if (t.type.equalsIgnoreCase("Expense"))
                byCategory.put(t.category, byCategory.getOrDefault(t.category, 0.0) + t.amount);
        }

        double balance = totalIncome - totalExpense;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Total Income: %.2f%nTotal Expense: %.2f%nBalance: %.2f%n%n", totalIncome, totalExpense, balance));
        sb.append("Expenses by Category:\n");
        for (Map.Entry<String, Double> e : byCategory.entrySet()) {
            sb.append(String.format(" - %s : %.2f%n", e.getKey(), e.getValue()));
        }
        JOptionPane.showMessageDialog(this, sb.toString(), "Summary", JOptionPane.INFORMATION_MESSAGE);
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        for (Transaction t : transactions) {
            tableModel.addRow(t.toRow());
        }
    }

    private void clearForm() {
        typeField.setText("");
        amountField.setText("");
        categoryField.setText("");
        dateField.setText(DF.format(LocalDate.now()));
        noteField.setText("");
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        String lower = s.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ExpenseTrackerGUI().setVisible(true));
    }
}
