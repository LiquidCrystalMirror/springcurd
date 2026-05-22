import {post} from "@/request/request.js"

// 获取库存列表（Dashboard查询）
export function getStocks(params){
    return post('/dashboard/getStocks', params)
}

// 商品补货
export function replenishStock(params){
    return post('/admin/stock/replenish', params)
}