-- =====================================================
-- 取消订单库存恢复脚本
-- 功能：原子性地增加商品库存，带幂等性控制
--
-- 参数说明：
--   KEYS: 需要恢复库存的 Redis key 列表（如 product:stock:1）
--   ARGV:
--     [1..N]  对应每个 key 的增加数量
--     [N+1]   业务单号（bizNo / orderNo）
--     [N+2]   平台标识（platformId）
--     [N+3]   幂等标记超时时间（秒）
--
-- 返回值：[状态码, 消息, 业务单号]
--   状态码: 1-成功, 0-失败
-- =====================================================

local N = #KEYS
if N == 0 then
    return {0, "no_keys_provided"}
end

-- 提取固定参数
local biz_no      = ARGV[N + 1]
local platform_id = ARGV[N + 2]
local timeout     = tonumber(ARGV[N + 3])

-- 幂等性 key（独立于扣减/增加的幂等标记）
local idempotent_key = "cancel:idempotent:" .. biz_no

-- 1. 幂等性检查
local processed = redis.call('GET', idempotent_key)
if processed == 'success' then
    return {1, "already_success", biz_no}
elseif processed == 'failed' then
    return {0, "already_failed", biz_no}
end

-- 2. 标记为处理中
redis.call('SETEX', idempotent_key, timeout, 'processing')

-- 3. 执行库存恢复（原子增加）
for i = 1, N do
    local quantity = tonumber(ARGV[i])
    redis.call('INCRBY', KEYS[i], quantity)
end

-- 4. 标记成功
redis.call('SETEX', idempotent_key, timeout, 'success')

-- 5. 返回结果（不发送任何队列消息）
return {1, "success", biz_no}