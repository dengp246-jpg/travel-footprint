const { getToken, request, upload } = require('../../utils/request')
const { DEFAULT_MAX_VIDEO_BYTES, chooseVideo, formatFileSize } = require('../../utils/video')

const MAX_SOURCE_IMAGE_BYTES = 25 * 1024 * 1024
const TARGET_IMAGE_BYTES = 1800 * 1024

function fileSize(filePath) {
  return new Promise((resolve, reject) => {
    wx.getFileSystemManager().getFileInfo({
      filePath,
      success: (result) => resolve(result.size || 0),
      fail: reject
    })
  })
}

function compressImage(filePath, quality) {
  return new Promise((resolve, reject) => {
    wx.compressImage({
      src: filePath,
      quality,
      compressedWidth: 1920,
      compressedHeight: 1920,
      success: (result) => resolve(result.tempFilePath),
      fail: reject
    })
  })
}

async function optimizeImage(file) {
  let currentPath = file.tempFilePath
  let currentSize = file.size || await fileSize(currentPath)
  if (currentSize > MAX_SOURCE_IMAGE_BYTES) {
    throw new Error('原图超过25MB，请先在相册中裁剪后重试')
  }
  if (currentSize <= TARGET_IMAGE_BYTES) {
    return { path: currentPath, size: currentSize }
  }
  for (const quality of [82, 70, 58, 46]) {
    currentPath = await compressImage(currentPath, quality)
    currentSize = await fileSize(currentPath)
    if (currentSize <= TARGET_IMAGE_BYTES) {
      return { path: currentPath, size: currentSize }
    }
  }
  throw new Error('图片压缩后仍超过2MB，请裁剪图片后重试')
}

function today() {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60000
  return new Date(now.getTime() - offset).toISOString().slice(0, 10)
}

function emptyForm() {
  return {
    title: '',
    location: '',
    province: '',
    category: '',
    tags: '',
    travelDate: today(),
    content: '',
    latitude: '',
    longitude: '',
    visibility: 'PUBLIC',
    approximateLocation: false
  }
}

Page({
  data: {
    submitting: false,
    uploadProgress: 0,
    uploadStatus: '',
    uploadFailed: false,
    provinceIndex: 0,
    provinceOptions: [],
    categoryIndex: 0,
    categoryOptions: [],
    processingPhoto: false,
    photoPath: '',
    photoName: '',
    videoPath: '',
    videoName: '',
    maxVideoBytes: DEFAULT_MAX_VIDEO_BYTES,
    maxVideoLabel: formatFileSize(DEFAULT_MAX_VIDEO_BYTES),
    visibilityIndex: 0,
    visibilityOptions: [
      { value: 'PUBLIC', label: '所有人可查看' },
      { value: 'FOLLOWERS', label: '仅关注者可查看' },
      { value: 'PRIVATE', label: '仅自己可查看' }
    ],
    privacyTitle: '所有人可查看',
    privacyText: '足迹会出现在公共动态中，地图展示具体点位。',
    arrivalPrefill: false,
    form: emptyForm()
  },

  onLoad(options = {}) {
    const latitude = Number(options.latitude)
    const longitude = Number(options.longitude)
    const hasCoordinates = Number.isFinite(latitude) && Number.isFinite(longitude)
      && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180
    if (options.arrival === '1' && hasCoordinates) {
      const travelDate = /^\d{4}-\d{2}-\d{2}$/.test(options.date || '') ? options.date : today()
      this.setData({
        arrivalPrefill: true,
        privacyText: '足迹会出现在公共动态与公共地图中。 对其他可见用户只展示省份范围，不显示具体点位。',
        form: {
          ...emptyForm(),
          location: String(options.location || '当前位置').slice(0, 100),
          province: String(options.province || ''),
          travelDate,
          latitude: String(latitude),
          longitude: String(longitude),
          approximateLocation: true
        }
      })
    }
    this.loadCatalog()
  },

  onShow() {
    if (!getToken()) {
      wx.showModal({
        title: '登录后记录旅程',
        content: '你的足迹、照片和旅行报告都会保存到个人账号。',
        confirmText: '去登录',
        showCancel: false,
        success: () => wx.navigateTo({ url: '/pages/login/login' })
      })
    }
  },

  async loadCatalog() {
    try {
      const [provinces, categories, uploadLimits] = await Promise.all([
        request({ url: '/api/mini/catalog/provinces' }),
        request({ url: '/api/mini/catalog/categories' }),
        request({ url: '/api/mini/catalog/upload-limits' })
      ])
      const maxVideoBytes = Number(uploadLimits.maxVideoSizeBytes) || DEFAULT_MAX_VIDEO_BYTES
      const requestedProvince = this.data.form.province
      const requestedCategory = this.data.form.category
      const provinceIndex = Math.max(0, provinces.indexOf(requestedProvince))
      const categoryIndex = Math.max(0, categories.indexOf(requestedCategory))
      this.setData({
        provinceOptions: provinces,
        categoryOptions: categories,
        provinceIndex,
        categoryIndex,
        maxVideoBytes,
        maxVideoLabel: formatFileSize(maxVideoBytes),
        'form.province': provinces[provinceIndex] || '',
        'form.category': categories[categoryIndex] || ''
      })
    } catch (error) {
      wx.showToast({ title: '发布选项加载失败', icon: 'none' })
    }
  },

  onFieldChange(event) {
    const field = event.currentTarget.dataset.field
    this.setData({ [`form.${field}`]: event.detail.value })
  },

  onProvinceChange(event) {
    const provinceIndex = Number(event.detail.value)
    this.setData({
      provinceIndex,
      'form.province': this.data.provinceOptions[provinceIndex]
    })
  },

  onCategoryChange(event) {
    const categoryIndex = Number(event.detail.value)
    this.setData({
      categoryIndex,
      'form.category': this.data.categoryOptions[categoryIndex]
    })
  },

  onDateChange(event) {
    this.setData({ 'form.travelDate': event.detail.value })
  },

  onVisibilityChange(event) {
    const visibilityIndex = Number(event.detail.value)
    const visibility = this.data.visibilityOptions[visibilityIndex].value
    this.setData({ visibilityIndex, 'form.visibility': visibility })
    this.updatePrivacyPreview(visibility, this.data.form.approximateLocation)
  },

  onApproximateChange(event) {
    const approximateLocation = Boolean(event.detail.value)
    this.setData({ 'form.approximateLocation': approximateLocation })
    this.updatePrivacyPreview(this.data.form.visibility, approximateLocation)
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
        ? ' 对其他可见用户只展示省份范围，不显示具体点位。'
        : ' 地图将按权限展示你填写的具体点位。')
    })
  },

  chooseLocation() {
    wx.chooseLocation({
      success: (res) => {
        const location = res.name || res.address || ''
        this.setData({
          'form.location': location,
          'form.latitude': String(res.latitude),
          'form.longitude': String(res.longitude)
        })
      },
      fail: (error) => {
        if (error.errMsg && error.errMsg.includes('auth deny')) {
          wx.showToast({ title: '可在设置中允许位置权限，或手动填写地点', icon: 'none' })
        }
      }
    })
  },

  chooseImage() {
    if (this.data.processingPhoto) return
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sizeType: ['compressed'],
      success: async (res) => {
        const file = res.tempFiles[0]
        this.setData({ processingPhoto: true, photoName: '正在优化图片…' })
        wx.showLoading({ title: '正在优化图片', mask: true })
        try {
          const optimized = await optimizeImage(file)
          this.setData({
            photoPath: optimized.path,
            photoName: `${Math.max(1, Math.round(optimized.size / 1024))}KB · 已优化`
          })
        } catch (error) {
          this.setData({ photoPath: '', photoName: '' })
          wx.showToast({ title: error.message || '图片处理失败', icon: 'none', duration: 3000 })
        } finally {
          this.setData({ processingPhoto: false })
          wx.hideLoading()
        }
      }
    })
  },

  removeImage() {
    this.setData({ photoPath: '', photoName: '' })
  },

  async chooseVideo() {
    try {
      const selected = await chooseVideo(this.data.maxVideoBytes)
      if (!selected) return
      this.setData({ videoPath: selected.path, videoName: selected.label })
    } catch (error) {
      wx.showToast({ title: error.message || '视频选择失败', icon: 'none', duration: 3000 })
    }
  },

  removeVideo() {
    this.setData({ videoPath: '', videoName: '' })
  },

  updateUploadProgress(stage, progress, start = 0, span = 100) {
    const normalized = Math.max(0, Math.min(100, Number(progress) || 0))
    this.setData({
      uploadProgress: Math.min(99, Math.round(start + normalized * span / 100)),
      uploadStatus: stage
    })
  },

  cancelUpload() {
    if (this.currentUploadTask && this.currentUploadTask.abort) {
      this.currentUploadTask.abort()
    }
  },

  validate() {
    const { title, location, province, category, travelDate, content } = this.data.form
    if (![title, location, province, category, travelDate, content].every((value) => String(value || '').trim())) {
      wx.showToast({ title: '请填写完整的旅行信息', icon: 'none' })
      return false
    }
    if (title.trim().length > 100 || location.trim().length > 100 || content.trim().length > 4000) {
      wx.showToast({ title: '部分内容超过长度限制', icon: 'none' })
      return false
    }
    return true
  },

  async submitPost() {
    if (!getToken()) {
      wx.navigateTo({ url: '/pages/login/login' })
      return
    }
    if (this.data.processingPhoto) {
      wx.showToast({ title: '请等待图片优化完成', icon: 'none' })
      return
    }
    if (this.data.submitting || !this.validate()) return

    this.setData({
      submitting: true,
      uploadProgress: 0,
      uploadStatus: '正在建立安全上传连接…',
      uploadFailed: false
    })
    if (wx.enableAlertBeforeUnload) {
      wx.enableAlertBeforeUnload({ message: '足迹仍在上传，离开会中断本次提交。' })
    }
    try {
      const formData = {
        ...this.data.form,
        approximateLocation: this.data.form.approximateLocation ? 'true' : 'false'
      }
      let createdPost
      if (this.data.photoPath) {
        createdPost = await upload({
          url: '/api/mini/posts',
          filePath: this.data.photoPath,
          name: 'photo',
          formData,
          onTask: (task) => { this.currentUploadTask = task },
          onProgress: ({ progress }) => this.updateUploadProgress(
            this.data.videoPath ? '正在上传封面照片…' : '正在上传照片并保存足迹…',
            progress,
            0,
            this.data.videoPath ? 50 : 100
          )
        })
      } else if (this.data.videoPath) {
        createdPost = await upload({
          url: '/api/mini/posts',
          filePath: this.data.videoPath,
          name: 'video',
          formData,
          timeout: 60000,
          onTask: (task) => { this.currentUploadTask = task },
          onProgress: ({ progress }) => this.updateUploadProgress('正在上传视频并保存足迹…', progress)
        })
      } else {
        this.setData({ uploadProgress: 40, uploadStatus: '正在保存足迹…' })
        createdPost = await request({
          url: '/api/mini/posts',
          method: 'POST',
          header: { 'Content-Type': 'application/x-www-form-urlencoded' },
          data: formData
        })
      }
      if (this.data.photoPath && this.data.videoPath) {
        try {
          await upload({
            url: `/api/mini/posts/${createdPost.id}/video`,
            filePath: this.data.videoPath,
            name: 'video',
            timeout: 60000,
            onTask: (task) => { this.currentUploadTask = task },
            onProgress: ({ progress }) => this.updateUploadProgress('足迹已保存，正在上传视频…', progress, 50, 50)
          })
        } catch (error) {
          wx.showModal({
            title: '足迹已保存，视频上传失败',
            content: `${error.message || '请稍后在详情页重新上传视频。'}\n可在“我的足迹”中打开并重试。`,
            showCancel: false
          })
        }
      }
      this.currentUploadTask = null
      this.setData({ uploadProgress: 100, uploadStatus: '上传完成，正在打开足迹…' })
      wx.showToast({ title: '足迹已发布', icon: 'success' })
      const form = emptyForm()
      form.province = this.data.provinceOptions[this.data.provinceIndex] || ''
      form.category = this.data.categoryOptions[this.data.categoryIndex] || ''
      this.setData({
        photoPath: '',
        photoName: '',
        videoPath: '',
        videoName: '',
        visibilityIndex: 0,
        arrivalPrefill: false,
        privacyTitle: '所有人可查看',
        privacyText: '足迹会出现在公共动态中，地图展示具体点位。',
        form
      })
      setTimeout(() => wx.switchTab({ url: '/pages/feed/feed' }), 500)
    } catch (error) {
      this.currentUploadTask = null
      this.setData({
        uploadFailed: true,
        uploadStatus: error.errMsg && error.errMsg.includes('abort')
          ? '上传已取消，可直接重新提交。'
          : '上传失败，表单内容已保留，可直接重试。'
      })
      wx.showToast({ title: error.message || '发布失败', icon: 'none' })
    } finally {
      if (wx.disableAlertBeforeUnload) {
        wx.disableAlertBeforeUnload()
      }
      this.setData({ submitting: false })
    }
  }
})
