import { post } from "@/request/request.js"

/**
 * 补货单列表（分页，联表查询）
 * @param {Object} params - { page, pageSize, status }
 */
export function getReplenishList(params) {
    return post('/replenish/list', params)
}

/**
 * 审核补货单（通过或拒绝）
 * @param {Object} params - { id, approved, remark }
 */
export function approveReplenish(params) {
    return post('/replenish/approve', params)
}

/**
 * 提交补货申请（批量）
 * @param {Object} params - { items: [{ productId, productName, quantity }] }
 */
export function submitReplenish(params) {
    return post('/replenish/submit', params)
}
