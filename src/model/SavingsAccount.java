package model;

import java.math.BigDecimal;

public final class SavingsAccount extends Account {
    public static final BigDecimal MONTHLY_RATE = new BigDecimal("0.0005");
    public SavingsAccount(String number, long customerId, String branch, BigDecimal balance) {
        super(number, customerId, branch, balance);
    }
    @Override public Type type() { return Type.SAVINGS; }
    @Override public BigDecimal monthlyInterest() { return MONTHLY_RATE; }
    @Override public boolean withdrawalsAllowed() { return false; }
}
