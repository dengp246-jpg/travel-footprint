(() => {
  setupActiveNavigation();
  registerServiceWorker();
  setupNetworkStatusIndicator();
  setupOfflineDraftForms();
  setupMobileNavigation();
  setupPwaInstall();
  setupCopyControls();
})();

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
