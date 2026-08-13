const { getToken, request } = require('../../utils/request')

Page({
  data: {
    loading: false,
    period: 'month',
    anchorDate: '',
    periods: [
      { value: 'week', label: '每周' },
      { value: 'month', label: '每月' },
      { value: 'year', label: '每年' }
    ],
    report: null
  },

  onLoad(options) {
    if (options.period) this.setData({ period: options.period })
  },

  onShow() {
    if (!getToken()) {
      wx.navigateTo({ url: '/pages/login/login' })
      return
    }
    this.loadReport()
  },

  onPullDownRefresh() {
    this.loadReport().finally(() => wx.stopPullDownRefresh())
  },

  selectPeriod(event) {
    this.setData({ period: event.currentTarget.dataset.period, anchorDate: '' })
    this.loadReport()
  },

  previousPeriod() {
    if (!this.data.report) return
    this.setData({ anchorDate: this.data.report.previousDate })
    this.loadReport()
  },

  nextPeriod() {
    if (!this.data.report || !this.data.report.hasNext) return
    this.setData({ anchorDate: this.data.report.nextDate })
    this.loadReport()
  },

  async loadReport() {
    if (this.data.loading || !getToken()) return
    this.setData({ loading: true })
    try {
      const report = await request({
        url: '/api/mini/reports',
        data: { period: this.data.period, date: this.data.anchorDate }
      })
      this.setData({ report })
    } catch (error) {
      wx.showToast({ title: error.message || '报告生成失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  openPost(event) {
    wx.navigateTo({ url: `/pages/post-detail/post-detail?id=${event.currentTarget.dataset.id}` })
  }
})
