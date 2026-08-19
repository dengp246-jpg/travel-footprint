const DEFAULT_MAX_VIDEO_BYTES = 20 * 1024 * 1024

function formatFileSize(bytes) {
  if (bytes >= 1024 * 1024) {
    const megabytes = bytes / 1024 / 1024
    return `${Number.isInteger(megabytes) ? megabytes : megabytes.toFixed(2)}MB`
  }
  return `${Math.max(1, Math.ceil(bytes / 1024))}KB`
}

function chooseVideo(maxBytes = DEFAULT_MAX_VIDEO_BYTES) {
  return new Promise((resolve, reject) => {
    wx.chooseMedia({
      count: 1,
      mediaType: ['video'],
      sourceType: ['album', 'camera'],
      maxDuration: 60,
      success: (res) => {
        const file = res.tempFiles && res.tempFiles[0]
        if (!file || !file.tempFilePath) {
          reject(new Error('没有读取到所选视频'))
          return
        }
        if (file.size > maxBytes) {
          reject(new Error(`视频不能超过${formatFileSize(maxBytes)}，请裁剪后重试`))
          return
        }
        resolve({
          path: file.tempFilePath,
          size: file.size || 0,
          duration: Number(file.duration) || 0,
          width: Number(file.width) || 0,
          height: Number(file.height) || 0,
          label: [
            formatFileSize(file.size || 0),
            Number(file.duration) > 0 ? `${Math.round(file.duration)}秒` : '',
            Number(file.width) > 0 && Number(file.height) > 0 ? `${file.width}×${file.height}` : ''
          ].filter(Boolean).join(' · ')
        })
      },
      fail: (error) => {
        if (error && error.errMsg && error.errMsg.includes('cancel')) {
          resolve(null)
          return
        }
        reject(new Error('视频选择失败，请检查相册或相机权限'))
      }
    })
  })
}

module.exports = {
  DEFAULT_MAX_VIDEO_BYTES,
  formatFileSize,
  chooseVideo
}
