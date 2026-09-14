package Expense_Tracker_Project;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

public class ExpenseTracker {

    enum Category {
        FOOD, TRAVEL, BILLS, SHOPPING, HEALTH, OTHER
    }

    static class Expense {
        int id;
        LocalDate date;
        Category cat;
        double amount;
        String note;

        Expense(int id, LocalDate d, Category c, double a, String n) {
            this.id = id;
            this.date = d;
            this.cat = c;
            this.amount = a;
            this.note = n;
        }

        String csv() {
            return id + "," + date + "," + cat + "," + amount + "," + note.replace(",", " ");
        }

        static Expense fromCsv(String line) {
            String[] a = line.split(",", 5);
            return new Expense(
                    Integer.parseInt(a[0]),
                    LocalDate.parse(a[1]),
                    Category.valueOf(a[2]),
                    Double.parseDouble(a[3]),
                    a.length > 4 ? a[4] : ""
            );
        }
        @Override
        public String toString() {
            return String.format("#%-3d %s | %-9s | %8.2f | %s",id, date, cat, amount, note);
        }
    }

    private final List<Expense> expenses = new ArrayList<>();
    private int nextId = 1;
    private double monthlyBudget = 0;
    private final Scanner sc = new Scanner(System.in);
    private static final Path FILE = Path.of("expenses.csv");

    public static void main(String[] args) {
        new ExpenseTracker().run();
    }

    void run() {
        load();
        System.out.println("=== Expense Tracker (" + expenses.size() + " records) ===");
        while (true) {
            System.out.println( "\n1.Add 2.List 3.Filter Category 4.Filter Date Range "
                    + "5.Monthly Summary 6.Category Totals 7.Set Budget 0.Save & Exit");
            System.out.print("Choose: ");
            String c = sc.nextLine().trim();
            switch (c) {

                case "1" -> add();
                case "2" -> list(expenses);
                case "3" -> filterCat();
                case "4" -> filterDate();
                case "5" -> monthlySummary();
                case "6" -> catTotals();
                case "7" -> setBudget();
                case "0" -> {
                    save();
                    System.out.println("Saved to " + FILE.toAbsolutePath());
                    return;
                }
                default ->
                        System.out.println(" ! Choose 0-7");
            }
        }
    }
    void add() {
        LocalDate d;
        System.out.print("Date YYYY-MM-DD [today]: ");
        String s = sc.nextLine().trim();
        try {
            d = s.isEmpty() ? LocalDate.now() : LocalDate.parse(s);
        } catch (Exception e) {
            System.out.println(" ! Bad date");
            return;
        }
        System.out.print("Category " + Arrays.toString(Category.values()) + ": ");
        String cs = sc.nextLine().trim().toUpperCase();
        Category cat;
        try {
            cat = Category.valueOf(cs);
        } catch (Exception e) {
            System.out.println(" ! Unknown category");
            return;
        }
        System.out.print("Amount: ");
        double amt;

        try {
            amt = Double.parseDouble(sc.nextLine().trim());
        } catch (Exception e) {
            System.out.println(" ! Invalid amount");
            return;
        }
        System.out.print("Note: ");
        String note = sc.nextLine().trim();
        Expense e = new Expense(nextId++, d, cat, amt, note);
        expenses.add(e);
        save();
        System.out.println(" Added " + e);
        checkBudget(d);
    }
    void list(List<Expense> list) {
        if (list.isEmpty()) {
            System.out.println(" (no records)");
            return;
        }
        list.stream()
                .sorted(Comparator.comparing(ex -> ex.date))
                .forEach(System.out::println);

        double tot = list.stream()
                .mapToDouble(ex -> ex.amount)
                .sum();
        System.out.printf(" Total: %.2f (%d records)%n", tot, list.size());
    }
    void filterCat() {
        System.out.print("Category: ");
        String cs = sc.nextLine()
                .trim()
                .toUpperCase();
        try {
            Category cat = Category.valueOf(cs);
            list( expenses.stream().filter(e -> e.cat == cat).toList());
        } catch (Exception e) {
            System.out.println(" ! Unknown category");
        }
    }
    void filterDate() {
        try {
            System.out.print("From YYYY-MM-DD: ");
            LocalDate from = LocalDate.parse(sc.nextLine().trim());
            System.out.print("To YYYY-MM-DD: ");
            LocalDate to = LocalDate.parse(sc.nextLine().trim());
            list( expenses.stream().filter(e -> !e.date.isBefore(from) 
            		&& !e.date.isAfter(to)).toList());
        } catch (Exception e) {
            System.out.println(" ! Bad date");
        }
    }
    void monthlySummary() {
        if (expenses.isEmpty()) {
            System.out.println(" (no data)");
            return;
        }
        var byMonth = expenses.stream()
                .collect( Collectors.groupingBy( e -> YearMonth.from(e.date),
                                Collectors.summingDouble(e -> e.amount)));
        byMonth.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(en ->
                        System.out.printf(" %s : %.2f%n", en.getKey(), en.getValue()));
        if (monthlyBudget > 0) {
            YearMonth cur = YearMonth.now();
            double curTot = byMonth.getOrDefault( cur, 0.0 );
            System.out.printf(" Budget: %.2f This month: %.2f Remaining: %.2f%n",
                    monthlyBudget,
                    curTot,
                    monthlyBudget - curTot );
        }
    }
    void catTotals() {
        var byCat = expenses.stream()
                .collect( Collectors.groupingBy( e -> e.cat,
                                Collectors.summingDouble( e -> e.amount ) ) );
        byCat.forEach( (c, v) ->
                        System.out.printf(" %-9s : %.2f%n", c, v ));
        double tot = expenses.stream()
                .mapToDouble(e -> e.amount)
                .sum();
        System.out.printf(" GRAND TOTAL: %.2f%n",tot );
    }
    void setBudget() {
        System.out.print("Monthly budget (0=disable): ");
        try {
            monthlyBudget = Double.parseDouble(sc.nextLine().trim());
            System.out.printf(" Budget set to %.2f%n",monthlyBudget);
        } catch (Exception e) {
            System.out.println(" ! Invalid budget");
        }
    }
    void checkBudget(LocalDate d) {
        if (monthlyBudget <= 0) {
            return;
        }
        YearMonth ym = YearMonth.from(d);
        double tot = expenses.stream()
                .filter(e -> YearMonth.from(e.date).equals(ym))
                .mapToDouble(e -> e.amount)
                .sum();
        if (tot > monthlyBudget) {
            System.out.printf(" !! Budget exceeded for %s: %.2f > %.2f%n",ym,tot,monthlyBudget);
        } else if (tot > monthlyBudget * 0.8) {
            System.out.printf(" !! Warning: 80%% of budget used%n");
        }
    }
    void save() {
        try {
            Files.write(FILE,expenses.stream()
                            .map(Expense::csv)
                            .toList());
        } catch (IOException e) {
            System.out.println(" ! Error saving file: " + e.getMessage());
        }
    }
    void load() {
        if (!Files.exists(FILE)) {
            return;
        }
        try {
            for (String l : Files.readAllLines(FILE)) {
                if (l.isBlank()) {
                    continue;
                }
                try {
                    Expense e = Expense.fromCsv(l);
                    expenses.add(e);
                    nextId = Math.max(nextId, e.id + 1);
                } catch (Exception ex) {
                    System.out.println(" ! Skipping invalid record");
                }
            }
        } catch (IOException e) {
            System.out.println(" ! Error loading file: " + e.getMessage());
        }
    }
}
