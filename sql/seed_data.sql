-- ============================================================
-- seed_data.sql —— 演示用种子数据
--
-- 用途：让项目启动后即有一个可用的管理员账号，
--       无需手动注册即可走通「补货→下单→取消」完整流程
--
-- 前置条件：已执行 sqltest.sql（建表）
-- 幂等性：使用 INSERT IGNORE，可安全重复执行
-- ============================================================

-- ============================================================
-- 1. 角色数据（5 角色体系）
-- ============================================================
INSERT IGNORE INTO role (id, role_name, description) VALUES
(1, 'BUYER',         '购买用户 —— 下单购买，自注册'),
(2, 'REPLENISHER',   '补货员 —— 提交补货申请'),
(3, 'HR',            '人事 —— 创建/管理员工账号'),
(4, 'SUPERVISOR',    '监管 —— 审核补货单/新用户'),
(5, 'SYSTEM_ADMIN',  '系统管理员 —— 全部权限');

-- ============================================================
-- 2. 平台数据
-- ============================================================
INSERT IGNORE INTO platform (id, name) VALUES
(1, 'PLATFORM_A');

-- ============================================================
-- 3. 系统管理员（staff 表）
--
-- 账号: id=1, 密码: admin123
-- BCrypt密文生成方式:
--   new BCryptPasswordEncoder(10).encode("admin123")
--
-- 如果密码不匹配，请用项目中的 PasswordUtil 重新生成密文替换
-- 或运行: personal/GenerateBcryptHash.java
-- ============================================================
INSERT IGNORE INTO staff (
    id, name, password, status, role_id,
    creator_id, approver_id, created_at, updated_at, approve_time
) VALUES (
    1,
    'admin',
    '$2a$10$wM32KdomYww4Dy0wwwv7Be9w54pAGvr7iUe.mB27J1clPVD0BNhmG',
    1,  -- status=1 已启用
    5,  -- role_id=5 系统管理员
    1,  -- creator_id=1 自创建
    1,  -- approver_id=1 自审核
    NOW(),
    NOW(),
    NOW()
);

-- ============================================================
-- 4. 购买用户（customer 表）-- 可选，用于测试下单权限
--
-- 账号: id=100, 密码: buyer123
-- ============================================================
INSERT IGNORE INTO customer (
    id, name, password, status, role_id, create_time, update_time
) VALUES (
    100,
    'testBuyer',
    '$2a$10$8Grd5d2OHymerNZRmghE7.RFbosD33KHJTU4DRyDE1pQB/zczdBRW',
    1,  -- status=1 已启用
    1,  -- role_id=1 购买用户
    NOW(),
    NOW()
);

SELECT 'seed_data.sql 执行完成' AS result;
SELECT '管理员账号: id=1, 密码=admin123（需先替换BCrypt密文）' AS notice;
SELECT '运行 personal/GenerateBcryptHash.java 生成密文后替换上方两个 $2a$10$PLACEHOLDER 占位符' AS instruction;
