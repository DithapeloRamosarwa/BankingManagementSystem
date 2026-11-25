package service;

import dao.AccountDAO;
import dao.CustomerDAO;
import dao.UserDAO;
import model.Account;
import model.Customer;
import model.ChequeAccount;
import model.InvestmentAccount;
import model.SavingsAccount;
import model.User;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/** Adds repeatable classroom/demo records while preserving data already in the database. */
public final class DemoDataSeeder {
    public static final String DEMO_PASSWORD = "BankDemo2026";
    public static final String TELLER_USERNAME = "Dithapelo Ramosarwa";
    private static final String BRANCH = "Gaborone Main";
    private static final List<SampleCustomer> CUSTOMERS = List.of(
            new SampleCustomer("Prince", "Petso", "Gaborone", "76613297", false, Account.Type.SAVINGS),
            new SampleCustomer("Naledi", "Molefe", "Francistown", "74182056", false, Account.Type.INVESTMENT),
            new SampleCustomer("Kabelo", "Molefe", "Maun", "72649138", true, Account.Type.CHEQUE),
            new SampleCustomer("Neo", "Ditswhene", "Gaborone", "71378542", false, Account.Type.SAVINGS),
            new SampleCustomer("Thato", "Dube", "Mochudi", "75829041", false, Account.Type.INVESTMENT),
            new SampleCustomer("Lesedi", "Moyo", "Selebi-Phikwe", "74361789", true, Account.Type.CHEQUE),
            new SampleCustomer("Mpho", "Segokgo", "Francistown", "77104563", false, Account.Type.SAVINGS),
            new SampleCustomer("Kabo", "Tau", "Lobatse", "72398416", false, Account.Type.INVESTMENT),
            new SampleCustomer("Boitumelo", "Kgosidintsi", "Palapye", "76502381", true, Account.Type.CHEQUE),
            new SampleCustomer("Amantle", "Kgosi", "Serowe", "73465820", false, Account.Type.SAVINGS)
    );

    private DemoDataSeeder() { }

    public static void seed(CustomerDAO customers, UserDAO users, AccountDAO accounts) throws SQLException {
        List<User> existingUsers = users.list();
        if (existingUsers.stream().noneMatch(user -> user.role() == User.Role.ADMIN)) {
            users.create("Admin User", DEMO_PASSWORD, User.Role.ADMIN, null);
        }
        User teller = existingUsers.stream().filter(user -> user.role() == User.Role.TELLER).findFirst().orElse(null);
        if (teller == null) users.create(TELLER_USERNAME, DEMO_PASSWORD, User.Role.TELLER, null);
        else if (!teller.username().equalsIgnoreCase(TELLER_USERNAME)) users.rename(teller.id(), TELLER_USERNAME);

        for (int i = 0; i < CUSTOMERS.size(); i++) {
            SampleCustomer sample = CUSTOMERS.get(i);
            String username = sample.firstName + " " + sample.lastName;
            User existingLogin = existingUsers.stream()
                    .filter(user -> user.username().equalsIgnoreCase(username)).findFirst().orElse(null);
            long customerId;
            if (existingLogin != null && existingLogin.customerId() != null) {
                customerId = existingLogin.customerId();
            } else {
                Customer profile = customers.search(username).stream()
                        .filter(candidate -> candidate.fullName().equalsIgnoreCase(username)).findFirst().orElse(null);
                customerId = profile == null
                        ? customers.create(new Customer(0, sample.firstName, sample.lastName,
                        sample.city + ", Botswana", sample.phone, sample.employed))
                        : profile.id();
                if (existingLogin == null) users.create(username, DEMO_PASSWORD, User.Role.CUSTOMER, customerId);
            }

            Customer owner = customers.find(customerId);
            if (owner != null && (owner.employed() != sample.employed || !owner.phone().equals(sample.phone))) {
                customers.update(new Customer(owner.id(), owner.firstName(), owner.lastName(), owner.address(),
                        sample.phone, sample.employed));
            }

            String oldNumber = String.format("SAV-DEMO-%03d", i + 1);
            String accountNumber = accountNumber(sample.type, i + 1);
            boolean seededAccount = false;
            if (accounts.find(oldNumber) != null) {
                String employer = sample.type == Account.Type.CHEQUE ? "Botswana Power Corporation" : null;
                String employerAddress = sample.type == Account.Type.CHEQUE ? "Gaborone, Botswana" : null;
                accounts.updateSeededAccount(oldNumber, accountNumber, sample.type, employer, employerAddress);
                seededAccount = true;
            } else if (accounts.find(accountNumber) != null) {
                seededAccount = true;
            } else if (accounts.list(customerId).isEmpty()) {
                BigDecimal opening = BigDecimal.valueOf(850 + i * 275L, 0);
                String employer = sample.type == Account.Type.CHEQUE ? "Botswana Power Corporation" : null;
                String employerAddress = sample.type == Account.Type.CHEQUE ? "Gaborone, Botswana" : null;
                Account account = switch (sample.type) {
                    case SAVINGS -> new SavingsAccount(accountNumber, customerId, BRANCH, opening);
                    case INVESTMENT -> new InvestmentAccount(accountNumber, customerId, BRANCH, opening);
                    case CHEQUE -> new ChequeAccount(accountNumber, customerId, BRANCH, opening, employer, employerAddress);
                };
                accounts.create(account, employer, employerAddress);
                BigDecimal deposit = BigDecimal.valueOf(100 + i * 25L, 0);
                BigDecimal previous = account.balance();
                account.deposit(deposit);
                accounts.recordTransaction(account, previous, "DEPOSIT", deposit, "Counter deposit");
                seededAccount = true;
            }
            if (seededAccount) accounts.staggerSampleTransactionTimes(accountNumber, i + 1);
        }
    }

    private static String accountNumber(Account.Type type, int sequence) {
        String prefix = switch (type) { case SAVINGS -> "SAV"; case INVESTMENT -> "INV"; case CHEQUE -> "CHQ"; };
        return String.format("%s-%04d", prefix, 1000 + sequence);
    }

    private record SampleCustomer(String firstName, String lastName, String city, String phone,
                                  boolean employed, Account.Type type) { }
}
