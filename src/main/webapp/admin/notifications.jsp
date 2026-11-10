<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Notifications" scope="request"/>
<c:set var="pageSubtitle" value="Result publications, re-evaluation decisions, and notices addressed to you" scope="request"/>
<%@ include file="/common/fragments/admin-head.jspf" %>

<div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:var(--space-4);">
    <div>
        <c:if test="${not empty unreadNotificationCount and unreadNotificationCount > 0}">
            <span class="badge badge-warning" style="font-size:0.85rem; padding:0.35rem 0.75rem;">
                ${unreadNotificationCount} unread notification<c:if test="${unreadNotificationCount > 1}">s</c:if>
            </span>
        </c:if>
    </div>
    <form method="post" action="${ctx}/admin/notifications/read-all">
        <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
        <button type="submit" class="btn-secondary" style="width:auto; padding:0.5rem 1.1rem;">Mark all as read</button>
    </form>
</div>

<div class="panel">
    <c:choose>
        <c:when test="${empty notifications}">
            <div class="panel-empty">
                <p>No notifications yet.</p>
            </div>
        </c:when>
        <c:otherwise>
            <c:forEach items="${notifications}" var="n">
                <div class="list-row" style="align-items:flex-start; justify-content:space-between; gap:var(--space-3); padding:var(--space-3) 0; border-bottom:1px solid var(--color-rule); ${n.read ? '' : 'background:rgba(184,146,90,0.06); padding:var(--space-3); border-radius:var(--radius-sm); margin-bottom:var(--space-2);'}">
                    <div style="flex:1;">
                        <div style="display:flex; align-items:center; gap:var(--space-2); margin-bottom:4px;">
                            <c:choose>
                                <c:when test="${!n.read}">
                                    <span class="badge badge-warning" style="font-size:0.7rem; padding:2px 6px;">UNREAD</span>
                                </c:when>
                                <c:otherwise>
                                    <span class="badge" style="font-size:0.7rem; padding:2px 6px; background:var(--color-paper-soft); color:var(--color-ink-soft);">READ</span>
                                </c:otherwise>
                            </c:choose>
                            <span style="font-weight:600; font-size:0.95rem; color:var(--color-ink);"><c:out value="${n.title}"/></span>
                        </div>
                        <div style="color:var(--color-ink-soft); font-size:0.88rem; line-height:1.45; margin-bottom:4px;"><c:out value="${n.message}"/></div>
                        <div style="color:var(--color-ink-muted); font-size:0.75rem;"><c:out value="${n.createdAt}"/></div>
                    </div>
                    <div style="display:flex; align-items:center; gap:var(--space-2); white-space:nowrap;">
                        <c:if test="${not empty n.linkUrl}">
                            <a href="${ctx}${n.linkUrl}" class="btn-secondary" style="width:auto; padding:0.35rem 0.85rem; font-size:0.8rem; text-decoration:none;">View &rarr;</a>
                        </c:if>
                        <c:if test="${!n.read}">
                            <form method="post" action="${ctx}/admin/notifications/read" style="margin:0;">
                                <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
                                <input type="hidden" name="notificationId" value="${n.id}">
                                <button type="submit" class="btn-secondary" style="width:auto; padding:0.35rem 0.85rem; font-size:0.8rem;">Mark read</button>
                            </form>
                        </c:if>
                    </div>
                </div>
            </c:forEach>
        </c:otherwise>
    </c:choose>
</div>

<%@ include file="/common/fragments/admin-foot.jspf" %>
