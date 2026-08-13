const { getBaseUrl, request, setAuth } = require('../../utils/request')

Page({
  data: {
    mode: 'login',
    submitting: false,
    baseUrl: '',
    form: {
      username: '',
      nickname: '',
      password: '',
      confirmPassword: '',
      bio: ''
    }
  },

  onShow() {
    this.setData({ baseUrl: getBaseUrl() })
  },

  switchMode(event) {
    this.setData({
      mode: event.currentTarget.dataset.mode
    })
  },

  onFieldChange(event) {
    const field = event.currentTarget.dataset.field
    this.setData({
      [`form.${field}`]: event.detail.value
    })
  },

  async submitForm() {
    if (this.data.submitting) {
      return
    }

    const { mode, form } = this.data
    if (!form.username.trim() || !form.password) {
      wx.showToast({ title: '请输入用户名和密码', icon: 'none' })
      return
    }
    if (mode === 'register' && (!form.nickname.trim() || form.password !== form.confirmPassword)) {
      wx.showToast({ title: '请检查昵称和两次密码', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    const url = mode === 'login' ? '/api/mini/auth/login' : '/api/mini/auth/register'
    try {
      const response = await request({
        url,
        method: 'POST',
        data: form
      })
      setAuth(response.token, response.user)
      wx.showToast({
        title: mode === 'login' ? '登录成功' : '注册成功',
        icon: 'success'
      })
      wx.switchTab({
        url: '/pages/feed/feed'
      })
    } catch (error) {
      wx.showToast({
        title: error.message || '操作失败',
        icon: 'none'
      })
    } finally {
      this.setData({ submitting: false })
    }
  },

  goServer() {
    wx.navigateTo({ url: '/pages/server/server' })
  }
})
