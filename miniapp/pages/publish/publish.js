const { getToken, request, upload } = require('../../utils/request')

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
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sizeType: ['compressed'],
      success: (res) => {
        const file = res.tempFiles[0]
        if (file.size && file.size > 10 * 1024 * 1024) {
          wx.showToast({ title: '图片不能超过 10MB', icon: 'none' })
          return
        }
        this.setData({
          photoPath: file.tempFilePath,
          photoName: file.size ? `${Math.max(1, Math.round(file.size / 1024))}KB` : '已选择'
        })
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
