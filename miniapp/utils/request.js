const config = require('../config')

const TOKEN_KEY = 'miniToken'
const USER_KEY = 'miniUser'

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
  if (path.startsWith('http')) {
    return path
  }
  return `${config.baseUrl}${path}`
}

function request(options) {
  return new Promise((resolve, reject) => {
    wx.request({
      url: buildUrl(options.url),
      method: options.method || 'GET',
      data: options.data,
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
        reject(res.data || { message: '请求失败，请稍后再试。' })
      },
      fail(error) {
        reject(error)
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
      header: {
        'X-Mini-Token': getToken(),
        ...(options.header || {})
      },
      success(res) {
        const data = res.data ? JSON.parse(res.data) : {}
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(data)
          return
        }
        reject(data || { message: '上传失败，请稍后再试。' })
      },
      fail(error) {
        reject(error)
      }
    })
  })
}

function resolveAsset(path) {
  if (!path) {
    return ''
  }
  return buildUrl(path)
}

module.exports = {
  TOKEN_KEY,
  USER_KEY,
  getToken,
  getUser,
  setAuth,
  clearAuth,
  buildUrl,
  request,
  upload,
  resolveAsset
}
