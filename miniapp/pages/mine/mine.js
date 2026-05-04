const { clearAuth, getToken, request } = require('../../utils/request')

Page({
  data: {
    user: null,
    posts: []
  },

  onShow() {
    this.loadPage()
  },

  async loadPage() {
    if (!getToken()) {
      this.setData({
        user: null,
        posts: []
      })
      return
    }

    try {
      const [user, posts] = await Promise.all([
        request({ url: '/api/mini/auth/me' }),
        request({ url: '/api/mini/posts', data: { mine: true } })
      ])
      this.setData({
        user,
        posts
      })
    } catch (error) {
      clearAuth()
      this.setData({
        user: null,
        posts: []
      })
      wx.showToast({
        title: error.message || '登录状态已失效',
        icon: 'none'
      })
    }
  },

  goLogin() {
    wx.navigateTo({
      url: '/pages/login/login'
    })
  },

  async logout() {
    try {
      await request({
        url: '/api/mini/auth/logout',
        method: 'POST'
      })
    } catch (error) {
      // Ignore logout request failures and clear local state anyway.
    }
    clearAuth()
    this.setData({
      user: null,
      posts: []
    })
    wx.showToast({
      title: '已退出登录',
      icon: 'success'
    })
  },

  openDetail(event) {
    wx.navigateTo({
      url: `/pages/post-detail/post-detail?id=${event.currentTarget.dataset.id}`
    })
  }
})
