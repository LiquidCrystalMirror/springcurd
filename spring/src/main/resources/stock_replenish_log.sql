-- =====================================================
-- 补货审计日志表（重构版）
-- 使用雪花算法ID作为批次号，天然唯一，无需前端传参
-- =====================================================

CREATE TABLE IF NOT EXISTS `stock_replenish_log` (
    `id` BIGINT NOT NULL COMMENT '补货批次ID（雪花算法，后端自动生成）',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `quantity` INT NOT NULL COMMENT '补货数量',
    `stock_before` INT COMMENT '补货前库存',
    `stock_after` INT COMMENT '补货后库存',
    `status` TINYINT DEFAULT 1 COMMENT '1-成功 0-已回滚',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`, `product_id`) COMMENT '联合主键：批次ID+商品ID，天然防重'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='补货审计日志表';

-- 说明：
-- 1. 主键使用雪花算法ID，保证分布式环境下全局唯一
-- 2. 联合主键 (id, product_id) 确保同一批次同一商品只能有一条记录
-- 3. 前端无需传入补货单号，后端自动生成并返回，彻底杜绝人为重复提交
-- 4. 保留新商品自动初始化逻辑，补货时若商品不存在会自动创建库存记录
