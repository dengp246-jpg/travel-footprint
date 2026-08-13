const { getToken, request, resolveAsset } = require('../../utils/request')

Page({
  data: {
    postId: '',
    loading: false,
    interacting: false,
    post: null
  },

  onLoad(options) {
    if (!options.id) {
      wx.showToast({ title: '缺少足迹编号', icon: 'none' })
      return
    }
    this.setData({ postId: options.id })
    this.loadPost()
  },

  async loadPost() {
    if (this.data.loading || !this.data.postId) return
    this.setData({ loading: true })
    try {
      const post = await request({ url: `/api/mini/posts/${this.data.postId}` })
      this.setData({
        post: {
          ...post,
          photoUrl: resolveAsset(post.photoPath),
          authorInitial: (post.author && post.author.nickname ? post.author.nickname : '旅').slice(0, 1),
          dateLabel: post.travelDate ? post.travelDate.replace(/-/g, '.') : ''
        }
      })
    } catch (error) {
      wx.showToast({ title: error.message || '详情加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  async toggleAction(type) {
    if (!getToken()) {
      wx.showModal({
        title: '登录后参与互动',
        content: type === 'like' ? '登录后可以为喜欢的旅途点赞。' : '登录后可以收藏想再次查看的足迹。',
        confirmText: '去登录',
        success: (res) => {
          if (res.confirm) wx.navigateTo({ url: '/pages/login/login' })
        }
      })
      return
    }
    if (this.data.interacting) return
    this.setData({ interacting: true })
    try {
      const result = await request({
        url: `/api/mini/posts/${this.data.postId}/${type}`,
        method: 'POST'
      })
      const flag = type === 'like' ? 'liked' : 'favorited'
      const count = type === 'like' ? 'likeCount' : 'favoriteCount'
      this.setData({ [`post.${flag}`]: result.active, [`post.${count}`]: result.count })
      wx.showToast({ title: result.active ? (type === 'like' ? '已点赞' : '已收藏') : '已取消', icon: 'none' })
    } catch (error) {
      wx.showToast({ title: error.message || '操作失败', icon: 'none' })
    } finally {
      this.setData({ interacting: false })
    }
  },

  toggleLike() {
    this.toggleAction('like')
  },

  toggleFavorite() {
    this.toggleAction('favorite')
  }
})
