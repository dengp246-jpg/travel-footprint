const { getToken, request, upload } = require('../../utils/request')

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
    longitude: ''
  }
}

Page({
  data: {
    submitting: false,
    provinceIndex: 0,
    provinceOptions: [],
    categoryIndex: 0,
    categoryOptions: [],
    processingPhoto: false,
    photoPath: '',
    photoName: '',
    form: emptyForm()
  },

  onLoad() {
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
      const [provinces, categories] = await Promise.all([
        request({ url: '/api/mini/catalog/provinces' }),
        request({ url: '/api/mini/catalog/categories' })
      ])
      this.setData({
        provinceOptions: provinces,
        categoryOptions: categories,
        'form.province': this.data.form.province || provinces[0] || '',
        'form.category': this.data.form.category || categories[0] || ''
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

    this.setData({ submitting: true })
    try {
      const formData = { ...this.data.form }
      if (this.data.photoPath) {
        await upload({ url: '/api/mini/posts', filePath: this.data.photoPath, formData })
      } else {
        await request({
          url: '/api/mini/posts',
          method: 'POST',
          header: { 'Content-Type': 'application/x-www-form-urlencoded' },
          data: formData
        })
      }
      wx.showToast({ title: '足迹已发布', icon: 'success' })
      const form = emptyForm()
      form.province = this.data.provinceOptions[this.data.provinceIndex] || ''
      form.category = this.data.categoryOptions[this.data.categoryIndex] || ''
      this.setData({ photoPath: '', photoName: '', form })
      setTimeout(() => wx.switchTab({ url: '/pages/feed/feed' }), 500)
    } catch (error) {
      wx.showToast({ title: error.message || '发布失败', icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  }
})
