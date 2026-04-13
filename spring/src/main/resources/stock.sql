-- 商品库存表（MySQL备份，最终一致）
CREATE TABLE `product_stock` (
                                 `product_id` bigint NOT NULL COMMENT '商品ID（主键）',
                                 `stock` int NOT NULL COMMENT '当前库存（备份值）',
                                 `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（可选，用于并发更新控制）',
                                 `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                 PRIMARY KEY (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品库存备份表';

-- 订单明细表（一个订单的每个商品一条记录）
CREATE TABLE `order_detail` (
                                `id` bigint NOT NULL COMMENT '雪花算法主键',
                                `order_no` varchar(100) NOT NULL COMMENT '订单号（业务单号）',
                                `platform_id` varchar(50) NOT NULL COMMENT '平台标识',
                                `product_id` bigint NOT NULL COMMENT '商品ID',
                                `quantity` int NOT NULL COMMENT '购买数量',
                                `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-已取消，1-正常，2-已回滚',
                                `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                PRIMARY KEY (`id`),
                                UNIQUE KEY `uk_order_product` (`order_no`, `product_id`),
                                KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';