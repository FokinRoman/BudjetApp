import java.io.*;
import java.util.*;
import java.util.Scanner;
import java.io.Serializable;

// Модели данных

class User implements Serializable {
    private String login;
    private String passwordHash;
    private Wallet wallet;

    public User(String login, String password) {
        this.login = login;
        this.passwordHash = hashPassword(password);
        this.wallet = new Wallet();
    }

    private String hashPassword(String password) {
        return password; // В реальном приложении используйте криптографическое хеширование
    }

    public String getLogin() { return login; }
    public String getPasswordHash() { return passwordHash; }
    public Wallet getWallet() { return wallet; }
    public void setWallet(Wallet wallet) { this.wallet = wallet; }
}

class Wallet implements Serializable {
    private List<Transaction> transactions = new ArrayList<>();
    private Map<String, Double> budgets = new HashMap<>();
    private double totalIncome = 0;
    private double totalExpense = 0;

    public void addIncome(String category, double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Сумма должна быть положительной");
        transactions.add(new Transaction(category, amount, true));
        totalIncome += amount;
        checkBudgetAlerts();
    }

    public void addExpense(String category, double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Сумма должна быть положительной");
        transactions.add(new Transaction(category, amount, false));
        totalExpense += amount;
        checkBudgetAlerts();
    }

    public void setBudget(String category, double budget) {
        if (budget < 0) throw new IllegalArgumentException("Бюджет не может быть отрицательным");
        budgets.put(category, budget);
    }

    public double getTotalIncome() { return totalIncome; }
    public double getTotalExpense() { return totalExpense; }

    public Map<String, Double> getIncomeByCategory() {
        Map<String, Double> incomeByCategory = new HashMap<>();
        for (Transaction t : transactions) {
            if (t.isIncome()) {
                incomeByCategory.merge(t.getCategory(), t.getAmount(), Double::sum);
            }
        }
        return incomeByCategory;
    }

    public Map<String, Double> getExpenseByCategory() {
        Map<String, Double> expenseByCategory = new HashMap<>();
        for (Transaction t : transactions) {
            if (!t.isIncome()) {
                expenseByCategory.merge(t.getCategory(), t.getAmount(), Double::sum);
            }
        }
        return expenseByCategory;
    }

    public Map<String, Double> getRemainingBudgets() {
        Map<String, Double> remaining = new HashMap<>();
        Map<String, Double> expenses = getExpenseByCategory();
        for (Map.Entry<String, Double> entry : budgets.entrySet()) {
            String category = entry.getKey();
            double budget = entry.getValue();
            double spent = expenses.getOrDefault(category, 0.0);
            remaining.put(category, budget - spent);
        }
        return remaining;
    }

    private void checkBudgetAlerts() {
        Map<String, Double> remaining = getRemainingBudgets();
        for (Map.Entry<String, Double> entry : remaining.entrySet()) {
            if (entry.getValue() < 0) {
                System.out.println("ВНИМАНИЕ: Превышен бюджет по категории '" + entry.getKey() + "' на " +
                        Math.abs(entry.getValue()) + " руб.");
            }
        }
        if (totalExpense > totalIncome) {
            System.out.println("ВНИМАНИЕ: Расходы превышают доходы на " + (totalExpense - totalIncome) + " руб.!");
        }
    }

    public List<Transaction> getTransactions() { return transactions; }
    public Map<String, Double> getBudgets() { return budgets; }
}

class Transaction implements Serializable {
    private String category;
    private double amount;
    private boolean isIncome;

    public Transaction(String category, double amount, boolean isIncome) {
        this.category = category;
        this.amount = amount;
        this.isIncome = isIncome;
    }

    public String getCategory() { return category; }
    public double getAmount() { return amount; }
    public boolean isIncome() { return isIncome; }
}

// Сервисный слой

class FinanceService {
    private Map<String, User> users = new HashMap<>();
    private User currentUser;

    // Авторизация
    public boolean login(String login, String password) {
        User user = users.get(login);
        if (user != null && user.getPasswordHash().equals(hashPassword(password))) {
            currentUser = user;
            loadUserData();
            return true;
        }
        return false;
    }

    // Регистрация
    public boolean register(String login, String password) {
        if (users.containsKey(login)) return false;
        users.put(login, new User(login, password));
        return true;
    }

    // Операции с финансами
    public void addIncome(String category, double amount) {
        currentUser.getWallet().addIncome(category, amount);
    }

    public void addExpense(String category, double amount) {
        currentUser.getWallet().addExpense(category, amount);
    }

    public void setBudget(String category, double budget) {
        currentUser.getWallet().setBudget(category, budget);
    }

    // Вывод информации
    public void printSummary() {
        Wallet wallet = currentUser.getWallet();
        System.out.println("\n=== ОБЗОР ФИНАНСОВ ===");
        System.out.printf("Общий доход: %,.2f\n", wallet.getTotalIncome());
        System.out.printf("Общие расходы: %,.2f\n", wallet.getTotalExpense());

        System.out.println("\nДоходы по категориям:");
        for (Map.Entry<String, Double> entry : wallet.getIncomeByCategory().entrySet()) {
            System.out.printf("%s: %,.2f\n", entry.getKey(), entry.getValue());
        }

        System.out.println("\nБюджет по категориям:");
        Map<String, Double> remainingBudgets = wallet.getRemainingBudgets();
        for (Map.Entry<String, Double> entry : remainingBudgets.entrySet()) {
            double budget = wallet.getBudgets().get(entry.getKey());
            System.out.printf("%s: %,.2f, Оставшийся бюджет: %,.2f\n",
                    entry.getKey(), budget, entry.getValue());
        }
    }

    // Перевод между кошельками
    public boolean transferToUser(String targetLogin, double amount, String description) {
        if (!users.containsKey(targetLogin)) return false;
        User targetUser = users.get(targetLogin);

        // Расход у отправителя
        currentUser.getWallet().addExpense("Перевод: " + targetLogin, amount);
        // Доход у получателя
        targetUser.getWallet().addIncome("Перевод от: " + currentUser.getLogin(), amount);

        return true;
    }

    // Сохранение/загрузка
    private void saveUserData() {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream("data_" + currentUser.getLogin() + ".dat"))) {
            oos.writeObject(currentUser.getWallet());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadUserData() {
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream("data_" + currentUser.getLogin() + ".dat"))) {
            Wallet loadedWallet = (Wallet) ois.readObject();
            currentUser.setWallet(loadedWallet);
        } catch (IOException | ClassNotFoundException e) {
            // Файл не найден — создаём новый кошелёк
        }
    }

    public void shutdown() {
        if (currentUser != null) saveUserData();
    }

    private String hashPassword(String password) {
        return password;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUserWallet(Wallet wallet) {
        if (currentUser != null) {
            currentUser.setWallet(wallet);
        }
    }
}

// Основной класс приложения

class FinanceApp {
    private static final FinanceService financeService = new FinanceService();
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("=== Приложение для управления личными финансами ===");

        while (true) {
            if (financeService.getCurrentUser() == null) {
                showAuthMenu();
            } else {
                showMainMenu();
            }
        }
    }

    private static void showAuthMenu() {
        System.out.println("\n--- Авторизация ---");
        System.out.println("1. Войти");
        System.out.println("2. Зарегистрироваться");
        System.out.println("3. Выход");
        System.out.print("Выберите действие: ");

        int choice = getIntInput();

        switch (choice) {
            case 1:
                login();
                break;
            case 2:
                register();
                break;
            case 3:
                System.out.println("До свидания!");
                financeService.shutdown();
                System.exit(0);
            default:
                System.out.println("Неверный выбор. Попробуйте снова.");
        }
    }

    private static void login() {
        System.out.print("Логин: ");
        String login = scanner.nextLine();
        System.out.print("Пароль: ");
        String password = scanner.nextLine();

        if (financeService.login(login, password)) {
            System.out.println("Успешный вход! Добро пожаловать, " + login);
        } else {
            System.out.println("Ошибка авторизации. Проверьте логин и пароль.");
        }
    }

    private static void register() {
        System.out.print("Логин: ");
        String login = scanner.nextLine();
        System.out.print("Пароль: ");
        String password = scanner.nextLine();

        if (financeService.register(login, password)) {
            System.out.println("Регистрация успешна! Теперь войдите в систему.");
        } else {
            System.out.println("Пользователь с таким логином уже существует.");
        }
    }

    private static void showMainMenu() {
        System.out.println("\n--- Главное меню ---");
        System.out.println("1. Добавить доход");
        System.out.println("2. Добавить расход");
        System.out.println("3. Установить бюджет по категории");
        System.out.println("4. Показать обзор финансов");
        System.out.println("5. Перевод другому пользователю");
        System.out.println("6. Выйти из аккаунта");
        System.out.println("7. Выход из приложения");
        System.out.print("Выберите действие: ");

        int choice = getIntInput();

        switch (choice) {
            case 1:
                addIncome();
                break;
            case 2:
                addExpense();
                break;
            case 3:
                setBudget();
                break;
            case 4:
                financeService.printSummary();
                break;
            case 5:
                transferToUser();
                break;
            case 6:
                System.out.println("Выход из аккаунта: " + financeService.getCurrentUser().getLogin());
                financeService.shutdown();
                financeService.setCurrentUserWallet(null);
                break;
            case 7:
                System.out.println("Завершение работы приложения...");
                financeService.shutdown();
                scanner.close();
                System.exit(0);
            default:
                System.out.println("Неверный выбор. Попробуйте снова.");
        }
    }

    private static void addIncome() {
        System.out.print("Категория дохода: ");
        String category = scanner.nextLine();
        System.out.print("Сумма дохода: ");
        double amount = getDoubleInput();
        financeService.addIncome(category, amount);
        System.out.println("Доход добавлен успешно!");
    }

    private static void addExpense() {
        System.out.print("Категория расхода: ");
        String category = scanner.nextLine();
        System.out.print("Сумма расхода: ");
        double amount = getDoubleInput();
        financeService.addExpense(category, amount);
        System.out.println("Расход добавлен успешно!");
    }

    private static void setBudget() {
        System.out.print("Категория: ");
        String category = scanner.nextLine();
        System.out.print("Бюджет: ");
        double budget = getDoubleInput();
        financeService.setBudget(category, budget);
        System.out.println("Бюджет установлен успешно!");
    }

    private static void transferToUser() {
        System.out.print("Логин получателя: ");
        String targetLogin = scanner.nextLine();
        System.out.print("Сумма перевода: ");
        double amount = getDoubleInput();
        System.out.print("Описание перевода: ");
        String description = scanner.nextLine();

        if (financeService.transferToUser(targetLogin, amount, description)) {
            System.out.println("Перевод выполнен успешно!");
        } else {
            System.out.println("Ошибка: пользователь не найден.");
        }
    }

    private static int getIntInput() {
        while (!scanner.hasNextInt()) {
            System.out.println("Пожалуйста, введите целое число.");
            scanner.next();
        }
        int value = scanner.nextInt();
        scanner.nextLine(); // очистка буфера
        return value;
    }

    private static double getDoubleInput() {
        while (!scanner.hasNextDouble()) {
            System.out.println("Пожалуйста, введите число.");
            scanner.next();
        }
        double value = scanner.nextDouble();
        scanner.nextLine(); // очистка буфера
        return value;
    }
}
