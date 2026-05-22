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

 Date: 23/04/2026 11:05:09
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for biz_idempotent
-- ----------------------------
DROP TABLE IF EXISTS `biz_idempotent`;
CREATE TABLE `biz_idempotent`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `biz_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '业务单号（订单号/补货单号等）',
  `op_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '操作类型: deduct=扣减, add=增加, cancel=取消, replenish=补货',
  `platform_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '平台标识',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态: 0=处理中, 1=成功, 2=失败',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_biz_op_platform`(`biz_no` ASC, `op_type` ASC, `platform_id` ASC) USING BTREE COMMENT '联合唯一索引，防止重复操作',
  INDEX `idx_biz_no`(`biz_no` ASC) USING BTREE COMMENT '业务单号索引，加速查询',
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE COMMENT '创建时间索引，便于清理过期数据'
) ENGINE = InnoDB AUTO_INCREMENT = 14590 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '业务幂等性记录表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
