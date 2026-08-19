(() => {
  const MAX_SOURCE_IMAGE_BYTES = 25 * 1024 * 1024;
  const MAX_TOTAL_UPLOAD_BYTES = 8 * 1024 * 1024;
  const DEFAULT_MAX_VIDEO_BYTES = 20 * 1024 * 1024;
  const ALLOWED_IMAGE_TYPES = new Set([
    "image/jpeg",
    "image/png",
    "image/gif",
    "image/webp"
  ]);
  const ALLOWED_VIDEO_TYPES = new Set(["video/mp4", "video/webm"]);
  let amapLoaderPromise;

  document.querySelectorAll("[data-post-editor]").forEach((form) => {
    setupCharacterCounters(form);
    setupGalleryUpload(form);
    setupVideoUpload(form);
    setupPrivacyPreview(form);
    setupLocationAssistant(form);
    setupAmapLocationPicker(form);
    setupUploadProgress(form);
    setupValidationFeedback(form);
  });

  function setupUploadProgress(form) {
    const panel = form.querySelector("[data-upload-progress]");
    const bar = form.querySelector("[data-upload-progress-bar]");
    const percent = form.querySelector("[data-upload-percent]");
    const status = form.querySelector("[data-upload-status]");
    const retry = form.querySelector("[data-upload-retry]");
    const cancel = form.querySelector("[data-upload-cancel]");
    const submitButton = form.querySelector("[data-submit-button]");
    if (!panel || !bar || !percent || !status) return;

    let uploading = false;
    let currentRequest = null;
    const warnBeforeLeave = (event) => {
      if (!uploading) return;
      event.preventDefault();
      event.returnValue = "上传仍在进行，确定要离开吗？";
    };
    window.addEventListener("beforeunload", warnBeforeLeave);

    const restoreButton = () => {
      if (!submitButton) return;
      submitButton.disabled = false;
      submitButton.removeAttribute("aria-busy");
      submitButton.textContent = submitButton.dataset.originalLabel || "重新提交";
    };

    const upload = () => {
      if (uploading || !form.reportValidity()) return;
      uploading = true;
      panel.hidden = false;
      retry.hidden = true;
      if (cancel) cancel.hidden = false;
      bar.value = 0;
      percent.textContent = "0%";
      status.textContent = "正在建立安全上传连接…";
      const xhr = new XMLHttpRequest();
      currentRequest = xhr;
      xhr.open((form.method || "POST").toUpperCase(), form.action, true);
      xhr.setRequestHeader("X-Requested-With", "XMLHttpRequest");
      xhr.upload.addEventListener("progress", (event) => {
        if (!event.lengthComputable) {
          status.textContent = "正在上传，请保持页面打开…";
          return;
        }
        const value = Math.min(99, Math.round(event.loaded * 100 / event.total));
        bar.value = value;
        percent.textContent = `${value}%`;
        status.textContent = value < 100 ? "正在上传图片和视频…" : "服务器正在保存足迹…";
      });
      xhr.addEventListener("load", () => {
        uploading = false;
        currentRequest = null;
        if (cancel) cancel.hidden = true;
        if (xhr.status >= 200 && xhr.status < 400) {
          bar.value = 100;
          percent.textContent = "100%";
          status.textContent = "上传完成，正在打开足迹…";
          window.location.assign(xhr.responseURL || "/");
          return;
        }
        status.textContent = `上传失败（${xhr.status}），请检查登录状态或文件大小。`;
        retry.hidden = false;
        restoreButton();
      });
      xhr.addEventListener("error", () => {
        uploading = false;
        currentRequest = null;
        if (cancel) cancel.hidden = true;
        status.textContent = "网络中断，已保留表单内容，可以直接重试。";
        retry.hidden = false;
        restoreButton();
      });
      xhr.addEventListener("abort", () => {
        uploading = false;
        currentRequest = null;
        if (cancel) cancel.hidden = true;
        status.textContent = "上传已取消，可以重新提交。";
        retry.hidden = false;
        restoreButton();
      });
      xhr.send(new FormData(form));
    };

    form.addEventListener("submit", (event) => {
      event.preventDefault();
      upload();
    });
    retry?.addEventListener("click", upload);
    cancel?.addEventListener("click", () => currentRequest?.abort());
  }

  function loadAmapEditor(form) {
    if (window.AMap?.Map) return Promise.resolve(window.AMap);
    if (amapLoaderPromise) return amapLoaderPromise;
    const key = (form.dataset.amapKey || "").trim();
    if (!key) return Promise.reject(new Error("missing-amap-key"));
    let serviceHost = (form.dataset.amapServiceHost || "").trim();
    if (form.dataset.amapLocalProxy === "true") {
      serviceHost = `${window.location.origin}/_AMapService`;
    }
    const securityCode = (form.dataset.amapSecurityCode || "").trim();
    window._AMapSecurityConfig = serviceHost ? { serviceHost } : { securityJsCode: securityCode };
    amapLoaderPromise = new Promise((resolve, reject) => {
      const script = document.createElement("script");
      script.src = "https://webapi.amap.com/loader.js";
      script.onload = () => window.AMapLoader.load({
        key,
        version: "2.0",
        plugins: ["AMap.PlaceSearch", "AMap.Geocoder", "AMap.Scale"]
      }).then(resolve).catch(reject);
      script.onerror = reject;
      document.head.appendChild(script);
    });
    return amapLoaderPromise;
  }

  function setupAmapLocationPicker(form) {
    const canvas = form.querySelector("[data-amap-location-map]");
    const results = form.querySelector("[data-amap-place-results]");
    const locationInput = form.querySelector("[data-location-input]");
    const provinceSelect = form.querySelector("[data-province-select]");
    const latitudeInput = form.querySelector("[data-latitude-input]");
    const longitudeInput = form.querySelector("[data-longitude-input]");
    const status = form.querySelector("[data-location-status]");
    if (!canvas || !results || !locationInput || !provinceSelect || !latitudeInput || !longitudeInput) return;

    loadAmapEditor(form).then((AMap) => {
      canvas.closest(".smart-location-map")?.classList.add("has-amap-picker");
      const initialLng = Number(longitudeInput.value);
      const initialLat = Number(latitudeInput.value);
      const hasInitial = Number.isFinite(initialLng) && Number.isFinite(initialLat)
        && longitudeInput.value !== "" && latitudeInput.value !== "";
      const map = new AMap.Map(canvas, {
        zoom: hasInitial ? 13 : 4,
        center: hasInitial ? [initialLng, initialLat] : [104.1954, 35.8617],
        viewMode: "2D"
      });
      map.addControl(new AMap.Scale());
      const marker = new AMap.Marker({
        position: hasInitial ? [initialLng, initialLat] : map.getCenter(),
        draggable: true,
        visible: hasInitial
      });
      map.add(marker);
      const placeSearch = new AMap.PlaceSearch({ pageSize: 6, pageIndex: 1 });
      const geocoder = new AMap.Geocoder();
      let searchTimer;

      const normalizeProvince = (value) => String(value || "")
        .replace(/特别行政区$/, "")
        .replace(/壮族自治区$/, "")
        .replace(/回族自治区$/, "")
        .replace(/维吾尔自治区$/, "")
        .replace(/自治区$/, "")
        .replace(/[省市]$/, "");

      const setPoint = (lng, lat, label, province) => {
        longitudeInput.value = Number(lng).toFixed(6);
        latitudeInput.value = Number(lat).toFixed(6);
        if (label) locationInput.value = label;
        const normalizedProvince = normalizeProvince(province);
        if (normalizedProvince && Array.from(provinceSelect.options).some((option) => option.value === normalizedProvince)) {
          provinceSelect.value = normalizedProvince;
        }
        marker.setPosition([lng, lat]);
        marker.show();
        map.setZoomAndCenter(14, [lng, lat]);
        if (status) status.textContent = `${locationInput.value || "地图选点"} 已定位；可拖动标记继续微调。`;
      };

      const reverseGeocode = (lng, lat) => {
        geocoder.getAddress([lng, lat], (state, response) => {
          const component = state === "complete" ? response.regeocode?.addressComponent : null;
          const formatted = response.regeocode?.formattedAddress;
          setPoint(lng, lat, formatted || locationInput.value, component?.province);
        });
      };

      const renderPlaces = (pois) => {
        results.replaceChildren();
        pois.slice(0, 6).forEach((poi) => {
          if (!poi.location) return;
          const button = document.createElement("button");
          button.type = "button";
          button.textContent = `${poi.name} · ${poi.district || poi.cityname || poi.pname || ""}`;
          button.addEventListener("click", () => {
            setPoint(poi.location.getLng(), poi.location.getLat(), poi.name, poi.pname || poi.cityname);
            results.hidden = true;
          });
          results.appendChild(button);
        });
        results.hidden = results.childElementCount === 0;
      };

      locationInput.addEventListener("input", () => {
        window.clearTimeout(searchTimer);
        const keyword = locationInput.value.trim();
        if (keyword.length < 2) {
          results.hidden = true;
          return;
        }
        searchTimer = window.setTimeout(() => {
          placeSearch.search(keyword, (state, response) => {
            renderPlaces(state === "complete" ? (response.poiList?.pois || []) : []);
          });
        }, 350);
      });
      map.on("click", (event) => reverseGeocode(event.lnglat.getLng(), event.lnglat.getLat()));
      marker.on("dragend", (event) => reverseGeocode(event.lnglat.getLng(), event.lnglat.getLat()));
      window.addEventListener("pagehide", () => map.destroy(), { once: true });
    }).catch(() => {
      if (status && !form.dataset.amapKey) {
        status.textContent = "高德 Key 未配置，当前仍可使用内置地点建议和省份定位。";
      }
    });
  }

  function setupPrivacyPreview(form) {
    const select = form.querySelector("[data-visibility-select]");
    const approximateInput = form.querySelector("[data-approximate-location-input]");
    const title = form.querySelector("[data-privacy-preview-title]");
    const text = form.querySelector("[data-privacy-preview-text]");
    if (!select || !title || !text) return;

    const visibilityCopy = {
      PUBLIC: ["所有人可查看", "这条足迹会出现在公共动态与公共地图中。"],
      FOLLOWERS: ["仅关注者可查看", "只有关注你的人能看到足迹内容与地图信息。"],
      PRIVATE: ["仅自己可查看", "这条足迹只会保存在你的个人空间中。"]
    };
    const update = () => {
      const copy = visibilityCopy[select.value] || visibilityCopy.PUBLIC;
      title.textContent = copy[0];
      text.textContent = copy[1] + (approximateInput?.checked
        ? " 对其他可见用户仅展示省份范围，不显示具体点位。"
        : " 地图将按权限展示你填写的具体点位。");
    };
    select.addEventListener("change", update);
    approximateInput?.addEventListener("change", update);
    update();
  }

  function setupCharacterCounters(form) {
    form.querySelectorAll("[data-counted-field]").forEach((field) => {
      const counter = field.closest("label")?.querySelector("[data-character-count]");
      if (!counter) {
        return;
      }
      const update = () => {
        counter.textContent = String(field.value.length);
      };
      field.addEventListener("input", update);
      update();
    });
  }

  function setupVideoUpload(form) {
    const input = form.querySelector("[data-video-input]");
    const hint = form.querySelector("[data-video-hint]");
    const preview = form.querySelector("[data-video-preview]");
    if (!input) return;
    const originalHint = hint?.textContent || "";
    const configuredMax = Number(input.dataset.maxVideoBytes);
    const maxVideoBytes = Number.isFinite(configuredMax) && configuredMax > 0
      ? configuredMax
      : DEFAULT_MAX_VIDEO_BYTES;
    const maxVideoLabel = formatFileSize(maxVideoBytes);
    let objectUrl = "";

    const clearPreview = () => {
      if (objectUrl) URL.revokeObjectURL(objectUrl);
      objectUrl = "";
      if (preview) {
        preview.removeAttribute("src");
        preview.load();
        preview.hidden = true;
      }
    };

    input.addEventListener("change", () => {
      clearPreview();
      clearFormError(form);
      const file = input.files?.[0];
      if (!file) {
        if (hint) hint.textContent = originalHint;
        return;
      }
      if (!isAllowedVideoFile(file)) {
        input.value = "";
        showFormError(form, "请选择 MP4 或 WebM 视频。");
        if (hint) hint.textContent = originalHint;
        return;
      }
      if (file.size > maxVideoBytes) {
        input.value = "";
        showFormError(form, `视频“${file.name}”超过 ${maxVideoLabel}，请压缩或裁剪后重试。`);
        if (hint) hint.textContent = originalHint;
        return;
      }
      if (hint) hint.textContent = `已选择 ${file.name} · ${formatFileSize(file.size)}`;
      if (preview) {
        objectUrl = URL.createObjectURL(file);
        preview.onloadedmetadata = () => {
          const duration = Number.isFinite(preview.duration) ? formatDuration(preview.duration) : "时长未知";
          const resolution = preview.videoWidth && preview.videoHeight
            ? `${preview.videoWidth}×${preview.videoHeight}`
            : "分辨率未知";
          if (hint) {
            hint.textContent = `已选择 ${file.name} · ${formatFileSize(file.size)} · ${duration} · ${resolution}`;
          }
        };
        preview.src = objectUrl;
        preview.hidden = false;
      }
    });
    window.addEventListener("pagehide", clearPreview, { once: true });
  }

  function formatFileSize(bytes) {
    if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(bytes % (1024 * 1024) === 0 ? 0 : 2)}MB`;
    return `${Math.max(1, Math.ceil(bytes / 1024))}KB`;
  }

  function isAllowedVideoFile(file) {
    const type = (file.type || "").toLowerCase();
    if (ALLOWED_VIDEO_TYPES.has(type)) return true;
    if (type && type !== "application/octet-stream") return false;
    return /\.(mp4|webm)$/i.test(file.name || "");
  }

  function formatDuration(seconds) {
    const wholeSeconds = Math.max(0, Math.round(seconds));
    const minutes = Math.floor(wholeSeconds / 60);
    const remainder = String(wholeSeconds % 60).padStart(2, "0");
    return `${minutes}:${remainder}`;
  }

  function setupGalleryUpload(form) {
    const input = form.querySelector("[data-gallery-input]");
    const preview = form.querySelector("[data-gallery-preview]");
    const grid = form.querySelector("[data-gallery-grid]");
    const clearButton = form.querySelector("[data-clear-gallery]");
    const coverIndexInput = form.querySelector("[data-cover-photo-index]");
    const uploadHint = form.querySelector("[data-gallery-hint]");
    const originalUploadHint = uploadHint?.textContent || "";
    if (!input || !preview || !grid || !clearButton || !coverIndexInput) return;

    let selectedFiles = [];
    let objectUrls = [];
    let coverFile = null;

    const releaseObjectUrls = () => {
      objectUrls.forEach((url) => URL.revokeObjectURL(url));
      objectUrls = [];
    };

    const syncInputFiles = () => {
      const transfer = new DataTransfer();
      selectedFiles.forEach((file) => transfer.items.add(file));
      input.files = transfer.files;
      if (!coverFile || !selectedFiles.includes(coverFile)) coverFile = selectedFiles[0] || null;
      coverIndexInput.value = String(Math.max(0, selectedFiles.indexOf(coverFile)));
    };

    const moveFile = (from, to) => {
      if (!Number.isInteger(from) || !Number.isInteger(to) || from === to
          || from < 0 || to < 0 || from >= selectedFiles.length || to >= selectedFiles.length) return;
      const [moved] = selectedFiles.splice(from, 1);
      selectedFiles.splice(to, 0, moved);
      syncInputFiles();
      render();
    };

    const render = () => {
      releaseObjectUrls();
      grid.replaceChildren();
      selectedFiles.forEach((file, index) => {
        const url = URL.createObjectURL(file);
        objectUrls.push(url);
        const card = document.createElement("article");
        card.className = "gallery-upload-item";
        card.draggable = true;
        card.dataset.index = String(index);
        const image = document.createElement("img");
        image.src = url;
        image.alt = `待上传照片 ${index + 1}`;
        const number = document.createElement("span");
        number.textContent = String(index + 1);
        const coverButton = document.createElement("button");
        coverButton.type = "button";
        coverButton.className = "gallery-upload-cover";
        coverButton.textContent = file === coverFile ? "当前封面" : "设为封面";
        coverButton.classList.toggle("is-cover", file === coverFile);
        coverButton.addEventListener("click", () => {
          coverFile = file;
          syncInputFiles();
          render();
        });
        const orderControls = document.createElement("div");
        orderControls.className = "gallery-order-controls";
        const movePrevious = document.createElement("button");
        movePrevious.type = "button";
        movePrevious.textContent = "←";
        movePrevious.title = "向前移动";
        movePrevious.setAttribute("aria-label", `将第 ${index + 1} 张照片向前移动`);
        movePrevious.disabled = index === 0;
        movePrevious.addEventListener("click", () => moveFile(index, index - 1));
        const moveNext = document.createElement("button");
        moveNext.type = "button";
        moveNext.textContent = "→";
        moveNext.title = "向后移动";
        moveNext.setAttribute("aria-label", `将第 ${index + 1} 张照片向后移动`);
        moveNext.disabled = index === selectedFiles.length - 1;
        moveNext.addEventListener("click", () => moveFile(index, index + 1));
        orderControls.append(movePrevious, moveNext);
        card.append(image, number, orderControls, coverButton);
        grid.append(card);
      });
      preview.hidden = selectedFiles.length === 0;
    };

    grid.addEventListener("dragstart", (event) => {
      const item = event.target.closest(".gallery-upload-item");
      if (item) event.dataTransfer.setData("text/plain", item.dataset.index);
    });
    grid.addEventListener("dragover", (event) => event.preventDefault());
    grid.addEventListener("drop", (event) => {
      event.preventDefault();
      const target = event.target.closest(".gallery-upload-item");
      const from = Number(event.dataTransfer.getData("text/plain"));
      const to = Number(target?.dataset.index);
      if (!Number.isInteger(from) || !Number.isInteger(to) || from === to) return;
      moveFile(from, to);
    });

    input.addEventListener("change", async () => {
      const files = Array.from(input.files || []);
      if (files.length > 9) {
        showFormError(form, "每篇足迹最多选择 9 张照片。");
        input.value = "";
        return;
      }
      const invalidType = files.find((file) => !ALLOWED_IMAGE_TYPES.has(file.type));
      const oversized = files.find((file) => file.size > MAX_SOURCE_IMAGE_BYTES);
      if (invalidType) {
        showFormError(form, "请选择 JPG、PNG、GIF 或 WebP 图片。");
        input.value = "";
        return;
      }
      if (oversized) {
        showFormError(form, `图片“${oversized.name}”超过 25MB，请先裁剪后再上传。`);
        input.value = "";
        return;
      }
      const submitButton = form.querySelector("[data-submit-button]");
      input.disabled = true;
      if (submitButton) submitButton.disabled = true;
      if (uploadHint) uploadHint.textContent = `正在优化 ${files.length} 张图片，请稍候…`;
      clearFormError(form);
      try {
        const optimizedFiles = [];
        for (const file of files) {
          optimizedFiles.push(await window.TravelImageCompression.compress(file, {
            targetBytes: 850 * 1024,
            maxOutputBytes: 2 * 1024 * 1024,
            maxDimension: 1920
          }));
        }
        const totalBytes = optimizedFiles.reduce((sum, file) => sum + file.size, 0);
        if (totalBytes > MAX_TOTAL_UPLOAD_BYTES) {
          throw new Error("所选图片压缩后总量仍超过 8MB，请减少照片数量后重试。");
        }
        selectedFiles = optimizedFiles;
        coverFile = selectedFiles[0] || null;
        syncInputFiles();
        render();
        if (uploadHint) {
          uploadHint.textContent = `${selectedFiles.length} 张图片已自动优化，共 ${(totalBytes / 1024 / 1024).toFixed(2)}MB`;
        }
      } catch (error) {
        selectedFiles = [];
        coverFile = null;
        input.value = "";
        render();
        showFormError(form, error.message || "图片处理失败，请重新选择。");
        if (uploadHint) uploadHint.textContent = originalUploadHint;
      } finally {
        input.disabled = false;
        if (submitButton) submitButton.disabled = false;
      }
    });

    clearButton.addEventListener("click", () => {
      selectedFiles = [];
      coverFile = null;
      input.value = "";
      coverIndexInput.value = "0";
      render();
      if (uploadHint) uploadHint.textContent = originalUploadHint;
    });
    window.addEventListener("pagehide", releaseObjectUrls, { once: true });
  }

  function setupLocationAssistant(form) {
    const assistant = form.querySelector("[data-location-assistant]");
    const locationInput = form.querySelector("[data-location-input]");
    const provinceSelect = form.querySelector("[data-province-select]");
    const latitudeInput = form.querySelector("[data-latitude-input]");
    const longitudeInput = form.querySelector("[data-longitude-input]");
    const status = form.querySelector("[data-location-status]");
    const previewPin = form.querySelector("[data-location-preview-pin]");
    const clearButton = form.querySelector("[data-location-clear]");
    const suggestionButtons = Array.from(form.querySelectorAll("[data-location-suggestion]"));
    if (!assistant || !locationInput || !provinceSelect || !latitudeInput || !longitudeInput) {
      return;
    }

    let selectedLocation = "";
    let selectedProvince = "";

    const projectCoordinates = (longitude, latitude) => {
      const svgWidth = 1600;
      const svgHeight = 1200;
      const minLongitude = 73.5;
      const maxLongitude = 134.8;
      const minLatitude = 18;
      const maxLatitude = 53.8;
      const scale = Math.min((svgWidth - 140) / (maxLongitude - minLongitude), (svgHeight - 180) / (maxLatitude - minLatitude));
      const originX = 70 + ((svgWidth - 140) - (maxLongitude - minLongitude) * scale) / 2;
      const originY = 80 + ((svgHeight - 180) - (maxLatitude - minLatitude) * scale) / 2;
      return {
        left: Math.max(4, Math.min(96, (originX + (longitude - minLongitude) * scale) / svgWidth * 100)),
        top: Math.max(4, Math.min(96, (originY + (maxLatitude - latitude) * scale) / svgHeight * 100))
      };
    };

    const showPreview = (left, top) => {
      if (!previewPin) {
        return;
      }
      previewPin.style.left = `${left}%`;
      previewPin.style.top = `${top}%`;
      previewPin.hidden = false;
    };

    const clearPreciseCoordinates = (message) => {
      latitudeInput.value = "";
      longitudeInput.value = "";
      selectedLocation = "";
      selectedProvince = "";
      if (previewPin) {
        previewPin.hidden = true;
      }
      if (clearButton) {
        clearButton.hidden = true;
      }
      if (status && message) {
        status.textContent = message;
      }
    };

    const hideSuggestions = () => {
      suggestionButtons.forEach((button) => {
        button.hidden = true;
      });
      assistant.classList.remove("has-suggestions");
    };

    const filterSuggestions = () => {
      const query = locationInput.value.trim().toLowerCase().replace(/\s+/g, "");
      let visibleCount = 0;
      suggestionButtons.forEach((button) => {
        const searchText = (button.dataset.search || "").toLowerCase().replace(/\s+/g, "");
        const matches = !query || searchText.includes(query);
        const visible = matches && visibleCount < 6;
        button.hidden = !visible;
        if (visible) {
          visibleCount += 1;
        }
      });
      assistant.classList.toggle("has-suggestions", visibleCount > 0);
      if (status && query && visibleCount === 0 && !latitudeInput.value) {
        status.textContent = "没有匹配的精准地点，你仍可选择省份后使用自动估算位置。";
      }
    };

    suggestionButtons.forEach((button) => {
      button.addEventListener("click", () => {
        locationInput.value = button.dataset.location || "";
        provinceSelect.value = button.dataset.province || "";
        latitudeInput.value = Number(button.dataset.latitude).toFixed(6);
        longitudeInput.value = Number(button.dataset.longitude).toFixed(6);
        selectedLocation = locationInput.value;
        selectedProvince = provinceSelect.value;
        showPreview(Number(button.dataset.left), Number(button.dataset.top));
        if (clearButton) {
          clearButton.hidden = false;
        }
        if (status) {
          status.textContent = `${selectedProvince} · ${selectedLocation} 已精准定位，可直接发布。`;
        }
        hideSuggestions();
        locationInput.focus();
      });
    });

    locationInput.addEventListener("focus", filterSuggestions);
    locationInput.addEventListener("input", () => {
      if (locationInput.value !== selectedLocation) {
        clearPreciseCoordinates("正在匹配地点；也可以继续手动填写并选择省份。");
      }
      filterSuggestions();
    });
    provinceSelect.addEventListener("change", () => {
      if (selectedProvince && provinceSelect.value !== selectedProvince) {
        clearPreciseCoordinates("省份已修改，将根据新省份和目的地自动估算位置。");
      }
    });
    assistant.addEventListener("focusout", () => {
      window.setTimeout(() => {
        if (!assistant.contains(document.activeElement) && document.activeElement !== locationInput) {
          hideSuggestions();
        }
      }, 80);
    });
    clearButton?.addEventListener("click", () => {
      clearPreciseCoordinates("精准坐标已取消，系统将根据省份和目的地自动估算位置。");
      locationInput.focus();
    });

    const initialLatitude = Number(latitudeInput.value);
    const initialLongitude = Number(longitudeInput.value);
    if (Number.isFinite(initialLatitude) && Number.isFinite(initialLongitude)
        && latitudeInput.value !== "" && longitudeInput.value !== "") {
      const point = projectCoordinates(initialLongitude, initialLatitude);
      selectedLocation = locationInput.value;
      selectedProvince = provinceSelect.value;
      showPreview(point.left, point.top);
      if (clearButton) {
        clearButton.hidden = false;
      }
      if (status) {
        status.textContent = "已载入这条足迹保存的精准坐标。";
      }
    }
  }

  function setupValidationFeedback(form) {
    form.addEventListener("invalid", (event) => {
      const field = event.target;
      const label = field.closest("label")?.childNodes[0]?.textContent?.trim() || "表单字段";
      showFormError(form, `${label}填写不完整或格式不正确，请检查后再提交。`);
    }, true);

    form.addEventListener("input", () => clearFormError(form));
    form.addEventListener("submit", () => {
      const button = form.querySelector("[data-submit-button]");
      if (!button) {
        return;
      }
      button.disabled = true;
      button.dataset.originalLabel = button.textContent;
      button.textContent = button.dataset.submitLabel || "正在提交…";
      button.setAttribute("aria-busy", "true");
    });
  }

  function showFormError(form, message) {
    const error = form.querySelector("[data-form-error]");
    if (!error) {
      return;
    }
    error.textContent = message;
    error.hidden = false;
  }

  function clearFormError(form) {
    const error = form.querySelector("[data-form-error]");
    if (!error) {
      return;
    }
    error.textContent = "";
    error.hidden = true;
  }
})();
