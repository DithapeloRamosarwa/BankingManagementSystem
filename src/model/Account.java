package model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public abstract class Account {
    private final String accountNumber;
    private final long customerId;
    private final String branch;
    private BigDecimal balance;

    protected Account(String accountNumber, long customerId, String branch, BigDecimal balance) {
        this.accountNumber = accountNumber;
        this.customerId = customerId;
        this.branch = branch;
        this.balance = money(balance);
        if (this.balance.signum() < 0) throw new IllegalArgumentException("Balance cannot be negative.");
    }

    public final String accountNumber() { return accountNumber; }
    public final long customerId() { return customerId; }
    public final String branch() { return branch; }
    public final BigDecimal balance() { return balance; }
    @Override public final String toString() { return type()+" · "+accountNumber+" · BWP "+balance.setScale(2).toPlainString(); }
    public abstract Type type();
    public abstract BigDecimal monthlyInterest();
    public boolean withdrawalsAllowed() { return true; }

    public final BigDecimal deposit(BigDecimal amount) {
        BigDecimal value = positive(amount);
        balance = balance.add(value);
        return balance;
    }

    public BigDecimal withdraw(BigDecimal amount) {
        if (!withdrawalsAllowed()) throw new IllegalArgumentException("Withdrawals are not allowed from a Savings Account.");
        BigDecimal value = positive(amount);
        if (balance.compareTo(value) < 0) throw new IllegalArgumentException("Insufficient funds.");
        balance = balance.subtract(value);
        return balance;
    }

    public final BigDecimal accrueMonthlyInterest() {
        BigDecimal interest = balance.multiply(monthlyInterest()).setScale(2, RoundingMode.HALF_UP);
        if (interest.signum() > 0) balance = balance.add(interest);
        return interest;
    }

    protected static BigDecimal positive(BigDecimal amount) {
        BigDecimal value = money(amount);
        if (value.signum() <= 0) throw new IllegalArgumentException("Amount must be greater than zero.");
        return value;
    }

    protected static BigDecimal money(BigDecimal amount) {
        if (amount == null) throw new IllegalArgumentException("Amount is required.");
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    public enum Type { SAVINGS, INVESTMENT, CHEQUE }
}
