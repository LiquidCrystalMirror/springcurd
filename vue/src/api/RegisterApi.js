import {post} from "@/request/request.js"
export  function registerApi(params){
    return post('/auth/register',params)
}
