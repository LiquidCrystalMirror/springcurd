import {post} from "@/request/request.js"

// 获取订单列表（Dashboard查询）
export function getOrders(params){
    return post('/dashboard/getOrders', params)
}

// 添加订单
export function addOrder(params){
    return post('/orders/add', params)
}

// 取消订单
export function cancelOrder(params){
    return post('/orders/cancel', params)
}