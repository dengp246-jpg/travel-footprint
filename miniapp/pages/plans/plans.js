const { getToken, request } = require('../../utils/request')

function emptyForm() {
  return {
    title: '',
    destination: '',
    startDate: '',
    endDate: '',
    budget: '',
    status: 'PLANNED',
    notes: ''
  }
}

Page({
  data: {
    loading: false,
    submitting: false,
    showForm: false,
    loggedIn: false,
    plans: [],
    statusIndex: 0,
    statusOptions: [
      { value: 'PLANNED', label: '规划中' },
      { value: 'BOOKED', label: '已预订' },
      { value: 'FINISHED', label: '已完成' }
    ],
    form: emptyForm()
  },

  onShow() {
    const loggedIn = Boolean(getToken())
    this.setData({ loggedIn })
    if (!loggedIn) {
      this.setData({ plans: [] })
      return
    }
    this.loadPlans()
  },

  onPullDownRefresh() {
    this.loadPlans().finally(() => wx.stopPullDownRefresh())
  },

  async loadPlans() {
    if (!getToken() || this.data.loading) return
    this.setData({ loading: true })
    try {
      const plans = await request({ url: '/api/mini/plans' })
      this.setData({
        plans: plans.map((item) => ({
          ...item,
          dateRange: item.startDate
            ? `${item.startDate.replace(/-/g, '.')} — ${(item.endDate || item.startDate).replace(/-/g, '.')}`
            : '日期待定',
          budgetLabel: item.budget === null || item.budget === undefined ? '待定' : `¥${item.budget}`
        }))
      })
    } catch (error) {
      wx.showToast({ title: error.message || '计划加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  toggleForm() {
    if (!getToken()) {
      wx.navigateTo({ url: '/pages/login/login' })
      return
    }
    this.setData({ showForm: !this.data.showForm })
  },

  onFieldChange(event) {
    this.setData({ [`form.${event.currentTarget.dataset.field}`]: event.detail.value })
  },

  onStartDateChange(event) {
    const startDate = event.detail.value
    this.setData({
      'form.startDate': startDate,
      'form.endDate': this.data.form.endDate || startDate
    })
  },

  onEndDateChange(event) {
    this.setData({ 'form.endDate': event.detail.value })
  },

  onStatusChange(event) {
    const statusIndex = Number(event.detail.value)
    this.setData({
      statusIndex,
      'form.status': this.data.statusOptions[statusIndex].value
    })
  },

  async createPlan() {
    if (this.data.submitting) return
    const { title, destination, startDate, endDate } = this.data.form
    if (!title.trim() || !destination.trim()) {
      wx.showToast({ title: '请填写计划名称和目的地', icon: 'none' })
      return
    }
    if (startDate && endDate && endDate < startDate) {
      wx.showToast({ title: '结束日期不能早于开始日期', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    try {
      await request({ url: '/api/mini/plans', method: 'POST', data: this.data.form })
      wx.showToast({ title: '计划已创建', icon: 'success' })
      this.setData({ showForm: false, statusIndex: 0, form: emptyForm() })
      this.loadPlans()
    } catch (error) {
      wx.showToast({ title: error.message || '创建失败', icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  },

  deletePlan(event) {
    const id = event.currentTarget.dataset.id
    const title = event.currentTarget.dataset.title
    wx.showModal({
      title: '删除行程计划',
      content: `确定删除“${title}”吗？关联足迹会保留。`,
      confirmColor: '#a44535',
      success: async (res) => {
        if (!res.confirm) return
        try {
          await request({ url: `/api/mini/plans/${id}`, method: 'DELETE' })
          wx.showToast({ title: '计划已删除', icon: 'success' })
          this.loadPlans()
        } catch (error) {
          wx.showToast({ title: error.message || '删除失败', icon: 'none' })
        }
      }
    })
  },

  goLogin() {
    wx.navigateTo({ url: '/pages/login/login' })
  },

  openReport() {
    if (!getToken()) {
      wx.navigateTo({ url: '/pages/login/login' })
      return
    }
    wx.navigateTo({ url: '/pages/report/report' })
  }
})
