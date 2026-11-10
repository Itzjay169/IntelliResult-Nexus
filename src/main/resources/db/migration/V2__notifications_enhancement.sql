-- ----------------------------------------------------------------------------
-- Migration: V2__notifications_enhancement.sql
-- Idempotent schema upgrade for Notification system enhancement
-- ----------------------------------------------------------------------------

-- Add notification_type column if missing
SET @dbname = DATABASE();
SET @tablename = 'notifications';

SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = 'notification_type') > 0,
  'SELECT 1',
  'ALTER TABLE notifications ADD COLUMN notification_type VARCHAR(50) NOT NULL DEFAULT ''GENERAL'''
));
PREPARE alterStmt FROM @preparedStatement;
EXECUTE alterStmt;
DEALLOCATE PREPARE alterStmt;

-- Add related_entity_type column if missing
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = 'related_entity_type') > 0,
  'SELECT 1',
  'ALTER TABLE notifications ADD COLUMN related_entity_type VARCHAR(50) NULL'
));
PREPARE alterStmt FROM @preparedStatement;
EXECUTE alterStmt;
DEALLOCATE PREPARE alterStmt;

-- Add related_entity_id column if missing
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = 'related_entity_id') > 0,
  'SELECT 1',
  'ALTER TABLE notifications ADD COLUMN related_entity_id BIGINT UNSIGNED NULL'
));
PREPARE alterStmt FROM @preparedStatement;
EXECUTE alterStmt;
DEALLOCATE PREPARE alterStmt;

-- Add link_url column if missing
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = 'link_url') > 0,
  'SELECT 1',
  'ALTER TABLE notifications ADD COLUMN link_url VARCHAR(255) NULL'
));
PREPARE alterStmt FROM @preparedStatement;
EXECUTE alterStmt;
DEALLOCATE PREPARE alterStmt;

-- Add composite index on (user_id, is_read, created_at) if missing
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND INDEX_NAME = 'idx_notifications_user_unread_created') > 0,
  'SELECT 1',
  'CREATE INDEX idx_notifications_user_unread_created ON notifications (user_id, is_read, created_at)'
));
PREPARE alterStmt FROM @preparedStatement;
EXECUTE alterStmt;
DEALLOCATE PREPARE alterStmt;

-- Add index on (related_entity_type, related_entity_id) if missing
SET @preparedStatement = (SELECT IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND INDEX_NAME = 'idx_notifications_related') > 0,
  'SELECT 1',
  'CREATE INDEX idx_notifications_related ON notifications (related_entity_type, related_entity_id)'
));
PREPARE alterStmt FROM @preparedStatement;
EXECUTE alterStmt;
DEALLOCATE PREPARE alterStmt;

