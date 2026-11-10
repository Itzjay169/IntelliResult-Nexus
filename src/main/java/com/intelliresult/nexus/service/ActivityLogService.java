package com.intelliresult.nexus.service;

import com.intelliresult.nexus.config.HibernateUtil;
import com.intelliresult.nexus.dao.ActivityLogDAO;
import com.intelliresult.nexus.dao.ActivityLogDAOImpl;
import com.intelliresult.nexus.entity.ActivityLog;
import com.intelliresult.nexus.entity.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.Transaction;

/**
 * Service to reliably persist activity logs in their own transaction.
 * A logging failure must never break the request.
 */
public class ActivityLogService {

    private static final Logger LOGGER = LogManager.getLogger(ActivityLogService.class);
    private final ActivityLogDAO activityLogDAO = new ActivityLogDAOImpl();

    /**
     * Saves an activity log inside its own transaction.
     * If an error occurs, it is logged and swallowed so the calling request continues unaffected.
     */
    public void log(User user, String action, String details, String ipAddress) {
        log(new ActivityLog(user, action, details, ipAddress));
    }

    /**
     * Saves an activity log entity inside its own transaction.
     * If an error occurs, it is logged and swallowed so the calling request continues unaffected.
     */
    public void log(ActivityLog activityLog) {
        if (activityLog == null) {
            return;
        }
        try {
            Session session = HibernateUtil.getCurrentSession();
            Transaction tx = session.getTransaction();
            boolean openedTx = false;
            if (tx == null || !tx.isActive()) {
                tx = session.beginTransaction();
                openedTx = true;
            }
            try {
                activityLogDAO.save(activityLog);
                if (openedTx) {
                    tx.commit();
                }
            } catch (RuntimeException e) {
                if (openedTx && tx.isActive()) {
                    tx.rollback();
                }
                LOGGER.warn("Failed to persist activity log for action {}: {}", activityLog.getAction(), e.getMessage());
            }
        } catch (Exception e) {
            LOGGER.warn("Could not record activity log for action {}: {}", activityLog.getAction(), e.getMessage());
        }
    }
}
