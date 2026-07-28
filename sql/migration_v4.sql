-- ============================================================
-- migration_v4.sql —— 补货单联表索引优化
-- 
-- 变更原因：
--   补货单审核流程需要联表查询 stock_replenish_order（头表）
--   与 stock_replenish_log（明细表），通过批次ID (id) 关联
--
-- 关于索引说明：
--   stock_replenish_log 的复合主键 (id, product_id) 已提供
--   最左前缀索引覆盖 id 字段，联表 JOIN 时无需额外建索引。
--   本脚本仅做显式索引创建（幂等，已存在则跳过）以确保部署一致性。
--
-- 执行前提：已执行 migration_v3.sql
-- 幂等性：使用存储过程检查，可安全重复执行
-- ============================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS idx_if_missing$$
CREATE PROCEDURE idx_if_missing(
    IN tbl_name  VARCHAR(128),
    IN idx_name  VARCHAR(128),
    IN idx_def   TEXT
)
BEGIN
    DECLARE cnt INT DEFAULT 0;
    SELECT COUNT(*) INTO cnt
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = tbl_name
      AND INDEX_NAME = idx_name;

    IF cnt = 0 THEN
        SET @sql = idx_def;
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

-- ============================================================
-- 1. stock_replenish_log：显式创建批次ID索引（联表 JOIN 优化）
--    说明：复合主键 (id, product_id) 的最左前缀已覆盖 id 字段，
--    但显式创建独立索引可确保查询优化器优先使用此索引
-- ============================================================
CALL idx_if_missing(
    'stock_replenish_log',
    'idx_replenish_batch_id',
    'ALTER TABLE stock_replenish_log ADD INDEX idx_replenish_batch_id (id)'
);

-- ============================================================
-- 2. stock_replenish_order：确保状态+创建时间联合索引（审核列表排序优化）
-- ============================================================
CALL idx_if_missing(
    'stock_replenish_order',
    'idx_status_create_time',
    'ALTER TABLE stock_replenish_order ADD INDEX idx_status_create_time (status, create_time)'
);

-- ============================================================
-- 3. 清理辅助存储过程
-- ============================================================
DROP PROCEDURE IF EXISTS idx_if_missing;

SELECT 'migration_v4.sql 执行完成：补货单联表索引已优化' AS result;
