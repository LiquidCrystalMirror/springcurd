-- ============================================================
-- migration_v3.sql —— 用户表拆分：customer(购买用户) + staff(内部员工)
-- 
-- 变更原因：
--   购买用户(role_id=1) 自注册、无审核、无审计字段
--   内部员工(role_id=2~5) 人事创建、监管审核、需审计链
--   两类实体差异太大，共用一张表导致字段污染
--
-- 执行前提：已执行 migration_v2.sql
-- 幂等性：使用存储过程检查，可安全重复执行
-- ============================================================

-- ============================================================
-- 辅助存储过程（幂等检查）
-- ============================================================
DELIMITER $$

DROP PROCEDURE IF EXISTS add_col_if_missing$$
CREATE PROCEDURE add_col_if_missing(
    IN tbl_name VARCHAR(128),
    IN col_name VARCHAR(128),
    IN col_def  TEXT
)
BEGIN
    DECLARE cnt INT DEFAULT 0;
    SELECT COUNT(*) INTO cnt
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = tbl_name
      AND COLUMN_NAME = col_name;

    IF cnt = 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', tbl_name, '` ADD COLUMN `', col_name, '` ', col_def);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS table_exists$$
CREATE PROCEDURE table_exists(
    IN  tbl_name VARCHAR(128),
    OUT tbl_cnt  INT
)
BEGIN
    SELECT COUNT(*) INTO tbl_cnt
    FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = tbl_name;
END$$

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
-- 1. 创建 customer 表（购买用户）
-- ============================================================
CALL table_exists('customer', @cnt);
SET @sql = IF(@cnt = 0,
    'CREATE TABLE `customer` (
        `id`         int         NOT NULL COMMENT ''用户ID'',
        `name`       varchar(255) NOT NULL COMMENT ''昵称'',
        `password`   varchar(255) NOT NULL COMMENT ''密码(加密)'',
        `status`     tinyint     NOT NULL DEFAULT 1 COMMENT ''0=禁用, 1=启用'',
        `role_id`    tinyint     NOT NULL DEFAULT 1 COMMENT ''固定为1(购买用户)'',
        `phone`      varchar(20)  DEFAULT NULL COMMENT ''手机号'',
        `address`    varchar(512) DEFAULT NULL COMMENT ''收货地址'',
        `create_time` datetime   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''注册时间'',
        `update_time` datetime   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'',
        PRIMARY KEY (`id`),
        INDEX `idx_status` (`status`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''购买用户表（C端消费者）''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 2. 创建 staff 表（内部员工：补货员/人事/监管/系统管理员）
-- ============================================================
CALL table_exists('staff', @cnt);
SET @sql = IF(@cnt = 0,
    'CREATE TABLE `staff` (
        `id`           int         NOT NULL COMMENT ''员工ID'',
        `name`         varchar(255) NOT NULL COMMENT ''姓名'',
        `password`     varchar(255) NOT NULL COMMENT ''密码(加密)'',
        `status`       tinyint     NOT NULL DEFAULT 2 COMMENT ''0=禁用, 1=启用(审核通过), 2=待审核, 3=审核拒绝'',
        `role_id`      tinyint     NOT NULL COMMENT ''角色：2=补货员, 3=人事, 4=监管, 5=系统管理员'',
        `creator_id`   int         DEFAULT NULL COMMENT ''创建人ID（人事）'',
        `approver_id`  int         DEFAULT NULL COMMENT ''审核人ID（监管/管理员）'',
        `created_at`   datetime   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'',
        `updated_at`   datetime   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'',
        `approve_time` datetime   DEFAULT NULL COMMENT ''审核时间'',
        PRIMARY KEY (`id`),
        INDEX `idx_role_id`    (`role_id`),
        INDEX `idx_status`     (`status`),
        INDEX `idx_creator_id` (`creator_id`),
        INDEX `idx_approver_id`(`approver_id`),
        CONSTRAINT `fk_staff_role` FOREIGN KEY (`role_id`) REFERENCES `role`(`id`)
            ON DELETE RESTRICT ON UPDATE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''内部员工表（补货员/人事/监管/管理员）''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 3. 数据迁移：从 user 表迁移到 customer / staff
--    （仅当 user 表仍存在且有数据时执行）
-- ============================================================
CALL table_exists('user', @user_exists);
CALL table_exists('customer', @customer_exists);
CALL table_exists('staff', @staff_exists);

-- 迁移购买用户 → customer
SET @sql = IF(@user_exists > 0 AND @customer_exists > 0,
    'INSERT IGNORE INTO `customer` (id, name, password, status, role_id, phone, address, create_time, update_time)
     SELECT id, name, password,
            CASE WHEN status IN (0,1) THEN status ELSE 1 END,
            1, NULL, NULL, NOW(), NOW()
     FROM `user`
     WHERE role_id = 1',
    'SELECT ''skip customer migration'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 迁移内部员工 → staff
SET @sql = IF(@user_exists > 0 AND @staff_exists > 0,
    'INSERT IGNORE INTO `staff` (id, name, password, status, role_id, creator_id, approver_id, created_at, updated_at, approve_time)
     SELECT id, name, password, status, role_id, creator_id, approver_id,
            COALESCE(created_at, NOW()), COALESCE(updated_at, NOW()), approve_time
     FROM `user`
     WHERE role_id IN (2,3,4,5)',
    'SELECT ''skip staff migration'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 安全备份：重命名旧表（不删除）
SET @sql = IF(@user_exists > 0,
    'RENAME TABLE `user` TO `user_backup_v3`',
    'SELECT ''user table already migrated or does not exist'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 4. 清理辅助存储过程
-- ============================================================
DROP PROCEDURE IF EXISTS add_col_if_missing;
DROP PROCEDURE IF EXISTS table_exists;
DROP PROCEDURE IF EXISTS idx_if_missing;

SELECT 'migration_v3.sql 执行完成：user → customer + staff' AS result;
