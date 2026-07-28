-- =====================================================
-- 通用批量回滚脚本
-- 根据快照恢复数据到操作前的状态
--
-- 参数说明：
--   KEYS: 需要回滚的key列表（可选，为空则从快照恢复所有key）
--   ARGV:
--     [1]  业务单号（用于定位快照）
--     [2]  平台标识（用于定位快照）
--     [3]  操作类型（与之前的操作类型对应：deduct/add）
--     [4]  是否删除快照（1=删除，0=保留）
--
-- 返回值：[状态码, 消息, 业务单号]
--   状态码: 1-成功, 0-失败
-- =====================================================

-- 提取参数
local biz_no = ARGV[1]
local platform = ARGV[2]
local op_type = ARGV[3]
local del_snapshot = tonumber(ARGV[4] or 1)  -- 默认删除快照

-- 构建快照key和幂等性key
local snapshot_key = "biz:snapshot:" .. op_type .. ":" .. biz_no
local idempotent_key = "biz:idempotent:" .. op_type .. ":" .. biz_no

-- 1. 获取快照数据
local snapshot_json = redis.call('GET', snapshot_key)
if not snapshot_json then
    -- 快照不存在，无法回滚
    return {0, "snapshot_not_found", biz_no}
end

-- 2. 解析快照JSON数据
local snapshot = cjson.decode(snapshot_json)

-- 3. 恢复数据
if #KEYS > 0 then
    -- 只恢复指定的keys
    for i = 1, #KEYS do
        if snapshot[KEYS[i]] then
            -- 从快照中取出操作前的值并恢复
            local original_value = snapshot[KEYS[i]]
            redis.call('SET', KEYS[i], original_value)
        end
    end
else
    -- 恢复快照中的所有keys
    for key, original_value in pairs(snapshot) do
        redis.call('SET', key, original_value)
    end
end

-- 4. 清理快照（根据参数决定是否删除）
if del_snapshot == 1 then
    redis.call('DEL', snapshot_key)
end

-- 5. 清理幂等性标记（允许重新执行该业务操作）
redis.call('DEL', idempotent_key)

-- 6. 返回成功结果
return {1, "rollback_success", biz_no}