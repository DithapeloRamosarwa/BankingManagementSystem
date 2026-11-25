package dao;

import model.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public final class AccountDAO {
    private final Database database;
    public AccountDAO(Database database) { this.database = database; }

    public void create(Account account, String employer, String employerAddress) throws SQLException {
        String sql = "INSERT INTO accounts(account_number,customer_id,type,branch,balance_cents,employer_name,employer_address,opened_at) VALUES(?,?,?,?,?,?,?,?)";
        try (var c = database.connect()) {
            c.setAutoCommit(false);
            try (var p = c.prepareStatement(sql); var t = c.prepareStatement(
                    "INSERT INTO transactions(account_number,type,amount_cents,balance_after_cents,note,occurred_at) VALUES(?,'OPEN',?,?,?,?)")) {
                p.setString(1, account.accountNumber()); p.setLong(2, account.customerId()); p.setString(3, account.type().name()); p.setString(4, account.branch());
                p.setLong(5, toCents(account.balance())); p.setString(6, account.type() == Account.Type.CHEQUE ? clean(employer) : null);
                p.setString(7, account.type() == Account.Type.CHEQUE ? clean(employerAddress) : null); p.setString(8, Instant.now().toString()); p.executeUpdate();
                t.setString(1, account.accountNumber()); t.setLong(2, toCents(account.balance())); t.setLong(3, toCents(account.balance()));
                t.setString(4, "Account opened"); t.setString(5, Instant.now().toString()); t.executeUpdate();
                c.commit();
            } catch (Exception ex) { c.rollback(); if (ex instanceof SQLException sqlEx) throw sqlEx; if (ex instanceof RuntimeException re) throw re; throw new SQLException(ex); }
        }
    }

    public List<Account> list(Long customerId) throws SQLException {
        String sql = "SELECT * FROM accounts" + (customerId == null ? "" : " WHERE customer_id=?") + " ORDER BY account_number";
        try (var c = database.connect(); var p = c.prepareStatement(sql)) {
            if (customerId != null) p.setLong(1, customerId);
            try (var r = p.executeQuery()) { List<Account> result = new ArrayList<>(); while(r.next()) result.add(read(r)); return result; }
        }
    }

    public Account find(String number) throws SQLException {
        try (var c=database.connect(); var p=c.prepareStatement("SELECT * FROM accounts WHERE account_number=?")) {
            p.setString(1, number); try(var r=p.executeQuery()){return r.next()?read(r):null;}
        }
    }

    /** Renames and changes the product for a seeded account while preserving its full ledger. */
    public void updateSeededAccount(String oldNumber, String newNumber, Account.Type type,
                                    String employer, String employerAddress) throws SQLException {
        try (Connection c = database.connect()) {
            c.setAutoCommit(false);
            try {
                try (var q = c.createStatement()) { q.execute("PRAGMA defer_foreign_keys = ON"); }
                try (var p = c.prepareStatement("UPDATE accounts SET account_number=?,type=?,employer_name=?,employer_address=? WHERE account_number=?")) {
                    p.setString(1, newNumber); p.setString(2, type.name());
                    p.setString(3, type == Account.Type.CHEQUE ? clean(employer) : null);
                    p.setString(4, type == Account.Type.CHEQUE ? clean(employerAddress) : null);
                    p.setString(5, oldNumber);
                    if (p.executeUpdate() != 1) throw new IllegalArgumentException("Seed account not found: " + oldNumber);
                }
                try (var p = c.prepareStatement("UPDATE transactions SET account_number=?,note=CASE WHEN note='Sample counter deposit' THEN 'Counter deposit' ELSE note END WHERE account_number=?")) {
                    p.setString(1, newNumber); p.setString(2, oldNumber); p.executeUpdate();
                }
                try (var p = c.prepareStatement("UPDATE interest_runs SET account_number=? WHERE account_number=?")) {
                    p.setString(1, newNumber); p.setString(2, oldNumber); p.executeUpdate();
                }
                c.commit();
            } catch (Exception ex) {
                c.rollback();
                if (ex instanceof SQLException sqlEx) throw sqlEx;
                if (ex instanceof RuntimeException runtimeEx) throw runtimeEx;
                throw new SQLException(ex);
            }
        }
    }

    /** Spreads the sample ledger timestamps out so the starter activity is readable in the UI. */
    public void staggerSampleTransactionTimes(String accountNumber, int sequence) throws SQLException {
        try (Connection c = database.connect()) {
            c.setAutoCommit(false);
            try {
                Instant openedAt;
                try (var p = c.prepareStatement("SELECT opened_at FROM accounts WHERE account_number=?")) {
                    p.setString(1, accountNumber);
                    try (var r = p.executeQuery()) {
                        if (!r.next()) throw new IllegalArgumentException("Sample account not found: " + accountNumber);
                        openedAt = Instant.parse(r.getString(1));
                    }
                }
                Instant openedTime = openedAt.minus(Duration.ofDays(20L - sequence));
                Instant depositTime = openedTime.plus(Duration.ofHours(2)).plus(Duration.ofMinutes(sequence * 3L));
                try (var p = c.prepareStatement("UPDATE transactions SET occurred_at=? WHERE account_number=? AND type='OPEN'")) {
                    p.setString(1, openedTime.toString()); p.setString(2, accountNumber); p.executeUpdate();
                }
                try (var p = c.prepareStatement("UPDATE transactions SET occurred_at=?,note='Counter deposit' WHERE account_number=? AND type='DEPOSIT' AND note IN ('Counter deposit','Sample counter deposit')")) {
                    p.setString(1, depositTime.toString()); p.setString(2, accountNumber); p.executeUpdate();
                }
                try (var p = c.prepareStatement("SELECT transaction_id,note FROM transactions WHERE account_number=? AND type='INTEREST' ORDER BY transaction_id")) {
                    p.setString(1, accountNumber);
                    try (var r = p.executeQuery()) {
                        int ordinal = 0;
                        while (r.next()) {
                            String note = r.getString("note");
                            String prefix = "Monthly interest for ";
                            if (note != null && note.startsWith(prefix)) {
                                try {
                                    YearMonth interestMonth = YearMonth.parse(note.substring(prefix.length()));
                                    YearMonth creditedMonth = interestMonth.plusMonths(1);
                                    int day = Math.min(creditedMonth.lengthOfMonth(), sequence + 1);
                                    LocalTime time = LocalTime.of(9, 0).plusMinutes(sequence * 4L + ordinal++);
                                    Instant creditedAt = LocalDateTime.of(LocalDate.of(creditedMonth.getYear(), creditedMonth.getMonth(), day), time)
                                            .atZone(ZoneId.of("Africa/Gaborone")).toInstant();
                                    try (var update = c.prepareStatement("UPDATE transactions SET occurred_at=? WHERE transaction_id=?")) {
                                        update.setString(1, creditedAt.toString()); update.setLong(2, r.getLong("transaction_id")); update.executeUpdate();
                                    }
                                } catch (java.time.DateTimeException ignored) { /* Leave nonstandard history timestamps intact. */ }
                            }
                        }
                    }
                }
                c.commit();
            } catch (Exception ex) {
                c.rollback();
                if (ex instanceof SQLException sqlEx) throw sqlEx;
                if (ex instanceof RuntimeException runtimeEx) throw runtimeEx;
                throw new SQLException(ex);
            }
        }
    }

    public void recordTransaction(Account account, BigDecimal previousBalance, String operation,
                                  BigDecimal amount, String note) throws SQLException {
        BigDecimal value = cents(amount);
        String kind = operation.toUpperCase();
        String entryType = kind.equals("DEPOSIT") ? "DEPOSIT" : "WITHDRAWAL";
        try (Connection c=database.connect()) {
            c.setAutoCommit(false);
            try {
                try(var p=c.prepareStatement("UPDATE accounts SET balance_cents=? WHERE account_number=? AND balance_cents=?")) {
                    p.setLong(1,toCents(account.balance()));p.setString(2,account.accountNumber());p.setLong(3,toCents(previousBalance));
                    if(p.executeUpdate()!=1)throw new SQLException("The account changed during this request. Refresh and try again.");
                }
                try(var p=c.prepareStatement("INSERT INTO transactions(account_number,type,amount_cents,balance_after_cents,note,occurred_at) VALUES(?,?,?,?,?,?)")) {
                    p.setString(1,account.accountNumber());p.setString(2,entryType);p.setLong(3,toCents(value));p.setLong(4,toCents(account.balance()));p.setString(5,clean(note));p.setString(6,Instant.now().toString());p.executeUpdate();
                }
                c.commit();
            } catch(Exception ex){c.rollback();if(ex instanceof SQLException se)throw se;if(ex instanceof RuntimeException re)throw re;throw new SQLException(ex);}
        }
    }

    public List<Transaction> history(String number) throws SQLException {
        try(var c=database.connect();var p=c.prepareStatement("SELECT * FROM transactions WHERE account_number=? ORDER BY transaction_id DESC LIMIT 100")){
            p.setString(1,number);try(var r=p.executeQuery()){List<Transaction> rows=new ArrayList<>();while(r.next())rows.add(new Transaction(
                    r.getLong("transaction_id"),r.getString("account_number"),Transaction.Type.valueOf(r.getString("type")),
                    fromCents(r.getLong("amount_cents")),fromCents(r.getLong("balance_after_cents")),r.getString("note"),Instant.parse(r.getString("occurred_at"))));return rows;}
        }
    }

    public boolean recordInterest(Account account, BigDecimal previousBalance, BigDecimal interest, YearMonth month) throws SQLException {
        try(Connection c=database.connect()){
            c.setAutoCommit(false);
            try{
                try(var p=c.prepareStatement("INSERT OR IGNORE INTO interest_runs(account_number,interest_month) VALUES(?,?)")){
                    p.setString(1,account.accountNumber());p.setString(2,month.toString());
                    if(p.executeUpdate()==0){c.rollback();return false;}
                }
                try(var p=c.prepareStatement("UPDATE accounts SET balance_cents=? WHERE account_number=? AND balance_cents=?")){
                    p.setLong(1,toCents(account.balance()));p.setString(2,account.accountNumber());p.setLong(3,toCents(previousBalance));
                    if(p.executeUpdate()!=1)throw new SQLException("The account changed while interest was being calculated.");
                }
                try(var p=c.prepareStatement("INSERT INTO transactions(account_number,type,amount_cents,balance_after_cents,note,occurred_at) VALUES(?,'INTEREST',?,?,?,?)")){
                    p.setString(1,account.accountNumber());p.setLong(2,toCents(interest));p.setLong(3,toCents(account.balance()));
                    p.setString(4,"Monthly interest for "+month);p.setString(5,Instant.now().toString());p.executeUpdate();
                }
                c.commit();return true;
            }catch(Exception ex){c.rollback();if(ex instanceof SQLException se)throw se;if(ex instanceof RuntimeException re)throw re;throw new SQLException(ex);}
        }
    }

    private static Account read(java.sql.ResultSet r) throws SQLException {
        String number=r.getString("account_number"); long owner=r.getLong("customer_id"); String branch=r.getString("branch");
        BigDecimal balance=fromCents(r.getLong("balance_cents"));
        return switch(Account.Type.valueOf(r.getString("type"))){
            case SAVINGS -> new SavingsAccount(number,owner,branch,balance);
            case INVESTMENT -> new InvestmentAccount(number,owner,branch,balance);
            case CHEQUE -> new ChequeAccount(number,owner,branch,balance,r.getString("employer_name"),r.getString("employer_address"));
        };
    }
    private static BigDecimal cents(BigDecimal value){if(value==null)throw new IllegalArgumentException("Amount is required.");return value.setScale(2,java.math.RoundingMode.UNNECESSARY);}
    private static long toCents(BigDecimal value){return value.movePointRight(2).longValueExact();}
    private static BigDecimal fromCents(long value){return BigDecimal.valueOf(value,2);}
    private static String clean(String value){return value==null?"":value.strip();}
}
