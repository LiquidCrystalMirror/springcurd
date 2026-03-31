import {post} from "@/request/request.js"
export  function registerApi(params){

    return post('/register',params)

}