const { request, resolveAsset } = require('../../utils/request')

Page({
  data: {
    loading: false,
    query: '',
    provinceIndex: 0,
    provinceOptions: ['全部省份'],
    posts: []
  },

  onLoad() {
    this.loadCatalog()
    this.loadPosts()
  },

  onShow() {
    this.loadPosts()
  },

  onQueryInput(event) {
    this.setData({
      query: event.detail.value
    })
  },

  onProvinceChange(event) {
    this.setData({
      provinceIndex: Number(event.detail.value)
    })
  },

  async loadCatalog() {
    try {
      const provinces = await request({
        url: '/api/mini/catalog/provinces'
      })
      this.setData({
        provinceOptions: ['全部省份', ...provinces]
      })
    } catch (error) {
      wx.showToast({
        title: '省份列表加载失败',
        icon: 'none'
      })
    }
  },

  async loadPosts() {
    this.setData({ loading: true })
    const selectedProvince = this.data.provinceOptions[this.data.provinceIndex]
    try {
      const posts = await request({
        url: '/api/mini/posts',
        data: {
          q: this.data.query,
          province: selectedProvince === '全部省份' ? '' : selectedProvince
        }
      })
      this.setData({
        posts: posts.map((item) => ({
          ...item,
          photoUrl: resolveAsset(item.photoPath)
        }))
      })
    } catch (error) {
      wx.showToast({
        title: error.message || '内容加载失败',
        icon: 'none'
      })
    } finally {
      this.setData({ loading: false })
    }
  },

  openDetail(event) {
    wx.navigateTo({
      url: `/pages/post-detail/post-detail?id=${event.currentTarget.dataset.id}`
    })
  }
})
