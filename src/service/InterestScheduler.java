package service;

import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class InterestScheduler implements AutoCloseable {
    private static final ZoneId BANK_ZONE=ZoneId.of("Africa/Gaborone");
    private static final Logger LOGGER=Logger.getLogger(InterestScheduler.class.getName());
    private final MonthlyInterestService interest;
    private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(r->{
        Thread thread=new Thread(r,"monthly-interest");thread.setDaemon(true);return thread;
    });
    public InterestScheduler(MonthlyInterestService interest){this.interest=interest;}

    public void start(){
        ZonedDateTime now=ZonedDateTime.now(BANK_ZONE);
        ZonedDateTime next=now.toLocalDate().plusDays(1).atTime(0,5).atZone(BANK_ZONE);
        long initialDelay=Duration.between(now,next).getSeconds();
        executor.execute(this::applyLatestCompletedMonth);
        executor.scheduleAtFixedRate(this::applyLatestCompletedMonth,initialDelay,TimeUnit.DAYS.toSeconds(1),TimeUnit.SECONDS);
    }

    private void applyLatestCompletedMonth(){
        try{interest.apply(YearMonth.now(BANK_ZONE).minusMonths(1));}
        catch(Exception ex){LOGGER.log(Level.WARNING,"Monthly interest could not be applied; it will be retried.",ex);}
    }
    @Override public void close(){executor.shutdownNow();}
}
