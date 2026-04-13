-- =====================================================
-- 通用批量库存操作脚本
-- 支持任意数量的商品批量扣减/增加，带幂等性和快照
--
-- 参数说明：
--   KEYS: 操作的目标key列表（库存key，如 product:stock:1）
--   ARGV:
--     [1..N] 对应每个key的操作值（扣减或增加的数量）
--     [N+1]  业务单号（用于幂等性控制）
--     [N+2]  超时时间（秒）
--     [N+3]  操作类型（deduct=扣减, add=增加）
--
-- 返回值：[状态码, 消息, 业务单号, 快照key]
--   状态码: 1-成功, 0-失败
-- =====================================================

-- 提取参数
local biz_no = ARGV[#ARGV - 2]       -- 业务单号
local timeout = tonumber(ARGV[#ARGV - 1])  -- 超时时间（秒）
local op_type = ARGV[#ARGV]          -- 操作类型

-- 构建幂等性key和快照key
local idempotent_key = "biz:idempotent:" .. op_type .. ":" .. biz_no
local snapshot_key = "biz:snapshot:" .. op_type .. ":" .. biz_no

-- 1. 幂等性检查：防止重复处理同一业务单号
local processed = redis.call('GET', idempotent_key)
if processed == 'success' then
    -- 已经成功处理过，直接返回成功（幂等性保证）
    return {1, "already_success", biz_no, snapshot_key}
elseif processed == 'failed' then
    -- 之前处理失败过，直接返回失败
    return {0, "already_failed", biz_no}
end

-- 2. 标记为"处理中"状态，设置过期时间防止死锁
redis.call('SETEX', idempotent_key, timeout, 'processing')

-- 3. 预检查并记录操作前的快照
local snapshot = {}
local failed_index = nil

for i = 1, #KEYS do
    local current_value = redis.call('GET', KEYS[i])

    -- 检查key是否存在
    if not current_value then
        redis.call('SET', idempotent_key, 'failed')
        return {0, "key_not_found", KEYS[i]}
    end

    local need = tonumber(ARGV[i])
    local current_num = tonumber(current_value)

    -- 如果是扣减操作，检查库存是否充足
    if op_type == 'deduct' then
        if current_num < need then
            failed_index = i
            break
        end
    end

    -- 记录快照：只保存操作前的值，用于回滚
    snapshot[KEYS[i]] = current_value
end

-- 4. 如果预检查失败，更新状态并返回
if failed_index then
    redis.call('SET', idempotent_key, 'failed')
    return {0, "insufficient_value", KEYS[failed_index],
            snapshot[KEYS[failed_index]], ARGV[failed_index]}
end

-- 5. 执行批量操作（原子性操作）
for i = 1, #KEYS do
    local value = tonumber(ARGV[i])
    local new_value

    -- 根据操作类型执行不同的命令
    if op_type == 'deduct' then
        -- 扣减操作
        new_value = redis.call('DECRBY', KEYS[i], value)

        -- 防止库存变为负数（双重保障）
        if new_value < 0 then
            redis.call('SET', idempotent_key, 'failed')
            return {0, "negative_stock_detected", KEYS[i]}
        end
    elseif op_type == 'add' then
        -- 增加操作
        new_value = redis.call('INCRBY', KEYS[i], value)
    else
        -- 不支持的操作类型
        redis.call('SET', idempotent_key, 'failed')
        return {0, "unsupported_operation", op_type}
    end
end

-- 6. 保存快照到Redis（用于回滚）
-- 将snapshot表转换为JSON字符串存储
local snapshot_json = cjson.encode(snapshot)
redis.call('SETEX', snapshot_key, timeout, snapshot_json)

-- 7. 加入异步队列（用于后续持久化到数据库）
redis.call('RPUSH', 'async:queue:' .. op_type, biz_no)

-- 8. 标记操作成功，设置过期时间
redis.call('SET', idempotent_key, 'success')
redis.call('EXPIRE', idempotent_key, timeout)

-- 9. 返回成功结果
return {1, "success", biz_no, snapshot_key}