const { getUser, request, resolveAsset } = require('../../utils/request')

Page({
  data: {
    loading: false,
    query: '',
    provinceIndex: 0,
    provinceOptions: ['全部省份'],
    posts: [],
    user: null,
    provinceCount: 0
  },

  onLoad() {
    this.loadCatalog()
  },

  onShow() {
    this.setData({ user: getUser() })
    this.loadPosts()
  },

  onPullDownRefresh() {
    Promise.all([this.loadCatalog(), this.loadPosts()]).finally(() => wx.stopPullDownRefresh())
  },

  onQueryInput(event) {
    this.setData({ query: event.detail.value })
  },

  onProvinceChange(event) {
    this.setData({ provinceIndex: Number(event.detail.value) })
    this.loadPosts()
  },

  async loadCatalog() {
    try {
      const provinces = await request({ url: '/api/mini/catalog/provinces' })
      this.setData({ provinceOptions: ['全部省份', ...provinces] })
    } catch (error) {
      wx.showToast({ title: '省份列表加载失败', icon: 'none' })
    }
  },

  async loadPosts() {
    if (this.data.loading) return
    this.setData({ loading: true })
    const selectedProvince = this.data.provinceOptions[this.data.provinceIndex]
    try {
      const posts = await request({
        url: '/api/mini/posts',
        data: {
          q: this.data.query.trim(),
          province: selectedProvince === '全部省份' ? '' : selectedProvince
        }
      })
      const provinceCount = new Set(posts.map((item) => item.province).filter(Boolean)).size
      this.setData({
        provinceCount,
        posts: posts.map((item) => ({
          ...item,
          photoUrl: resolveAsset(item.photoPath),
          authorInitial: (item.author && item.author.nickname ? item.author.nickname : '旅').slice(0, 1),
          dateLabel: item.travelDate ? item.travelDate.replace(/-/g, '.') : ''
        }))
      })
    } catch (error) {
      wx.showToast({ title: error.message || '内容加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  openDetail(event) {
    wx.navigateTo({ url: `/pages/post-detail/post-detail?id=${event.currentTarget.dataset.id}` })
  },

  goPublish() {
    wx.switchTab({ url: '/pages/publish/publish' })
  }
})
