SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
                        `id` int NOT NULL,                    -- 由用户设定
                        `name` varchar(255) NOT NULL,
                        `password` varchar(255) NOT NULL,     -- 密码字段，不可为空
                        `money` int NOT NULL,
                        PRIMARY KEY (`id`)                    -- 只有主键，没有唯一约束
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 插入示例数据
INSERT INTO `user` VALUES (1, 'zs', '123456', 100);
INSERT INTO `user` VALUES (2, 'zl', '123456', 100);

SET FOREIGN_KEY_CHECKS = 1;