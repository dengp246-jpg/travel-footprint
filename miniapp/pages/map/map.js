const { getToken, request } = require('../../utils/request')

Page({
  data: {
    loading: false,
    mine: false,
    selectedProvince: '',
    provinces: [],
    points: [],
    visiblePoints: [],
    markers: [],
    polyline: [],
    includePoints: [],
    centerLatitude: 35.8617,
    centerLongitude: 104.1954,
    scale: 4
  },

  onShow() {
    this.loadOverview()
  },

  onPullDownRefresh() {
    this.loadOverview().finally(() => wx.stopPullDownRefresh())
  },

  async loadOverview() {
    if (this.data.loading) return
    this.setData({ loading: true })
    try {
      const data = await request({
        url: '/api/mini/map/overview',
        data: { mine: this.data.mine }
      })
      this.setData({ provinces: data.provinces, points: data.points })
      this.applyProvinceFilter(this.data.selectedProvince)
    } catch (error) {
      if (this.data.mine && !getToken()) {
        wx.showModal({
          title: '查看个人地图',
          content: '登录后才能查看自己的足迹点位和旅行轨迹。',
          confirmText: '去登录',
          success: (res) => {
            if (res.confirm) wx.navigateTo({ url: '/pages/login/login' })
          }
        })
        this.setData({ mine: false })
      } else {
        wx.showToast({ title: error.message || '足迹地图加载失败', icon: 'none' })
      }
    } finally {
      this.setData({ loading: false })
    }
  },

  switchScope(event) {
    const mine = event.currentTarget.dataset.mine === 'true'
    if (mine && !getToken()) {
      wx.navigateTo({ url: '/pages/login/login' })
      return
    }
    this.setData({ mine, selectedProvince: '' })
    this.loadOverview()
  },

  selectProvince(event) {
    const province = event.currentTarget.dataset.province
    this.applyProvinceFilter(this.data.selectedProvince === province ? '' : province)
  },

  clearProvince() {
    this.applyProvinceFilter('')
  },

  applyProvinceFilter(province) {
    const visiblePoints = this.data.points.filter((item) => !province || item.province === province)
    const markers = visiblePoints.map((item, index) => ({
      id: Number(item.postId),
      latitude: item.latitude,
      longitude: item.longitude,
      title: item.location,
      width: 30,
      height: 38,
      callout: {
        content: `${index + 1}  ${item.location}`,
        color: '#17352f',
        fontSize: 12,
        borderRadius: 8,
        bgColor: '#ffffff',
        padding: 8,
        display: 'BYCLICK'
      },
      label: this.data.mine ? {
        content: String(index + 1),
        color: '#ffffff',
        fontSize: 10,
        anchorX: -4,
        anchorY: -30,
        bgColor: '#176557',
        borderRadius: 10,
        padding: 3
      } : undefined
    }))
    const includePoints = visiblePoints.map((item) => ({
      latitude: item.latitude,
      longitude: item.longitude
    }))
    const polyline = this.data.mine && includePoints.length > 1 ? [{
      points: includePoints,
      color: '#176557CC',
      width: 4,
      dottedLine: false,
      arrowLine: true,
      borderColor: '#ffffff',
      borderWidth: 1
    }] : []
    let centerLatitude = 35.8617
    let centerLongitude = 104.1954
    if (includePoints.length) {
      centerLatitude = includePoints.reduce((sum, item) => sum + item.latitude, 0) / includePoints.length
      centerLongitude = includePoints.reduce((sum, item) => sum + item.longitude, 0) / includePoints.length
    }
    this.setData({
      selectedProvince: province,
      visiblePoints,
      markers,
      polyline,
      includePoints,
      centerLatitude,
      centerLongitude,
      scale: province ? 7 : (includePoints.length === 1 ? 9 : 4)
    })
  },

  onMarkerTap(event) {
    wx.navigateTo({ url: `/pages/post-detail/post-detail?id=${event.detail.markerId}` })
  },

  openDetail(event) {
    wx.navigateTo({ url: `/pages/post-detail/post-detail?id=${event.currentTarget.dataset.id}` })
  }
})
