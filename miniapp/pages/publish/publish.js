const { getToken, request, upload } = require('../../utils/request')

Page({
  data: {
    submitting: false,
    provinceIndex: 0,
    provinceOptions: [],
    photoPath: '',
    photoName: '',
    form: {
      title: '',
      location: '',
      province: '',
      category: '',
      tags: '',
      travelDate: '',
      content: ''
    }
  },

  onLoad() {
    this.loadCatalog()
  },

  onShow() {
    if (!getToken()) {
      wx.showToast({
        title: '请先登录后再发布',
        icon: 'none'
      })
      wx.navigateTo({
        url: '/pages/login/login'
      })
    }
  },

  async loadCatalog() {
    try {
      const provinces = await request({
        url: '/api/mini/catalog/provinces'
      })
      this.setData({
        provinceOptions: provinces,
        'form.province': provinces[0] || ''
      })
    } catch (error) {
      wx.showToast({
        title: '省份列表加载失败',
        icon: 'none'
      })
    }
  },

  onFieldChange(event) {
    const field = event.currentTarget.dataset.field
    this.setData({
      [`form.${field}`]: event.detail.value
    })
  },

  onProvinceChange(event) {
    const provinceIndex = Number(event.detail.value)
    this.setData({
      provinceIndex,
      'form.province': this.data.provinceOptions[provinceIndex]
    })
  },

  onDateChange(event) {
    this.setData({
      'form.travelDate': event.detail.value
    })
  },

  chooseImage() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      success: (res) => {
        const file = res.tempFiles[0]
        this.setData({
          photoPath: file.tempFilePath,
          photoName: file.size ? `${Math.round(file.size / 1024)}KB 图片` : '已选择图片'
        })
      }
    })
  },

  async submitPost() {
    if (!getToken()) {
      wx.navigateTo({
        url: '/pages/login/login'
      })
      return
    }
    if (this.data.submitting) {
      return
    }

    this.setData({ submitting: true })
    try {
      const formData = { ...this.data.form }
      if (this.data.photoPath) {
        await upload({
          url: '/api/mini/posts',
          filePath: this.data.photoPath,
          formData
        })
      } else {
        await request({
          url: '/api/mini/posts',
          method: 'POST',
          header: {
            'Content-Type': 'application/x-www-form-urlencoded'
          },
          data: formData
        })
      }
      wx.showToast({
        title: '发布成功',
        icon: 'success'
      })
      this.setData({
        photoPath: '',
        photoName: '',
        form: {
          title: '',
          location: '',
          province: this.data.provinceOptions[this.data.provinceIndex] || '',
          category: '',
          tags: '',
          travelDate: '',
          content: ''
        }
      })
      wx.switchTab({
        url: '/pages/feed/feed'
      })
    } catch (error) {
      wx.showToast({
        title: error.message || '发布失败',
        icon: 'none'
      })
    } finally {
      this.setData({ submitting: false })
    }
  }
})
