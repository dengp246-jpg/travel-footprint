const { getToken, request, upload, resolveAsset } = require('../../utils/request')
const { DEFAULT_MAX_VIDEO_BYTES, chooseVideo, formatFileSize } = require('../../utils/video')

Page({
  data: {
    postId: '',
    loading: false,
    interacting: false,
    updatingVideo: false,
    updatingPrivacy: false,
    visibilityIndex: 0,
    visibilityOptions: [
      { value: 'PUBLIC', label: '所有人可查看' },
      { value: 'FOLLOWERS', label: '仅关注者可查看' },
      { value: 'PRIVATE', label: '仅自己可查看' }
    ],
    privacyTitle: '所有人可查看',
    privacyText: '',
    maxVideoBytes: DEFAULT_MAX_VIDEO_BYTES,
    maxVideoLabel: formatFileSize(DEFAULT_MAX_VIDEO_BYTES),
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
      const [post, uploadLimits] = await Promise.all([
        request({ url: `/api/mini/posts/${this.data.postId}` }),
        request({ url: '/api/mini/catalog/upload-limits' })
      ])
      const maxVideoBytes = Number(uploadLimits.maxVideoSizeBytes) || DEFAULT_MAX_VIDEO_BYTES
      const decoratedPost = this.decoratePost(post)
      const visibilityIndex = Math.max(0, this.data.visibilityOptions.findIndex((item) => item.value === decoratedPost.visibility))
      this.setData({
        post: decoratedPost,
        visibilityIndex,
        maxVideoBytes,
        maxVideoLabel: formatFileSize(maxVideoBytes)
      })
      this.updatePrivacyPreview(decoratedPost.visibility, decoratedPost.approximateLocation)
    } catch (error) {
      wx.showToast({ title: error.message || '详情加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  decoratePost(post) {
    return {
      ...post,
      photoUrl: resolveAsset(post.photoPath),
      videoUrl: resolveAsset(post.videoPath),
      authorInitial: (post.author && post.author.nickname ? post.author.nickname : '旅').slice(0, 1),
      dateLabel: post.travelDate ? post.travelDate.replace(/-/g, '.') : ''
    }
  },

  async replaceVideo() {
    if (this.data.updatingVideo || !this.data.post || !this.data.post.owned) return
    try {
      const selected = await chooseVideo(this.data.maxVideoBytes)
      if (!selected) return
      this.setData({ updatingVideo: true })
      wx.showLoading({ title: '正在上传视频', mask: true })
      const post = await upload({
        url: `/api/mini/posts/${this.data.postId}/video`,
        filePath: selected.path,
        name: 'video',
        timeout: 60000
      })
      this.setData({ post: this.decoratePost(post) })
      wx.showToast({ title: '视频已更新', icon: 'success' })
    } catch (error) {
      wx.showToast({ title: error.message || '视频上传失败', icon: 'none', duration: 3000 })
    } finally {
      wx.hideLoading()
      this.setData({ updatingVideo: false })
    }
  },

  removeVideo() {
    if (this.data.updatingVideo || !this.data.post || !this.data.post.videoPath) return
    wx.showModal({
      title: '删除旅行视频？',
      content: '删除后无法恢复，但不会影响足迹文字和照片。',
      confirmColor: '#a44535',
      success: async (result) => {
        if (!result.confirm) return
        this.setData({ updatingVideo: true })
        try {
          const post = await request({
            url: `/api/mini/posts/${this.data.postId}/video`,
            method: 'DELETE'
          })
          this.setData({ post: this.decoratePost(post) })
          wx.showToast({ title: '视频已删除', icon: 'success' })
        } catch (error) {
          wx.showToast({ title: error.message || '删除失败', icon: 'none' })
        } finally {
          this.setData({ updatingVideo: false })
        }
      }
    })
  },

  onPrivacyVisibilityChange(event) {
    const visibilityIndex = Number(event.detail.value)
    const visibility = this.data.visibilityOptions[visibilityIndex].value
    this.setData({ visibilityIndex, 'post.visibility': visibility })
    this.updatePrivacyPreview(visibility, this.data.post.approximateLocation)
  },

  onPrivacyApproximateChange(event) {
    const approximateLocation = Boolean(event.detail.value)
    this.setData({ 'post.approximateLocation': approximateLocation })
    this.updatePrivacyPreview(this.data.post.visibility, approximateLocation)
  },

  updatePrivacyPreview(visibility, approximateLocation) {
    const copy = {
      PUBLIC: ['所有人可查看', '足迹会出现在公共动态与公共地图中。'],
      FOLLOWERS: ['仅关注者可查看', '只有关注你的人能看到足迹内容与地图信息。'],
      PRIVATE: ['仅自己可查看', '足迹只会保存在你的个人空间中。']
    }[visibility] || ['所有人可查看', '足迹会出现在公共动态与公共地图中。']
    this.setData({
      privacyTitle: copy[0],
      privacyText: copy[1] + (approximateLocation
        ? ' 对其他可见用户只展示省份范围。'
        : ' 地图将按权限展示具体点位。')
    })
  },

  async savePrivacy() {
    if (this.data.updatingPrivacy || !this.data.post || !this.data.post.owned) return
    this.setData({ updatingPrivacy: true })
    try {
      const post = await request({
        url: `/api/mini/posts/${this.data.postId}/privacy`,
        method: 'POST',
        data: {
          visibility: this.data.post.visibility,
          approximateLocation: this.data.post.approximateLocation
        }
      })
      const decoratedPost = this.decoratePost(post)
      this.setData({ post: decoratedPost })
      this.updatePrivacyPreview(decoratedPost.visibility, decoratedPost.approximateLocation)
      wx.showToast({ title: '隐私设置已保存', icon: 'success' })
    } catch (error) {
      wx.showToast({ title: error.message || '隐私设置保存失败', icon: 'none' })
    } finally {
      this.setData({ updatingPrivacy: false })
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
