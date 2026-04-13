-- =====================================================
-- 通用批量查询脚本
-- 批量获取多个key的当前值
--
-- 参数说明：
--   KEYS: 要查询的key列表（如 product:stock:1, product:stock:2）
--   ARGV:
--     [1] 业务单号（可选，用于日志追踪）
--
-- 返回值：[状态码, 消息, JSON格式的查询结果]
--   状态码: 1-成功
--   查询结果格式: {"product:stock:1": "100", "product:stock:2": "200"}
-- =====================================================

-- 提取业务单号（默认为unknown）
local biz_no = ARGV[1] or "unknown"

-- 构建结果表
local result = {}

-- 遍历所有key，获取对应的值
for i = 1, #KEYS do
    local value = redis.call('GET', KEYS[i])
    -- 将key-value存入结果表（value可能为nil）
    result[KEYS[i]] = value
end

-- 注意：已移除无意义的异步队列记录，避免浪费Redis资源

-- 返回查询结果（转换为JSON格式）
return {1, "success", cjson.encode(result)}