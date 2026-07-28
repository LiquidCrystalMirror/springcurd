
import {post} from "@/request/request.js"
export  function loginApi(params){
    return post('/auth/login',params)
}
