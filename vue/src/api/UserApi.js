import {post, get} from "@/request/request.js"

/** 员工列表（分页条件查询） */
export function findStaff(params){
    return post('/staff/list', params)
}

/** 员工详情（含审计字段） */
export function getStaffDetail(id){
    return get('/staff/' + id)
}

/** 人事创建员工 */
export function createStaff(params){
    return post('/staff/create', params)
}

/** 监管审核员工 */
export function approveStaff(id, params){
    return post('/staff/' + id + '/approve', params)
}

/** 更新员工 */
export function updateStaff(id, params){
    return post('/staff/update', {
        id: id,
        name: params.name,
        status: params.status,
        roleId: params.roleId
    })
}

/** 删除员工 */
export function deleteStaff(id){
    return get('/staff/' + id, { _method: 'DELETE' })
}
