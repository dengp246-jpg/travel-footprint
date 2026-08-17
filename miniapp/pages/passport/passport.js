const { getToken, request, resolveAsset } = require('../../utils/request')

Page({
  data: {
    loading: false,
    passport: null
  },

  onShow() {
    if (!getToken()) {
      wx.showModal({
        title: '登录后查看旅行护照',
        content: '护照只使用你自己的真实足迹生成印章、勋章和旅程签注。',
        confirmText: '去登录',
        showCancel: false,
        success: () => wx.navigateTo({ url: '/pages/login/login' })
      })
      return
    }
    this.loadPassport()
  },

  onPullDownRefresh() {
    this.loadPassport().finally(() => wx.stopPullDownRefresh())
  },

  async loadPassport() {
    if (this.data.loading || !getToken()) return
    this.setData({ loading: true })
    try {
      const passport = await request({ url: '/api/mini/passport' })
      this.setData({
        passport: {
          ...passport,
          joinedLabel: passport.joinedAt ? passport.joinedAt.slice(0, 10).replace(/-/g, '.') : '',
          milestones: (passport.milestones || []).map((item) => ({
            ...item,
            dateLabel: item.journeyDate ? item.journeyDate.replace(/-/g, '.') : '日期待补充',
            photoUrl: resolveAsset(item.photoPath),
            videoUrl: resolveAsset(item.videoPath)
          })),
          stamps: (passport.stamps || []).map((item) => ({
            ...item,
            firstVisitedLabel: item.firstVisitedOn ? item.firstVisitedOn.replace(/-/g, '.') : ''
          }))
        }
      })
    } catch (error) {
      wx.showToast({ title: error.message || '旅行护照加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  goStory() {
    wx.setStorageSync('miniMapScope', 'personal')
    wx.switchTab({ url: '/pages/map/map' })
  },

  openDetail(event) {
    wx.navigateTo({ url: `/pages/post-detail/post-detail?id=${event.currentTarget.dataset.id}` })
  }
})
