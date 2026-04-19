-- 补货专用脚本（同步版，不发送队列）
-- KEYS: product:stock:xxx
-- ARGV[1..N]: 对应增加数量
-- ARGV[N+1]: replenishNo
-- ARGV[N+2]: 幂等超时秒数
-- 返回: [状态码, 消息, replenishNo, json结果]
--
-- 注意：如果Redis中key不存在，会自动初始化为0后再增加

local N = #KEYS
if N == 0 then return {0, "no_keys_provided"} end

local replenish_no = ARGV[N + 1]
local timeout = tonumber(ARGV[N + 2])
local idempotent_key = "replenish:idempotent:" .. replenish_no

local processed = redis.call('GET', idempotent_key)
if processed == 'success' then
    return {1, "already_success", replenish_no, "{}"}
elseif processed == 'failed' then
    return {0, "already_failed", replenish_no}
end

redis.call('SETEX', idempotent_key, timeout, 'processing')

local result = {}
for i = 1, N do
    local before = redis.call('GET', KEYS[i])
    local qty = tonumber(ARGV[i])
    
    -- 如果key不存在，自动初始化为0
    if not before then
        redis.call('SET', KEYS[i], 0)
        before = 0
    end
    
    local after = redis.call('INCRBY', KEYS[i], qty)
    result[KEYS[i]] = {before = tonumber(before), after = after}
end

redis.call('SETEX', idempotent_key, timeout, 'success')
return {1, "success", replenish_no, cjson.encode(result)}