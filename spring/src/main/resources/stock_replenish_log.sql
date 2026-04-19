CREATE TABLE stock_replenish_log (
                                     id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
                                     replenish_no VARCHAR(64) NOT NULL COMMENT '补货单号',
                                     product_id BIGINT NOT NULL COMMENT '商品ID',
                                     quantity INT NOT NULL COMMENT '补货数量',
                                     stock_before INT COMMENT '补货前库存',
                                     stock_after INT COMMENT '补货后库存',
                                     status TINYINT DEFAULT 1 COMMENT '1-成功 0-已回滚',
                                     create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                                     UNIQUE KEY uk_replenish_product (replenish_no, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='补货审计日志';