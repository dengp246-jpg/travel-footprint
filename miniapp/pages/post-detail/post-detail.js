const { request, resolveAsset } = require('../../utils/request')

Page({
  data: {
    post: null
  },

  onLoad(options) {
    if (!options.id) {
      wx.showToast({
        title: '缺少足迹编号',
        icon: 'none'
      })
      return
    }
    this.loadPost(options.id)
  },

  async loadPost(id) {
    try {
      const post = await request({
        url: `/api/mini/posts/${id}`
      })
      this.setData({
        post: {
          ...post,
          photoUrl: resolveAsset(post.photoPath)
        }
      })
    } catch (error) {
      wx.showToast({
        title: error.message || '详情加载失败',
        icon: 'none'
      })
    }
  }
})
