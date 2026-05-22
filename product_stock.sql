/*
 Navicat MySQL Dump SQL

 Source Server         : l1
 Source Server Type    : MySQL
 Source Server Version : 80044 (8.0.44)
 Source Host           : localhost:3306
 Source Schema         : sqltest

 Target Server Type    : MySQL
 Target Server Version : 80044 (8.0.44)
 File Encoding         : 65001

 Date: 23/04/2026 11:05:31
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for product_stock
-- ----------------------------
DROP TABLE IF EXISTS `product_stock`;
CREATE TABLE `product_stock`  (
  `product_id` bigint NOT NULL COMMENT '商品ID（主键）',
  `stock` int NOT NULL COMMENT '当前库存（备份值）',
  `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（可选，用于并发更新控制）',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`product_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商品库存备份表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
