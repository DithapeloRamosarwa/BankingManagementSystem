package controller;

import dao.AccountDAO;
import dao.CustomerDAO;
import dao.UserDAO;
import model.*;
import service.MonthlyInterestService;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public final class BankingController {
    private final LoginController login;
    private final UserDAO users;
    private final CustomerDAO customers;
    private final AccountDAO accounts;
    private final MonthlyInterestService interest;

    public BankingController(LoginController login, UserDAO users, CustomerDAO customers, AccountDAO accounts) {
        this(login,users,customers,accounts,new MonthlyInterestService(accounts));
    }
    public BankingController(LoginController login, UserDAO users, CustomerDAO customers, AccountDAO accounts,
                             MonthlyInterestService interest) {
        this.login = login; this.users = users; this.customers = customers; this.accounts = accounts; this.interest=interest;
    }

    public List<Customer> searchCustomers(String query) throws SQLException {
        requireStaff(); return customers.search(query);
    }
    public long registerCustomer(String first, String last, String address, String phone, boolean employed) throws SQLException {
        requireStaff(); return customers.create(new Customer(0,first,last,address,phone,employed));
    }
    public long createLogin(String username, String password, User.Role role, Long customerId) throws SQLException {
        requireRole(User.Role.ADMIN);
        if(username==null||username.isBlank()||username.strip().length()>60)throw new IllegalArgumentException("Enter a username of 1 to 60 characters.");
        if(password==null||password.length()<8)throw new IllegalArgumentException("Password must have at least 8 characters.");
        if(role==null)throw new IllegalArgumentException("Choose a login role.");
        if (role == User.Role.ADMIN && customerId != null) throw new IllegalArgumentException("Administrator logins do not belong to a customer.");
        if (role == User.Role.CUSTOMER && customers.find(customerId == null ? -1 : customerId) == null)
            throw new IllegalArgumentException("Select a saved customer for this login.");
        return users.create(username,password,role,customerId);
    }
    public List<User> listUsers() throws SQLException { requireRole(User.Role.ADMIN); return users.list(); }
    public void setUserActive(long userId,boolean active) throws SQLException {
        requireRole(User.Role.ADMIN);
        if(userId==login.currentUser().id()&&!active)throw new IllegalArgumentException("Sign in with another administrator before disabling this login.");
        users.setActive(userId,active);
    }
    public String openAccount(long ownerId, Account.Type type, String branch, BigDecimal opening,
                              String employer, String employerAddress) throws SQLException {
        User user=login.currentUser();
        if(user.role()==User.Role.CUSTOMER && !Long.valueOf(ownerId).equals(user.customerId()))
            throw new SecurityException("Customers can only open accounts for themselves.");
        Customer owner=customers.find(ownerId);
        if(owner==null)throw new IllegalArgumentException("Select an existing customer.");
        if(type==null)throw new IllegalArgumentException("Choose an account type.");
        if(branch==null||branch.isBlank())throw new IllegalArgumentException("Branch is required.");
        BigDecimal amount=money(opening);
        if(amount.signum()<0)throw new IllegalArgumentException("Opening deposit cannot be negative.");
        if(type==Account.Type.INVESTMENT&&amount.compareTo(InvestmentAccount.MINIMUM_OPENING_DEPOSIT)<0)
            throw new IllegalArgumentException("Investment Accounts require an opening deposit of at least BWP 500.00.");
        if(type==Account.Type.CHEQUE&&!owner.employed())throw new IllegalArgumentException("Cheque Accounts are only available to employed individual customers.");
        String number=switch(type){case SAVINGS->"SAV";case INVESTMENT->"INV";case CHEQUE->"CHQ";}+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase();
        Account account=switch(type){
            case SAVINGS->new SavingsAccount(number,ownerId,branch.strip(),amount);
            case INVESTMENT->new InvestmentAccount(number,ownerId,branch.strip(),amount);
            case CHEQUE->new ChequeAccount(number,ownerId,branch.strip(),amount,employer,employerAddress);
        };
        accounts.create(account,employer,employerAddress);
        return number;
    }
    public List<Customer> visibleCustomers() throws SQLException {
        User user=login.currentUser();
        if(user.role()==User.Role.CUSTOMER) return List.of(customers.find(user.customerId()));
        return customers.search("");
    }
    public List<Account> visibleAccounts() throws SQLException {
        User user=login.currentUser();
        return accounts.list(user.role()==User.Role.CUSTOMER?user.customerId():null);
    }
    public BigDecimal transact(String accountNumber, String operation, BigDecimal amount, String note) throws SQLException {
        Account account=assertAccountAccess(accountNumber);
        BigDecimal value=money(amount);
        if(value.signum()<=0)throw new IllegalArgumentException("Transaction amount must be greater than zero.");
        String kind=operation==null?"":operation.strip().toUpperCase();
        BigDecimal previous=account.balance();
        if(kind.equals("DEPOSIT"))account.deposit(value);
        else if(kind.equals("WITHDRAWAL"))account.withdraw(value);
        else throw new IllegalArgumentException("Choose Deposit or Withdrawal.");
        accounts.recordTransaction(account,previous,kind,value,note);
        return account.balance();
    }
    public List<Transaction> history(String accountNumber) throws SQLException {
        assertAccountAccess(accountNumber); return accounts.history(accountNumber);
    }
    public int runMonthlyInterest(YearMonth month) throws SQLException {
        requireRole(User.Role.ADMIN,User.Role.TELLER);
        if(month==null)throw new IllegalArgumentException("Choose an interest month.");
        return interest.apply(month);
    }

    private Account assertAccountAccess(String number) throws SQLException {
        Account account=accounts.find(number);
        if(account==null) throw new IllegalArgumentException("Account not found.");
        User user=login.currentUser();
        if(user.role()==User.Role.CUSTOMER && !Long.valueOf(account.customerId()).equals(user.customerId()))
            throw new SecurityException("Customers may only access their own accounts.");
        return account;
    }
    private void requireStaff() { requireRole(User.Role.TELLER,User.Role.ADMIN); }
    private void requireRole(User.Role... allowed) {
        User user=login.currentUser();
        for(User.Role role:allowed) if(user.role()==role)return;
        throw new SecurityException("Your account is not allowed to perform this action.");
    }
    private static BigDecimal money(BigDecimal value){
        if(value==null)throw new IllegalArgumentException("Enter an amount.");
        try{return value.setScale(2,java.math.RoundingMode.UNNECESSARY);}
        catch(ArithmeticException ex){throw new IllegalArgumentException("Use no more than two decimal places.");}
    }
}
