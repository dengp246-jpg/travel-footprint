const { getToken, request, resolveAsset } = require('../../utils/request')

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
    scale: 4,
    storyIndex: -1,
    storyMax: 0,
    storyPoint: null,
    storyPlaying: false
  },

  onShow() {
    const requestedScope = wx.getStorageSync('miniMapScope')
    if (requestedScope === 'personal' && getToken()) {
      wx.removeStorageSync('miniMapScope')
      this.setData({ mine: true, selectedProvince: '' })
    }
    this.loadOverview()
  },

  onHide() {
    this.stopStory()
  },

  onUnload() {
    this.stopStory()
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
      const points = (data.points || []).map((item) => ({
        ...item,
        dateLabel: item.travelDate || (item.createdAt ? item.createdAt.slice(0, 10) : '日期待补充'),
        photoUrl: resolveAsset(item.photoPath),
        videoUrl: resolveAsset(item.videoPath)
      }))
      this.setData({ provinces: data.provinces || [], points })
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
    this.stopStory()
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
    const polyline = this.buildPolyline(includePoints)
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
      storyIndex: -1,
      storyMax: Math.max(visiblePoints.length - 1, 0),
      storyPoint: null,
      centerLatitude,
      centerLongitude,
      scale: province ? 7 : (includePoints.length === 1 ? 9 : 4)
    })
  },

  buildPolyline(points) {
    return this.data.mine && points.length > 1 ? [{
      points,
      color: '#176557CC',
      width: 4,
      dottedLine: false,
      arrowLine: true,
      borderColor: '#ffffff',
      borderWidth: 1
    }] : []
  },

  showStoryAt(requestedIndex) {
    const points = this.data.visiblePoints
    if (!points.length) return
    const storyIndex = Math.max(0, Math.min(Number(requestedIndex) || 0, points.length - 1))
    const storyPoint = points[storyIndex]
    const routePoints = points.slice(0, storyIndex + 1).map((item) => ({
      latitude: item.latitude,
      longitude: item.longitude
    }))
    this.setData({
      storyIndex,
      storyPoint,
      polyline: this.buildPolyline(routePoints),
      centerLatitude: storyPoint.latitude,
      centerLongitude: storyPoint.longitude,
      scale: 8
    })
  },

  playStory() {
    if (!this.data.mine || !this.data.visiblePoints.length) return
    if (this.storyTimer) {
      this.stopStory()
      return
    }
    const nextIndex = this.data.storyIndex >= this.data.visiblePoints.length - 1
      ? 0
      : this.data.storyIndex + 1
    this.showStoryAt(nextIndex)
    this.setData({ storyPlaying: true })
    this.storyTimer = setInterval(() => {
      if (this.data.storyIndex >= this.data.visiblePoints.length - 1) {
        this.stopStory()
        return
      }
      this.showStoryAt(this.data.storyIndex + 1)
    }, 1700)
  },

  stopStory() {
    if (this.storyTimer) {
      clearInterval(this.storyTimer)
      this.storyTimer = null
    }
    if (this.data.storyPlaying) this.setData({ storyPlaying: false })
  },

  previousStory() {
    this.stopStory()
    this.showStoryAt(Math.max(0, this.data.storyIndex - 1))
  },

  nextStory() {
    this.stopStory()
    this.showStoryAt(Math.min(this.data.visiblePoints.length - 1, this.data.storyIndex + 1))
  },

  onStorySlider(event) {
    this.stopStory()
    this.showStoryAt(Number(event.detail.value))
  },

  resetStory() {
    this.stopStory()
    this.applyProvinceFilter(this.data.selectedProvince)
  },

  onMarkerTap(event) {
    const index = this.data.visiblePoints.findIndex((item) => Number(item.postId) === Number(event.detail.markerId))
    if (index >= 0) this.showStoryAt(index)
  },

  openStoryDetail() {
    if (!this.data.storyPoint) return
    wx.navigateTo({ url: `/pages/post-detail/post-detail?id=${this.data.storyPoint.postId}` })
  },

  openDetail(event) {
    wx.navigateTo({ url: `/pages/post-detail/post-detail?id=${event.currentTarget.dataset.id}` })
  }
})
