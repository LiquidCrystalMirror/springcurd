import {post} from "@/request/request.js"
export  function findUser(params){

    return post('/dashboard/getUsers',params)

}
export  function updateUser(id,params){

    return post('/dashboard/updateUsers',{
        name:params.name,
        status:params.status,
        id:id,
        roleId:params.roleId
    })

}
