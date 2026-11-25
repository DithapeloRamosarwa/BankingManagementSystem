package model;

import java.math.BigDecimal;

public final class InvestmentAccount extends Account {
    public static final BigDecimal MONTHLY_RATE = new BigDecimal("0.05");
    public static final BigDecimal MINIMUM_OPENING_DEPOSIT = new BigDecimal("500.00");
    public InvestmentAccount(String number, long customerId, String branch, BigDecimal balance) {
        super(number, customerId, branch, balance);
    }
    @Override public Type type() { return Type.INVESTMENT; }
    @Override public BigDecimal monthlyInterest() { return MONTHLY_RATE; }
}
