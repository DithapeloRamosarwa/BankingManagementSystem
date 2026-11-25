package model;

import java.math.BigDecimal;
import java.time.Instant;

public record Transaction(long id, String accountNumber, Type type, BigDecimal amount,
                          BigDecimal balanceAfter, String note, Instant occurredAt) {
    public enum Type { OPEN, DEPOSIT, WITHDRAWAL, INTEREST }
}
