package com.hrragassistant.backend.hosting;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Daily caps on AI calls (0 = unlimited, the default for local use). On the
 * public server each person gets a few report analyses and questions per day,
 * and the whole site has a ceiling too, so a busy day or a script can't run up
 * the AI bill. The admin is never limited.
 */
@Component
public class UsageLimits {

    static final String ANALYSES = "analyses";
    static final String QUESTIONS = "questions";
    static final String IMPORTS = "imports";

    private final DailyQuota quota;
    private final int analysesPerUser;
    private final int analysesTotal;
    private final int questionsPerUser;
    private final int questionsTotal;
    private final int importsPerUser;

    /** Spring uses this one; the package-private constructor below is for tests. */
    @Autowired
    public UsageLimits(@Value("${app.limits.analyses-per-user-per-day:0}") int analysesPerUser,
                       @Value("${app.limits.analyses-per-day:0}") int analysesTotal,
                       @Value("${app.limits.questions-per-user-per-day:0}") int questionsPerUser,
                       @Value("${app.limits.questions-per-day:0}") int questionsTotal,
                       @Value("${app.limits.imports-per-user-per-day:0}") int importsPerUser,
                       @Value("${app.limits.zone:Europe/Amsterdam}") String zone) {
        this(new DailyQuota(Clock.system(ZoneId.of(zone))), analysesPerUser, analysesTotal, questionsPerUser, questionsTotal, importsPerUser);
    }

    UsageLimits(DailyQuota quota, int analysesPerUser, int analysesTotal, int questionsPerUser, int questionsTotal) {
        this(quota, analysesPerUser, analysesTotal, questionsPerUser, questionsTotal, 0);
    }

    UsageLimits(DailyQuota quota, int analysesPerUser, int analysesTotal, int questionsPerUser, int questionsTotal, int importsPerUser) {
        this.quota = quota;
        this.analysesPerUser = analysesPerUser;
        this.analysesTotal = analysesTotal;
        this.questionsPerUser = questionsPerUser;
        this.questionsTotal = questionsTotal;
        this.importsPerUser = importsPerUser;
    }

    /** Company imports download several large filings; each person gets a few per day (no AI cost involved). */
    public void acquireImport(String username, boolean admin) {
        if (admin) {
            return;
        }
        acquire(IMPORTS, username, importsPerUser, 0,
                "You've imported " + importsPerUser + " companies today, your daily limit. Companies others imported are always available.",
                "");
    }

    public void releaseImport(String username, boolean admin) {
        if (!admin) {
            release(IMPORTS, username, importsPerUser, 0);
        }
    }

    /** Takes one report analysis from this person's and the site's allowance, or throws LimitReached. */
    public void acquireAnalysis(String username, boolean admin) {
        if (admin) {
            return;
        }
        acquire(ANALYSES, username, analysesPerUser, analysesTotal,
                "You've used your " + analysesPerUser + " report analyses for today. Your allowance resets at midnight (Amsterdam time).",
                "Mentalist has reached today's limit of report analyses for everyone. Please try again tomorrow.");
    }

    /** Gives an analysis back when it didn't happen after all (e.g. the upload was refused). */
    public void releaseAnalysis(String username, boolean admin) {
        if (admin) {
            return;
        }
        release(ANALYSES, username, analysesPerUser, analysesTotal);
    }

    public void acquireQuestion(String username, boolean admin) {
        if (admin) {
            return;
        }
        acquire(QUESTIONS, username, questionsPerUser, questionsTotal,
                "You've asked " + questionsPerUser + " questions today, your daily limit. It resets at midnight (Amsterdam time).",
                "Mentalist has reached today's limit of questions for everyone. Please try again tomorrow.");
    }

    public Remaining remaining(String username, boolean admin) {
        if (admin) {
            return new Remaining(-1, -1, -1, -1);
        }
        return new Remaining(
                left(ANALYSES, username, analysesPerUser, analysesTotal), analysesPerUser,
                left(QUESTIONS, username, questionsPerUser, questionsTotal), questionsPerUser);
    }

    /** -1 means unlimited. "PerDay" is the person's own daily allowance. */
    public record Remaining(int analysesLeft, int analysesPerDay, int questionsLeft, int questionsPerDay) {
    }

    private void acquire(String kind, String username, int perUser, int total, String userMessage, String totalMessage) {
        String userKey = kind + ":" + username;
        if (!quota.tryAcquire(userKey, perUser)) {
            throw new LimitReached(userMessage, 0);
        }
        if (!quota.tryAcquire(kind, total)) {
            if (perUser > 0) {
                quota.release(userKey);
            }
            throw new LimitReached(totalMessage, 0);
        }
    }

    private void release(String kind, String username, int perUser, int total) {
        if (perUser > 0) {
            quota.release(kind + ":" + username);
        }
        if (total > 0) {
            quota.release(kind);
        }
    }

    private int left(String kind, String username, int perUser, int total) {
        int user = quota.remaining(kind + ":" + username, perUser);
        int site = quota.remaining(kind, total);
        return user < 0 ? site : site < 0 ? user : Math.min(user, site);
    }
}
