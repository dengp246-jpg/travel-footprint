const { getBaseUrl, setBaseUrl, request } = require('../../utils/request')

Page({
  data: {
    baseUrl: '',
    testing: false,
    status: 'idle',
    statusText: '尚未检测连接'
  },

  onShow() {
    this.setData({ baseUrl: getBaseUrl() })
  },

  onInput(event) {
    this.setData({ baseUrl: event.detail.value, status: 'idle', statusText: '地址已修改，等待检测' })
  },

  async saveAndTest() {
    if (this.data.testing) return
    try {
      const baseUrl = setBaseUrl(this.data.baseUrl)
      this.setData({ baseUrl, testing: true, status: 'testing', statusText: '正在连接服务器…' })
      await request({ url: '/api/mini/catalog/provinces', timeout: 8000 })
      this.setData({ status: 'success', statusText: '连接成功，可以正常使用小程序' })
      wx.showToast({ title: '连接成功', icon: 'success' })
    } catch (error) {
      this.setData({ status: 'error', statusText: error.message || '连接失败，请检查地址和网络' })
      wx.showToast({ title: error.message || '连接失败', icon: 'none' })
    } finally {
      this.setData({ testing: false })
    }
  },

  useLocalhost() {
    this.setData({ baseUrl: 'http://127.0.0.1:8080', status: 'idle', statusText: '适用于微信开发者工具' })
  },

  copyExample() {
    wx.setClipboardData({ data: 'http://192.168.1.20:8080' })
  }
})
