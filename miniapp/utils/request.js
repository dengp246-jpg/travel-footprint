const config = require('../config')

const TOKEN_KEY = 'miniToken'
const USER_KEY = 'miniUser'
const BASE_URL_KEY = 'miniBaseUrl'

function normalizeBaseUrl(value) {
  return String(value || '')
    .trim()
    .replace(/\/+$/, '')
}

function getBaseUrl() {
  return normalizeBaseUrl(wx.getStorageSync(BASE_URL_KEY) || config.baseUrl)
}

function setBaseUrl(value) {
  const normalized = normalizeBaseUrl(value)
  if (!/^https?:\/\/[^\s]+$/i.test(normalized)) {
    throw new Error('请输入以 http:// 或 https:// 开头的服务器地址')
  }
  wx.setStorageSync(BASE_URL_KEY, normalized)
  return normalized
}

function getToken() {
  return wx.getStorageSync(TOKEN_KEY) || ''
}

function setAuth(token, user) {
  if (token) {
    wx.setStorageSync(TOKEN_KEY, token)
  }
  if (user) {
    wx.setStorageSync(USER_KEY, user)
  }
}

function clearAuth() {
  wx.removeStorageSync(TOKEN_KEY)
  wx.removeStorageSync(USER_KEY)
}

function getUser() {
  return wx.getStorageSync(USER_KEY) || null
}

function buildUrl(path) {
  if (/^https?:\/\//i.test(path)) {
    return path
  }
  return `${getBaseUrl()}${path}`
}

function errorPayload(res, fallback) {
  if (res && res.data && typeof res.data === 'object') {
    return res.data
  }
  return { message: fallback }
}

function request(options) {
  return new Promise((resolve, reject) => {
    wx.request({
      url: buildUrl(options.url),
      method: options.method || 'GET',
      data: options.data,
      timeout: options.timeout || 15000,
      header: {
        'Content-Type': 'application/json',
        'X-Mini-Token': getToken(),
        ...(options.header || {})
      },
      success(res) {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data)
          return
        }
        if (res.statusCode === 401) {
          clearAuth()
        }
        reject(errorPayload(res, `请求失败（${res.statusCode}）`))
      },
      fail(error) {
        reject({
          ...error,
          message: '无法连接服务器，请检查网络和连接设置。'
        })
      }
    })
  })
}

function upload(options) {
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: buildUrl(options.url),
      filePath: options.filePath,
      name: options.name || 'photo',
      formData: options.formData || {},
      timeout: options.timeout || 30000,
      header: {
        'X-Mini-Token': getToken(),
        ...(options.header || {})
      },
      success(res) {
        let data = {}
        try {
          data = res.data ? JSON.parse(res.data) : {}
        } catch (error) {
          data = { message: '服务器返回了无法识别的数据。' }
        }
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(data)
          return
        }
        if (res.statusCode === 401) {
          clearAuth()
        }
        reject(data || { message: '上传失败，请稍后再试。' })
      },
      fail(error) {
        reject({
          ...error,
          message: '文件上传失败，请检查网络后重试。'
        })
      }
    })
  })
}

function resolveAsset(path) {
  if (!path) {
    return ''
  }
  const url = buildUrl(path)
  const token = getToken()
  if (!token || !String(path).startsWith('/uploads/')) {
    return url
  }
  return `${url}${url.includes('?') ? '&' : '?'}miniToken=${encodeURIComponent(token)}`
}

module.exports = {
  TOKEN_KEY,
  USER_KEY,
  BASE_URL_KEY,
  getBaseUrl,
  setBaseUrl,
  getToken,
  getUser,
  setAuth,
  clearAuth,
  buildUrl,
  request,
  upload,
  resolveAsset
}
