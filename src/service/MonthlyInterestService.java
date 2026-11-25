package service;

import dao.AccountDAO;
import model.Account;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.YearMonth;

public final class MonthlyInterestService {
    private final AccountDAO accounts;
    public MonthlyInterestService(AccountDAO accounts){this.accounts=accounts;}

    public int apply(YearMonth month)throws SQLException{
        int credited=0;
        for(Account account:accounts.list(null)){
            if(account.monthlyInterest().signum()==0)continue;
            BigDecimal previous=account.balance();
            BigDecimal interest=account.accrueMonthlyInterest();
            if(interest.signum()>0&&accounts.recordInterest(account,previous,interest,month))credited++;
        }
        return credited;
    }
}
