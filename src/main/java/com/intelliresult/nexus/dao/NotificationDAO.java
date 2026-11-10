package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.Notification;

import java.util.List;
import java.util.Optional;

public interface NotificationDAO extends GenericDAO<Notification, Long> {
    List<Notification> findByUser(Long userId);

    /** Feeds both the unread-count badge and the "unread notifications" list (Sec. 23). */
    List<Notification> findUnreadByUser(Long userId);

    /** The badge itself (Sec. 23) - a COUNT query, not findUnreadByUser(id).size(), so AuthenticationFilter's per-request check (every authenticated page needs this number) never has to hydrate the notifications themselves just to report how many there are. */
    long countUnreadByUser(Long userId);

    /** Checks whether a notification of this type has already been delivered to the recipient for the given entity. */
    boolean existsByRecipientAndEntity(Long userId, String notificationType, String relatedEntityType, Long relatedEntityId);

    /** Finds the latest notification of this type for the recipient and entity, if one exists. */
    Optional<Notification> findLatestByRecipientAndEntity(Long userId, String notificationType, String relatedEntityType, Long relatedEntityId);
}
