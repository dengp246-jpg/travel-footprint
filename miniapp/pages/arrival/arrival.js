const { getBaseUrl, getToken, getUser, request } = require('../../utils/request')

const REMINDER_COOLDOWN_MS = 24 * 60 * 60 * 1000
const DWELL_TIME_MS = 2 * 60 * 1000
const NEW_PLACE_DISTANCE_METERS = 500
const POLL_INTERVAL_MS = 30 * 1000

function storageKey(name) {
  const user = getUser() || {}
  const identity = user.id || user.username || 'user'
  return `arrivalReminder.${getBaseUrl()}.${identity}.${name}`
}

function readJson(name) {
  try {
    return wx.getStorageSync(storageKey(name)) || null
  } catch (error) {
    return null
  }
}

function writeJson(name, value) {
  try {
    wx.setStorageSync(storageKey(name), value)
  } catch (error) {
    // The foreground reminder remains usable for the current page.
  }
}

function removeValue(name) {
  try {
    wx.removeStorageSync(storageKey(name))
  } catch (error) {
    // Persistent state is optional.
  }
}

function distanceMeters(first, second) {
  const radius = 6371008.8
  const toRadians = (value) => value * Math.PI / 180
  const latitudeDelta = toRadians(second.latitude - first.latitude)
  const longitudeDelta = toRadians(second.longitude - first.longitude)
  const firstLatitude = toRadians(first.latitude)
  const secondLatitude = toRadians(second.latitude)
  const value = Math.sin(latitudeDelta / 2) ** 2
    + Math.cos(firstLatitude) * Math.cos(secondLatitude) * Math.sin(longitudeDelta / 2) ** 2
  return radius * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value))
}

function today() {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60000
  return new Date(now.getTime() - offset).toISOString().slice(0, 10)
}

function ensurePrivacyAuthorized() {
  if (typeof wx.getPrivacySetting !== 'function') return Promise.resolve()
  return new Promise((resolve, reject) => {
    wx.getPrivacySetting({
      success: (result) => {
        if (!result.needAuthorization) {
          resolve()
          return
        }
        if (typeof wx.requirePrivacyAuthorize !== 'function') {
          reject({ errMsg: 'privacy authorization required' })
          return
        }
        wx.requirePrivacyAuthorize({ success: resolve, fail: reject })
      },
      fail: reject
    })
  })
}

function requestLocation(isHighAccuracy) {
  return new Promise((resolve, reject) => {
    wx.getLocation({
      type: 'gcj02',
      isHighAccuracy,
      highAccuracyExpireTime: isHighAccuracy ? 8000 : 3000,
      success: resolve,
      fail: reject
    })
  })
}

async function getLocation() {
  await ensurePrivacyAuthorized()
  try {
    return await requestLocation(true)
  } catch (error) {
    const message = String(error.errMsg || '').toLowerCase()
    if (!message.includes('timeout')) throw error
    return requestLocation(false)
  }
}

function locationFailure(error) {
  const rawMessage = String(error && (error.errMsg || error.message) || 'unknown error')
  const message = rawMessage.toLowerCase()
  if (message.includes('privacy') || message.includes('privateinfo') || message.includes('requiredprivateinfos')) {
    return {
      type: 'privacy',
      title: '位置隐私接口尚未授权',
      detail: '请在微信公众平台完成用户隐私保护指引，并声明 wx.getLocation 与 wx.chooseLocation 后重新提交开发版。'
    }
  }
  if (message.includes('auth deny') || message.includes('auth denied')
      || message.includes('permission denied') || message.includes('authorize:fail')) {
    return {
      type: 'permission',
      title: '位置权限未开启',
      detail: '请在小程序右上角“··· → 设置”以及手机系统的微信权限中允许位置信息。'
    }
  }
  if (message.includes('timeout')) {
    return {
      type: 'timeout',
      title: '定位超时',
      detail: '请确认手机定位服务已开启，移动到网络或 GPS 信号较好的位置后重新尝试。'
    }
  }
  return {
    type: 'unavailable',
    title: '暂时无法读取位置',
    detail: `微信未能提供位置，请检查权限和网络后重试。（${rawMessage.slice(0, 120)}）`
  }
}

Page({
  data: {
    enabled: false,
    checking: false,
    status: '到访提醒尚未开启',
    statusDetail: '开启后仅在本页面保持显示时读取位置。',
    errorDetail: '',
    promptVisible: false,
    place: '当前位置',
    promptDetail: '',
    accuracyLabel: '',
    currentPosition: null,
    currentMatch: null
  },

  onLoad() {
    this.setData({ enabled: Boolean(readJson('enabled')) })
  },

  onShow() {
    if (!getToken()) {
      wx.showModal({
        title: '登录后使用到访提醒',
        content: '定位结果与足迹预填需要关联你的个人账号。',
        confirmText: '去登录',
        showCancel: false,
        success: () => wx.navigateTo({ url: '/pages/login/login' })
      })
      return
    }
    if (this.data.enabled) this.startMonitoring(false)
  },

  onHide() {
    this.stopMonitoring()
    removeValue('candidate')
  },

  onUnload() {
    this.stopMonitoring()
    removeValue('candidate')
  },

  handleReminderTap() {
    console.info('[arrival-reminder] primary control tapped', {
      enabled: this.data.enabled,
      checking: this.data.checking
    })
    if (this.data.enabled) {
      this.disableReminder()
      return
    }
    this.setData({
      status: '已收到开启请求',
      statusDetail: '正在请求微信位置权限…',
      errorDetail: ''
    })
    this.startMonitoring(true)
  },

  startMonitoring(interactive) {
    this.stopMonitoring()
    writeJson('enabled', true)
    this.setData({
      enabled: true,
      status: '到访提醒已开启',
      statusDetail: '正在读取当前位置…',
      errorDetail: ''
    })
    this.checkPosition(interactive)
    this.pollTimer = setInterval(() => this.checkPosition(false), POLL_INTERVAL_MS)
  },

  stopMonitoring() {
    if (this.pollTimer) clearInterval(this.pollTimer)
    this.pollTimer = null
  },

  disableReminder(statusDetail = '不会继续读取当前位置。', status = '到访提醒已关闭', errorDetail = '') {
    this.stopMonitoring()
    writeJson('enabled', false)
    removeValue('candidate')
    this.setData({
      enabled: false,
      checking: false,
      promptVisible: false,
      status,
      statusDetail,
      errorDetail
    })
  },

  async checkPosition(interactive) {
    if (this.data.checking || this.data.promptVisible) return
    this.setData({ checking: true })
    try {
      const result = await getLocation()
      const position = {
        latitude: Number(result.latitude),
        longitude: Number(result.longitude),
        accuracy: Math.max(1, Number(result.accuracy) || 1)
      }
      if (!Number.isFinite(position.latitude) || !Number.isFinite(position.longitude)) return
      if (position.accuracy > 1000) {
        this.setData({ statusDetail: '定位精度较低，正在等待下一次定位…' })
        return
      }
      this.scheduleCandidate(position, interactive)
    } catch (error) {
      console.error('[arrival-reminder] location failed', error)
      const failure = locationFailure(error)
      this.disableReminder(failure.detail, failure.title, failure.detail)
      const canOpenSettings = failure.type === 'permission'
      wx.showModal({
        title: failure.title,
        content: failure.detail,
        showCancel: canOpenSettings,
        confirmText: canOpenSettings ? '检查权限' : '我知道了',
        cancelText: '稍后处理',
        success: (result) => {
          if (canOpenSettings && result.confirm) this.openSettings()
        }
      })
    } finally {
      this.setData({ checking: false })
    }
  },

  retryLocation() {
    this.startMonitoring(true)
  },

  scheduleCandidate(position, interactive) {
    const lastPrompt = readJson('lastPrompt')
    if (lastPrompt && Date.now() - lastPrompt.promptedAt < REMINDER_COOLDOWN_MS
        && distanceMeters(lastPrompt, position) < NEW_PLACE_DISTANCE_METERS) {
      this.setData({ statusDetail: '当前地点今天已经提醒过。' })
      return
    }
    const storedCandidate = readJson('candidate')
    const sameCandidate = storedCandidate
      && distanceMeters(storedCandidate, position) < NEW_PLACE_DISTANCE_METERS
    const candidate = sameCandidate ? storedCandidate : {
      latitude: position.latitude,
      longitude: position.longitude,
      accuracy: position.accuracy,
      startedAt: Date.now()
    }
    writeJson('candidate', candidate)
    const elapsed = Date.now() - candidate.startedAt
    if (interactive || elapsed >= DWELL_TIME_MS) {
      this.resolveArrival(position)
      return
    }
    const seconds = Math.max(1, Math.ceil((DWELL_TIME_MS - elapsed) / 1000))
    this.setData({ statusDetail: `检测到新位置，继续停留约 ${seconds} 秒后确认。` })
  },

  async resolveArrival(position) {
    this.setData({ statusDetail: '正在匹配附近地点…' })
    try {
      const match = await request({
        url: '/api/mini/location/arrival-match',
        data: { latitude: position.latitude, longitude: position.longitude }
      })
      removeValue('candidate')
      this.setData({
        promptVisible: true,
        place: match.province ? `${match.province} · ${match.location}` : (match.location || '当前位置'),
        promptDetail: match.matched
          ? '检测到新的到访地点，是否加入旅行足迹？'
          : '暂未匹配到附近地点，你仍可进入发布页补充名称和省份。',
        accuracyLabel: `定位精度约 ${Math.round(position.accuracy)} 米`,
        currentPosition: position,
        currentMatch: match,
        statusDetail: `已发现：${match.location || '当前位置'}`
      })
    } catch (error) {
      this.setData({ statusDetail: error.message || '地点匹配失败，稍后会自动重试。' })
    }
  },

  rememberPrompt() {
    const position = this.data.currentPosition
    if (!position) return
    writeJson('lastPrompt', {
      latitude: position.latitude,
      longitude: position.longitude,
      promptedAt: Date.now()
    })
  },

  ignoreArrival() {
    this.rememberPrompt()
    this.setData({ promptVisible: false, statusDetail: '本次地点已忽略，提醒继续开启。' })
  },

  addFootprint() {
    const position = this.data.currentPosition
    const match = this.data.currentMatch || {}
    if (!position) return
    this.rememberPrompt()
    const params = [
      'arrival=1',
      `latitude=${encodeURIComponent(position.latitude.toFixed(6))}`,
      `longitude=${encodeURIComponent(position.longitude.toFixed(6))}`,
      `date=${encodeURIComponent(today())}`,
      `location=${encodeURIComponent(match.location || '当前位置')}`,
      `province=${encodeURIComponent(match.province || '')}`
    ].join('&')
    wx.navigateTo({ url: `/pages/publish/publish?${params}` })
  },

  openSettings() {
    wx.openSetting({
      success: (result) => {
        if (result.authSetting && result.authSetting['scope.userLocation']) {
          this.setData({ statusDetail: '位置权限已允许，请点击“重新尝试定位”。', errorDetail: '' })
        }
      }
    })
  }
})
