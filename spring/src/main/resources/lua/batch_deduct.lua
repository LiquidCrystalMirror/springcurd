-- =====================================================
-- 通用批量库存操作脚本（修正版）
-- 支持批量扣减/增加，带幂等性和快照
--
-- 参数说明：
--   KEYS: 操作的目标key列表（库存key，如 product:stock:1）
--   ARGV:
--     [1..N] 对应每个key的操作值（扣减或增加的数量）
--     [N+1]  业务单号（bizNo）
--     [N+2]  平台标识（platformId）
--     [N+3]  超时时间（秒）
--     [N+4]  操作类型（deduct=扣减, add=增加）
--
-- 返回值：[状态码, 消息, bizNo, snapshotKey]
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
local op_type     = ARGV[N + 4]

-- 构建幂等性key和快照key
local idempotent_key = "biz:idempotent:" .. op_type .. ":" .. biz_no
local snapshot_key   = "biz:snapshot:" .. op_type .. ":" .. biz_no

-- 1. 幂等性检查
local processed = redis.call('GET', idempotent_key)
if processed == 'success' then
    return {1, "already_success", biz_no, snapshot_key}
elseif processed == 'failed' then
    return {0, "already_failed", biz_no}
end

-- 2. 标记为处理中
redis.call('SETEX', idempotent_key, timeout, 'processing')

-- 3. 预检查并记录快照
local snapshot = {}
local failed_index = nil

for i = 1, N do
    local current_value = redis.call('GET', KEYS[i])
    if not current_value then
        redis.call('SET', idempotent_key, 'failed')
        return {0, "key_not_found", KEYS[i]}
    end

    local need = tonumber(ARGV[i])
    local current_num = tonumber(current_value)

    if op_type == 'deduct' and current_num < need then
        failed_index = i
        break
    end

    snapshot[KEYS[i]] = current_value
end

-- 4. 预检查失败处理
if failed_index then
    redis.call('SET', idempotent_key, 'failed')
    return {0, "insufficient_value", KEYS[failed_index],
            snapshot[KEYS[failed_index]], ARGV[failed_index]}
end

-- 5. 执行原子操作
for i = 1, N do
    local value = tonumber(ARGV[i])
    if op_type == 'deduct' then
        local new_value = redis.call('DECRBY', KEYS[i], value)
        if new_value < 0 then
            redis.call('SET', idempotent_key, 'failed')
            return {0, "negative_stock_detected", KEYS[i]}
        end
    elseif op_type == 'add' then
        redis.call('INCRBY', KEYS[i], value)
    else
        redis.call('SET', idempotent_key, 'failed')
        return {0, "unsupported_operation", op_type}
    end
end

-- 6. 保存快照
redis.call('SETEX', snapshot_key, timeout, cjson.encode(snapshot))

-- 7. 构造队列消息（包含商品明细）
local items = {}
for i = 1, N do
    table.insert(items, {
        key = KEYS[i],
        quantity = tonumber(ARGV[i])
    })
end
local queue_msg = cjson.encode({
    bizNo = biz_no,
    platformId = platform_id,
    items = items
})
redis.call('RPUSH', 'async:queue:' .. op_type, queue_msg)

-- 8. 标记成功
redis.call('SETEX', idempotent_key, timeout, 'success')

-- 9. 返回
return {1, "success", biz_no, snapshot_key}