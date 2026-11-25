package model;

import java.math.BigDecimal;

public final class ChequeAccount extends Account {
    private final String employerName;
    private final String employerAddress;
    public ChequeAccount(String number, long customerId, String branch, BigDecimal balance,
                         String employerName, String employerAddress) {
        super(number, customerId, branch, balance);
        this.employerName = employerName == null ? "" : employerName.strip();
        this.employerAddress = employerAddress == null ? "" : employerAddress.strip();
        if (this.employerName.isBlank() || this.employerAddress.isBlank()) {
            throw new IllegalArgumentException("Employer name and address are required for a Cheque Account.");
        }
    }
    public String employerName() { return employerName; }
    public String employerAddress() { return employerAddress; }
    @Override public Type type() { return Type.CHEQUE; }
    @Override public BigDecimal monthlyInterest() { return BigDecimal.ZERO; }
}
