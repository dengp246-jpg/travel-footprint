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

function getLocation() {
  return new Promise((resolve, reject) => {
    wx.getLocation({
      type: 'gcj02',
      isHighAccuracy: true,
      highAccuracyExpireTime: 5000,
      success: resolve,
      fail: reject
    })
  })
}

Page({
  data: {
    enabled: false,
    checking: false,
    status: '到访提醒尚未开启',
    statusDetail: '开启后仅在本页面保持显示时读取位置。',
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

  toggleReminder() {
    if (this.data.enabled) {
      this.disableReminder()
      return
    }
    wx.showModal({
      title: '开启到访提醒',
      content: '小程序仅在本页面显示时读取位置，并发送到当前服务器匹配离线地点；不会保存定位轨迹或自动发布。',
      confirmText: '允许并开启',
      success: (result) => {
        if (result.confirm) this.startMonitoring(true)
      }
    })
  },

  startMonitoring(interactive) {
    this.stopMonitoring()
    writeJson('enabled', true)
    this.setData({
      enabled: true,
      status: '到访提醒已开启',
      statusDetail: '正在读取当前位置…'
    })
    this.checkPosition(interactive)
    this.pollTimer = setInterval(() => this.checkPosition(false), POLL_INTERVAL_MS)
  },

  stopMonitoring() {
    if (this.pollTimer) clearInterval(this.pollTimer)
    this.pollTimer = null
  },

  disableReminder() {
    this.stopMonitoring()
    writeJson('enabled', false)
    removeValue('candidate')
    this.setData({
      enabled: false,
      checking: false,
      promptVisible: false,
      status: '到访提醒已关闭',
      statusDetail: '不会继续读取当前位置。'
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
      const denied = String(error.errMsg || '').includes('auth deny')
        || String(error.errMsg || '').includes('auth denied')
      this.setData({
        statusDetail: denied ? '位置权限未开启，请在小程序设置中允许定位。' : '暂时无法读取位置，稍后会自动重试。'
      })
      if (denied) this.disableReminder()
    } finally {
      this.setData({ checking: false })
    }
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
    wx.openSetting()
  }
})
