import axios from 'axios';
import qs from 'qs'

const instance = axios.create({
    // baseURL: '/api',
    // timeout: 1000,
});
instance.defaults.headers.post['Content-Type'] = 'application/x-www-form-urlencoded'; //设置post请求是的header信息

// 添加响应拦截器
instance.interceptors.response.use(function (response) {
    // 2xx 范围内的状态码都会触发该函数。
    // 对响应数据做点什么
    // console.log('对响应数据做点什么')
    // console.log(response)
    // if (!response.data.flag) {
    //     return Promise.reject(response.data.errorMsg)
    // }
    return response.data;
}, function (error) {
    // 超出 2xx 范围的状态码都会触发该函数。
    // 对响应错误做点什么
    console.log(error)
    return Promise.reject("服务器异常");
});

/**
 * post 请求方法
 * @param url
 * @param data
 * @returns {Promise}
 */
export function post(url, data = {}) {
    return new Promise((resolve, reject) => {
        instance.post(url, qs.stringify(data)).then(
            response => {
                // console.log(response.data.code)
                resolve(response)
            },
            err => {
                reject(err)
            }
        )
    })
}


/**
 * get 请求方法
 * @param url
 * @param params
 * @returns {Promise}
 */
export function get(url, params = {}) {
    return new Promise((resolve, reject) => {
        instance
            .get(url, {
                params: params
            })
            .then(response => {
                resolve(response)
            })
            .catch(err => {
                reject(err)
            })
    })
}
