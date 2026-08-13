const { clearAuth, getBaseUrl, getToken, request, resolveAsset } = require('../../utils/request')

Page({
  data: {
    loading: false,
    user: null,
    posts: [],
    baseUrl: ''
  },

  onShow() {
    this.setData({ baseUrl: getBaseUrl() })
    this.loadPage()
  },

  onPullDownRefresh() {
    this.loadPage().finally(() => wx.stopPullDownRefresh())
  },

  async loadPage() {
    if (!getToken()) {
      this.setData({ user: null, posts: [] })
      return
    }
    this.setData({ loading: true })
    try {
      const [user, posts] = await Promise.all([
        request({ url: '/api/mini/auth/me' }),
        request({ url: '/api/mini/posts', data: { mine: true } })
      ])
      this.setData({
        user: {
          ...user,
          initial: (user.nickname || user.username || '旅').slice(0, 1),
          avatarUrl: resolveAsset(user.avatarPath)
        },
        posts: posts.map((item) => ({
          ...item,
          dateLabel: item.travelDate ? item.travelDate.replace(/-/g, '.') : ''
        }))
      })
    } catch (error) {
      clearAuth()
      this.setData({ user: null, posts: [] })
      wx.showToast({ title: error.message || '登录状态已失效', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  goLogin() {
    wx.navigateTo({ url: '/pages/login/login' })
  },

  goReport() {
    wx.navigateTo({ url: '/pages/report/report' })
  },

  goServer() {
    wx.navigateTo({ url: '/pages/server/server' })
  },

  goPlans() {
    wx.switchTab({ url: '/pages/plans/plans' })
  },

  goMap() {
    wx.switchTab({ url: '/pages/map/map' })
  },

  async logout() {
    try {
      await request({ url: '/api/mini/auth/logout', method: 'POST' })
    } catch (error) {
      // Local authentication is cleared even when the server is temporarily unavailable.
    }
    clearAuth()
    this.setData({ user: null, posts: [] })
    wx.showToast({ title: '已退出登录', icon: 'success' })
  },

  openDetail(event) {
    wx.navigateTo({ url: `/pages/post-detail/post-detail?id=${event.currentTarget.dataset.id}` })
  }
})
