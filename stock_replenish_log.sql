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

 Date: 23/04/2026 11:05:44
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for stock_replenish_log
-- ----------------------------
DROP TABLE IF EXISTS `stock_replenish_log`;
CREATE TABLE `stock_replenish_log`  (
  `id` bigint NOT NULL COMMENT '补货批次ID（雪花算法，后端自动生成）',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `quantity` int NOT NULL COMMENT '补货数量',
  `stock_before` int NULL DEFAULT NULL COMMENT '补货前库存',
  `stock_after` int NULL DEFAULT NULL COMMENT '补货后库存',
  `status` tinyint NULL DEFAULT 1 COMMENT '1-成功 0-已回滚',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`, `product_id`) USING BTREE COMMENT '联合主键：批次ID+商品ID，天然防重'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '补货审计日志表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
