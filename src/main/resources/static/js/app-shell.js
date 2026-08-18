(() => {
  setupActiveNavigation();
  registerServiceWorker();
  setupNetworkStatusIndicator();
  setupOfflineDraftForms();
  setupMobileNavigation();
  setupPwaInstall();
  setupCopyControls();
  setupArrivalReminders();
})();

function setupArrivalReminders() {
  const toggle = document.querySelector("[data-arrival-toggle]");
  const toggleLabel = document.querySelector("[data-arrival-toggle-label]");
  const consent = document.querySelector("[data-arrival-consent]");
  const dialog = document.querySelector("[data-arrival-dialog]");
  const status = document.querySelector("[data-arrival-status]");
  const statusText = document.querySelector("[data-arrival-status-text]");
  if (!toggle || !consent || !dialog) return;

  const storagePrefix = `travelFootprint.arrivalReminder.${toggle.dataset.arrivalUser || "user"}`;
  const ENABLED_KEY = `${storagePrefix}.enabled`;
  const LAST_PROMPT_KEY = `${storagePrefix}.lastPrompt`;
  const CANDIDATE_KEY = `${storagePrefix}.candidate`;
  const REMINDER_COOLDOWN_MS = 24 * 60 * 60 * 1000;
  const DWELL_TIME_MS = 2 * 60 * 1000;
  const NEW_PLACE_DISTANCE_METERS = 500;
  let watchId = null;
  let dwellTimer = null;
  let resolving = false;
  let interactiveStart = false;
  let coarseRequestPending = false;
  let locationGeneration = 0;

  const readJson = (key) => {
    try {
      return JSON.parse(localStorage.getItem(key) || "null");
    } catch (error) {
      try { localStorage.removeItem(key); } catch (storageError) { /* optional local state */ }
      return null;
    }
  };
  const writeJson = (key, value) => {
    try { localStorage.setItem(key, JSON.stringify(value)); } catch (error) { /* optional local state */ }
  };
  const readValue = (key) => {
    try { return localStorage.getItem(key); } catch (error) { return null; }
  };
  const removeValue = (key) => {
    try { localStorage.removeItem(key); } catch (error) { /* optional local state */ }
  };
  const isEnabled = () => readValue(ENABLED_KEY) === "true";
  const setEnabled = (enabled) => {
    try { localStorage.setItem(ENABLED_KEY, String(enabled)); } catch (error) { /* current page still works */ }
    toggle.classList.toggle("is-enabled", enabled);
    if (toggleLabel) toggleLabel.textContent = enabled ? "关闭到访提醒" : "开启到访提醒";
    if (status) status.hidden = !enabled;
  };
  const setArrivalStatus = (message) => {
    if (statusText) statusText.textContent = message;
  };
  const closeModal = (modal) => {
    modal.hidden = true;
    if (consent.hidden && dialog.hidden) document.body.classList.remove("arrival-modal-open");
  };
  const openModal = (modal) => {
    modal.hidden = false;
    document.body.classList.add("arrival-modal-open");
  };
  const distanceMeters = (first, second) => {
    const radius = 6371008.8;
    const toRadians = (value) => value * Math.PI / 180;
    const latitudeDelta = toRadians(second.latitude - first.latitude);
    const longitudeDelta = toRadians(second.longitude - first.longitude);
    const firstLatitude = toRadians(first.latitude);
    const secondLatitude = toRadians(second.latitude);
    const value = Math.sin(latitudeDelta / 2) ** 2
      + Math.cos(firstLatitude) * Math.cos(secondLatitude) * Math.sin(longitudeDelta / 2) ** 2;
    return radius * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
  };
  const localDate = () => {
    const now = new Date();
    const offset = now.getTimezoneOffset() * 60000;
    return new Date(now.getTime() - offset).toISOString().slice(0, 10);
  };
  const recentlyPrompted = (position) => {
    const lastPrompt = readJson(LAST_PROMPT_KEY);
    return lastPrompt && Date.now() - lastPrompt.promptedAt < REMINDER_COOLDOWN_MS
      && distanceMeters(lastPrompt, position) < NEW_PLACE_DISTANCE_METERS;
  };
  const rememberPrompt = (position) => writeJson(LAST_PROMPT_KEY, {
    latitude: position.latitude,
    longitude: position.longitude,
    promptedAt: Date.now()
  });
  const stopWatching = () => {
    locationGeneration += 1;
    if (watchId !== null && navigator.geolocation) navigator.geolocation.clearWatch(watchId);
    watchId = null;
    coarseRequestPending = false;
    window.clearTimeout(dwellTimer);
    dwellTimer = null;
  };
  const disableReminder = () => {
    stopWatching();
    setEnabled(false);
    removeValue(CANDIDATE_KEY);
    closeModal(consent);
    closeModal(dialog);
  };
  const createPostUrl = (position, match) => {
    const parameters = new URLSearchParams({
      arrivalLatitude: position.latitude.toFixed(6),
      arrivalLongitude: position.longitude.toFixed(6),
      arrivalDate: localDate(),
      arrivalLocation: match.location || "当前位置"
    });
    if (match.province) parameters.set("arrivalProvince", match.province);
    return `/posts/new?${parameters.toString()}`;
  };
  const resolveArrival = async (position) => {
    if (resolving || !dialog.hidden || recentlyPrompted(position) || document.visibilityState !== "visible") return;
    resolving = true;
    window.clearTimeout(dwellTimer);
    dwellTimer = null;
    setArrivalStatus("正在识别当前地点…");
    try {
      const parameters = new URLSearchParams({
        latitude: position.latitude.toFixed(6),
        longitude: position.longitude.toFixed(6)
      });
      const response = await fetch(`/api/location/arrival-match?${parameters.toString()}`, {
        credentials: "same-origin",
        headers: { Accept: "application/json" },
        cache: "no-store"
      });
      if (response.status === 401) {
        disableReminder();
        return;
      }
      const match = response.ok ? await response.json() : {
        location: "当前位置", province: "", matched: false
      };
      const place = dialog.querySelector("[data-arrival-place]");
      const detail = dialog.querySelector("[data-arrival-detail]");
      const coordinates = dialog.querySelector("[data-arrival-coordinates]");
      const addLink = dialog.querySelector("[data-arrival-add]");
      if (place) place.textContent = match.province
        ? `${match.province} · ${match.location}` : match.location || "当前位置";
      if (detail) detail.textContent = match.matched
        ? (position.accuracy > 1000
          ? "当前为粗略定位，附近地点可能有偏差。确认后可在发布页修改地点，并会默认隐藏具体点位。"
          : "检测到你来到新的地点。是否加入旅行足迹？确认后会自动填写地点、坐标和当天日期。")
        : "暂未匹配到附近的离线地点。你仍可加入足迹，并在发布页面补充地点名称和省份。";
      if (coordinates) coordinates.textContent = `定位精度约 ${Math.round(position.accuracy)} 米`;
      if (addLink) addLink.href = createPostUrl(position, match);
      dialog.dataset.latitude = String(position.latitude);
      dialog.dataset.longitude = String(position.longitude);
      setArrivalStatus(`已发现：${match.location || "当前位置"}`);
      openModal(dialog);
    } catch (error) {
      setArrivalStatus(navigator.onLine ? "地点识别失败，等待下次定位" : "当前离线，等待网络恢复");
    } finally {
      resolving = false;
      removeValue(CANDIDATE_KEY);
    }
  };
  const scheduleCandidate = (position) => {
    if (resolving || !dialog.hidden) return;
    if (recentlyPrompted(position)) {
      interactiveStart = false;
      setArrivalStatus("到访提醒已开启 · 当前地点今天已提醒");
      return;
    }
    const storedCandidate = readJson(CANDIDATE_KEY);
    const sameCandidate = storedCandidate
      && distanceMeters(storedCandidate, position) < NEW_PLACE_DISTANCE_METERS;
    const candidate = sameCandidate ? storedCandidate : {
      latitude: position.latitude,
      longitude: position.longitude,
      accuracy: position.accuracy,
      startedAt: Date.now()
    };
    writeJson(CANDIDATE_KEY, candidate);
    window.clearTimeout(dwellTimer);
    const remaining = Math.max(0, DWELL_TIME_MS - (Date.now() - candidate.startedAt));
    if (interactiveStart) {
      interactiveStart = false;
      resolveArrival(position);
      return;
    }
    setArrivalStatus(remaining > 0 ? "检测到新位置，停留确认中…" : "正在确认新的到访地点…");
    dwellTimer = window.setTimeout(() => resolveArrival(position), remaining);
  };
  const handlePosition = ({ coords }) => {
    if (!Number.isFinite(coords.latitude) || !Number.isFinite(coords.longitude)) return;
    const accuracy = Math.max(1, coords.accuracy || 1);
    if (accuracy > 5000) {
      setArrivalStatus("定位精度较低，正在重新定位…");
      return;
    }
    if (accuracy > 1000 && !interactiveStart) {
      setArrivalStatus("已取得粗略位置，正在等待更精确的定位…");
      return;
    }
    scheduleCandidate({
      latitude: coords.latitude,
      longitude: coords.longitude,
      accuracy
    });
  };
  const handleLocationError = (error, mode = "high") => {
    if (!dialog.hidden) return;
    if (error.code === 1) {
      disableReminder();
      window.alert("定位权限未开启。请在浏览器地址栏的网站权限中允许定位后重试。");
      return;
    }
    if (error.code === 3) {
      setArrivalStatus(mode === "high" && coarseRequestPending
        ? "高精度定位响应较慢，正在尝试兼容定位…"
        : "定位超时：请开启设备位置服务，或改用手机后重试");
      return;
    }
    setArrivalStatus("设备暂时无法提供位置，请检查系统位置服务后重试");
  };
  const requestCoarsePosition = (generation) => {
    coarseRequestPending = true;
    navigator.geolocation.getCurrentPosition((position) => {
      if (generation !== locationGeneration) return;
      coarseRequestPending = false;
      handlePosition(position);
    }, (error) => {
      if (generation !== locationGeneration) return;
      coarseRequestPending = false;
      handleLocationError(error, "coarse");
    }, {
      enableHighAccuracy: false,
      maximumAge: 5 * 60 * 1000,
      timeout: 15000
    });
  };
  const startWatching = (fromUserAction) => {
    if (!window.isSecureContext || !navigator.geolocation) {
      window.alert("当前浏览器无法使用定位。请通过 HTTPS 或本机 localhost 打开旅迹，并确认浏览器支持定位。");
      setEnabled(false);
      return;
    }
    stopWatching();
    const generation = locationGeneration;
    interactiveStart = fromUserAction;
    setEnabled(true);
    setArrivalStatus("正在获取当前位置…");
    requestCoarsePosition(generation);
    watchId = navigator.geolocation.watchPosition((position) => {
      if (generation === locationGeneration) handlePosition(position);
    }, (error) => {
      if (generation === locationGeneration) handleLocationError(error, "high");
    }, {
      enableHighAccuracy: true,
      maximumAge: 60000,
      timeout: 45000
    });
  };

  toggle.addEventListener("click", () => {
    if (isEnabled()) disableReminder();
    else openModal(consent);
  });
  consent.querySelectorAll("[data-arrival-consent-close]").forEach((button) => {
    button.addEventListener("click", () => closeModal(consent));
  });
  consent.querySelector("[data-arrival-enable]")?.addEventListener("click", () => {
    closeModal(consent);
    startWatching(true);
  });
  dialog.querySelectorAll("[data-arrival-dismiss]").forEach((button) => {
    button.addEventListener("click", () => {
      rememberPrompt({
        latitude: Number(dialog.dataset.latitude),
        longitude: Number(dialog.dataset.longitude)
      });
      closeModal(dialog);
      setArrivalStatus("到访提醒已开启 · 本次地点已忽略");
    });
  });
  dialog.querySelector("[data-arrival-add]")?.addEventListener("click", () => {
    rememberPrompt({
      latitude: Number(dialog.dataset.latitude),
      longitude: Number(dialog.dataset.longitude)
    });
  });
  dialog.querySelector("[data-arrival-disable]")?.addEventListener("click", disableReminder);
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible" && isEnabled()) startWatching(false);
    else if (document.visibilityState !== "visible") {
      stopWatching();
      removeValue(CANDIDATE_KEY);
    }
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      if (!consent.hidden) closeModal(consent);
      else if (!dialog.hidden) closeModal(dialog);
    }
  });

  setEnabled(isEnabled());
  if (isEnabled() && document.visibilityState === "visible") startWatching(false);
}

function setupActiveNavigation() {
  const currentPath = window.location.pathname;
  const navigation = document.querySelector("[data-primary-navigation]");
  if (!navigation) return;

  const aliases = {
    "/posts/new": "/posts/new",
    "/reports": "/reports",
    "/recap": "/reports",
    "/plans": "/plans",
    "/trips": "/plans",
    "/messages": "/messages",
    "/notifications": "/notifications"
  };
  const preferredPath = Object.entries(aliases)
    .find(([prefix]) => currentPath === prefix || currentPath.startsWith(`${prefix}/`))?.[1] || currentPath;

  let matched = false;
  navigation.querySelectorAll("a[href]").forEach((link) => {
    const path = new URL(link.href, window.location.origin).pathname;
    const isHome = path === "/" && preferredPath === "/";
    const isSection = path !== "/" && (preferredPath === path || preferredPath.startsWith(`${path}/`));
    if (isHome || isSection) {
      link.classList.add("is-active");
      link.setAttribute("aria-current", "page");
      if (link.closest(".nav-more")) link.closest(".nav-more").classList.add("is-active");
      matched = true;
    }
  });

  document.body.classList.add("premium-ui");
  if (!matched && currentPath.startsWith("/users/")) {
    const profileLink = navigation.querySelector(".nav-user");
    profileLink?.classList.add("is-active");
    profileLink?.setAttribute("aria-current", "page");
    matched = Boolean(profileLink);
  }
  if (!matched && currentPath.startsWith("/posts/")) {
    navigation.querySelector('a[href="/posts/new"]')?.classList.add("is-active");
  }
}

function setupCopyControls() {
  document.querySelectorAll("[data-copy-value]").forEach((button) => {
    button.addEventListener("click", async () => {
      const value = button.dataset.copyValue;
      if (!value) return;
      const fullValue = value.startsWith("/") ? new URL(value, window.location.origin).href : value;
      try {
        await navigator.clipboard.writeText(fullValue);
        const original = button.textContent;
        button.textContent = "已复制";
        window.setTimeout(() => {
          button.textContent = original;
        }, 1600);
      } catch (error) {
        const input = document.querySelector("[data-share-link]");
        input?.select();
      }
    });
  });
}

function setupPwaInstall() {
  const installButtons = Array.from(document.querySelectorAll("[data-install-app]"));
  const statusNodes = Array.from(document.querySelectorAll("[data-install-status]"));
  if (installButtons.length === 0 && statusNodes.length === 0) {
    return;
  }

  let installPrompt = null;
  const updateStatus = (text) => statusNodes.forEach((node) => {
    node.textContent = text;
  });

  window.addEventListener("beforeinstallprompt", (event) => {
    event.preventDefault();
    installPrompt = event;
    installButtons.forEach((button) => {
      button.hidden = false;
    });
    updateStatus("当前浏览器支持安装，可作为独立应用打开旅迹。");
  });

  installButtons.forEach((button) => button.addEventListener("click", async () => {
    if (!installPrompt) {
      updateStatus("安装入口暂不可用；也可以使用浏览器菜单中的“安装应用”。");
      return;
    }
    installPrompt.prompt();
    const choice = await installPrompt.userChoice;
    updateStatus(choice.outcome === "accepted" ? "安装请求已确认。" : "已取消安装，可稍后再试。");
    installPrompt = null;
    button.hidden = true;
  }));

  window.addEventListener("appinstalled", () => {
    installButtons.forEach((button) => {
      button.hidden = true;
    });
    updateStatus("旅迹已经安装到当前设备。");
  });
}

function setupMobileNavigation() {
  const toggle = document.querySelector("[data-nav-toggle]");
  const navigation = document.querySelector("[data-primary-navigation]");
  if (!toggle || !navigation) {
    return;
  }

  const closeNavigation = () => {
    toggle.setAttribute("aria-expanded", "false");
    navigation.classList.remove("is-open");
  };

  toggle.addEventListener("click", () => {
    const willOpen = toggle.getAttribute("aria-expanded") !== "true";
    toggle.setAttribute("aria-expanded", String(willOpen));
    navigation.classList.toggle("is-open", willOpen);
  });

  navigation.addEventListener("click", (event) => {
    if (event.target.closest("a, button")) {
      closeNavigation();
    }
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      closeNavigation();
      toggle.focus();
    }
  });

  window.addEventListener("resize", () => {
    if (window.innerWidth > 720) {
      closeNavigation();
    }
  });
}

function registerServiceWorker() {
  if (!("serviceWorker" in navigator)) {
    return;
  }

  window.addEventListener("load", () => {
    navigator.serviceWorker.register("/service-worker.js").catch(() => {
      // Keep the app usable even if offline cache registration fails.
    });
  });
}

function setupNetworkStatusIndicator() {
  const banner = ensureNetworkBanner();
  const labelNode = banner.querySelector("[data-network-label]");
  const detailNode = banner.querySelector("[data-network-detail]");
  const connection = navigator.connection || navigator.mozConnection || navigator.webkitConnection;

  const syncNetworkState = () => {
    const isOnline = navigator.onLine;
    banner.hidden = false;
    banner.classList.toggle("is-online", isOnline);
    banner.classList.toggle("is-offline", !isOnline);
    labelNode.textContent = isOnline ? "当前网络：在线" : "当前网络：离线";
    detailNode.textContent = isOnline
      ? buildOnlineNetworkDetail(connection)
      : "浏览器检测到电脑当前没有网络连接，系统会优先使用本地缓存与草稿。";
    document.dispatchEvent(new CustomEvent("travelfootprint:network-state", {
      detail: {
        online: isOnline
      }
    }));
  };

  window.addEventListener("online", syncNetworkState);
  window.addEventListener("offline", syncNetworkState);
  window.addEventListener("pageshow", syncNetworkState);

  if (connection) {
    if (typeof connection.addEventListener === "function") {
      connection.addEventListener("change", syncNetworkState);
    } else {
      connection.onchange = syncNetworkState;
    }
  }

  syncNetworkState();
}

function ensureNetworkBanner() {
  let banner = document.querySelector("[data-network-banner]");
  if (banner) {
    return banner;
  }

  banner = document.createElement("section");
  banner.className = "network-banner";
  banner.hidden = true;
  banner.setAttribute("data-network-banner", "");
  banner.innerHTML = [
    '<div class="container network-banner-inner">',
    '<strong data-network-label>网络状态检测中</strong>',
    '<span data-network-detail>正在读取设备网络信息...</span>',
    '</div>'
  ].join("");
  document.body.prepend(banner);
  return banner;
}

function buildOnlineNetworkDetail(connection) {
  const parts = [];
  const typeLabel = describeConnectionType(connection?.type);
  const qualityLabel = describeEffectiveType(connection?.effectiveType);

  if (typeLabel) {
    parts.push("连接类型：" + typeLabel);
  }
  if (qualityLabel) {
    parts.push("网络质量：" + qualityLabel);
  }

  return parts.length > 0
    ? parts.join(" · ")
    : "浏览器检测到电脑当前网络连接正常。";
}

function describeConnectionType(type) {
  switch (type) {
    case "wifi":
      return "Wi-Fi";
    case "ethernet":
      return "以太网";
    case "cellular":
      return "蜂窝网络";
    case "bluetooth":
      return "蓝牙网络";
    case "wimax":
      return "WiMAX";
    case "vpn":
      return "VPN";
    case "none":
      return "无网络";
    default:
      return "";
  }
}

function describeEffectiveType(effectiveType) {
  switch (effectiveType) {
    case "slow-2g":
      return "较弱";
    case "2g":
      return "2G";
    case "3g":
      return "3G";
    case "4g":
      return "4G";
    default:
      return "";
  }
}

function setupOfflineDraftForms() {
  document.querySelectorAll(".offline-draft-form").forEach((form) => {
    const draftKey = form.dataset.draftKey;
    if (!draftKey) {
      return;
    }

    const statusNode = form.querySelector("[data-draft-status]");
    const clearButton = form.querySelector("[data-clear-draft]");
    const fields = Array.from(form.querySelectorAll("input, textarea, select"))
      .filter((field) => field.name && field.type !== "file" && field.type !== "submit" && field.type !== "button");

    hydrateDraft(fields, draftKey, statusNode);

    let saveTimer = null;
    const saveDraft = () => {
      const payload = {};
      fields.forEach((field) => {
        payload[field.name] = field.value;
      });
      localStorage.setItem(draftKey, JSON.stringify(payload));
      setStatus(statusNode, "草稿已保存到本机");
    };

    fields.forEach((field) => {
      field.addEventListener("input", () => {
        clearTimeout(saveTimer);
        saveTimer = window.setTimeout(saveDraft, 250);
      });
      field.addEventListener("change", saveDraft);
    });

    clearButton?.addEventListener("click", () => {
      localStorage.removeItem(draftKey);
      fields.forEach((field) => {
        if (field.tagName === "SELECT") {
          field.selectedIndex = 0;
        } else {
          field.value = "";
        }
      });
      setStatus(statusNode, "本地草稿已清空");
    });

    form.addEventListener("submit", () => {
      setStatus(statusNode, "正在提交；如果暂时无法提交，本地草稿仍会保留。");
    });

    document.addEventListener("travelfootprint:network-state", (event) => {
      if (!event.detail?.online) {
        setStatus(statusNode, "当前离线；文字草稿会继续保存在本机。");
      }
    });
  });
}

function hydrateDraft(fields, draftKey, statusNode) {
  const raw = localStorage.getItem(draftKey);
  if (!raw) {
    return;
  }

  try {
    const draft = JSON.parse(raw);
    fields.forEach((field) => {
      if (!field.value && typeof draft[field.name] === "string") {
        field.value = draft[field.name];
      }
    });
    setStatus(statusNode, "已恢复上次未提交的本地草稿");
  } catch (error) {
    localStorage.removeItem(draftKey);
  }
}

function setStatus(node, text) {
  if (node) {
    node.textContent = text;
  }
}
