-- 补货专用脚本（重构版 - 无状态模式）
-- 由于后端每次生成唯一的雪花ID，天然防重，无需Redis幂等检查
-- KEYS: product:stock:xxx
-- ARGV[1..N]: 对应增加数量
-- 返回: [1, "success", json结果]
--
-- 注意：如果Redis中key不存在，会自动初始化为0后再增加

local N = #KEYS
if N == 0 then return {0, "no_keys_provided"} end

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

-- 直接返回成功结果，不再维护幂等key
return {1, "success", cjson.encode(result)}