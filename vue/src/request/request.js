import axios from 'axios';
import qs from 'qs'
import { getToken, isTokenValid, redirectToLogin } from '@/service/AuthService'

const instance = axios.create({
    // baseURL: '/api',
    // timeout: 1000,
});
instance.defaults.headers.post['Content-Type'] = 'application/json';

// ... existing code ...

// 定义不需要 token 验证的白名单路径
const whiteList = ['/login', '/register']

// 添加请求拦截器，在发送请求前检查 token 有效性
instance.interceptors.request.use(function (config) {
    // 检查当前请求路径是否在白名单中
    const isWhitePath = whiteList.some(path => config.url.includes(path))

    // 如果在白名单中，跳过 token 验证
    if (isWhitePath) {
        return config
    }

    // 检查 token 是否有效
    if (!isTokenValid()) {
        redirectToLogin()
        return Promise.reject(new Error('登录已过期'))
    }

    // 如果 token 有效，添加到请求头
    const token = getToken()
    if (token) {
        config.headers['Authorization'] = `Bearer ${token}`
    }

    return config
}, function (error) {
    return Promise.reject(error)
})

// ... existing code ...


// 添加响应拦截器
instance.interceptors.response.use(function (response) {
    return response.data;
}, function (error) {
    console.log(error)
    
    // 如果是 401 错误，说明 token 无效，跳转到登录页
    if (error.response && error.response.status === 401) {
        redirectToLogin()
    }
    
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
        instance.post(url, data).then(
            response => {
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
