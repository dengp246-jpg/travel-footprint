const { getToken, request } = require('../../utils/request')

Page({
  data: {
    mine: false,
    selectedProvince: '',
    provinces: [],
    points: [],
    visiblePoints: []
  },

  onLoad() {
    this.loadOverview()
  },

  onShow() {
    this.loadOverview()
  },

  async loadOverview() {
    try {
      const data = await request({
        url: '/api/mini/map/overview',
        data: {
          mine: this.data.mine
        }
      })
      this.setData({
        provinces: data.provinces,
        points: data.points
      })
      this.applyProvinceFilter(this.data.selectedProvince)
    } catch (error) {
      if (this.data.mine && !getToken()) {
        wx.showToast({
          title: '请先登录',
          icon: 'none'
        })
      } else {
        wx.showToast({
          title: error.message || '足迹分布加载失败',
          icon: 'none'
        })
      }
    }
  },

  switchScope(event) {
    const mine = event.currentTarget.dataset.mine === 'true'
    this.setData({
      mine,
      selectedProvince: ''
    })
    this.loadOverview()
  },

  selectProvince(event) {
    const province = event.currentTarget.dataset.province
    const nextProvince = this.data.selectedProvince === province ? '' : province
    this.applyProvinceFilter(nextProvince)
  },

  applyProvinceFilter(province) {
    const visiblePoints = this.data.points.filter((item) => !province || item.province === province)
    this.setData({
      selectedProvince: province,
      visiblePoints
    })
  },

  openDetail(event) {
    wx.navigateTo({
      url: `/pages/post-detail/post-detail?id=${event.currentTarget.dataset.id}`
    })
  }
})
