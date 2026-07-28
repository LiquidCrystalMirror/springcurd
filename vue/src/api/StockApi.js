import {post} from "@/request/request.js"

// 获取库存列表
export function getStocks(params){
    return post('/dashboard/stocks', params)
}

// 商品补货
export function replenishStock(params){
    return post('/replenish/submit', params)
}
