-- =====================================================
-- 业务幂等性记录表
-- 用于持久化存储业务操作的幂等性状态，防止重复执行
-- =====================================================

CREATE TABLE IF NOT EXISTS `biz_idempotent` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `biz_no` VARCHAR(64) NOT NULL COMMENT '业务单号（订单号/补货单号等）',
    `op_type` VARCHAR(20) NOT NULL COMMENT '操作类型: deduct=扣减, add=增加, cancel=取消, replenish=补货',
    `platform_id` VARCHAR(50) NOT NULL COMMENT '平台标识',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0=处理中, 1=成功, 2=失败',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_biz_op_platform` (`biz_no`, `op_type`, `platform_id`) COMMENT '联合唯一索引，防止重复操作',
    KEY `idx_biz_no` (`biz_no`) COMMENT '业务单号索引，加速查询',
    KEY `idx_create_time` (`create_time`) COMMENT '创建时间索引，便于清理过期数据'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务幂等性记录表';

-- 说明：
-- 1. 该表用于解决Redis TTL幂等失效导致的数据不一致问题
-- 2. 联合唯一索引确保同一业务单号+操作类型+平台只能成功执行一次
-- 3. 状态字段支持追踪操作进度（处理中/成功/失败）
-- 4. 可定期清理历史数据（如保留90天）
