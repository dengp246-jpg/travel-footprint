(() => {
  const MAX_SOURCE_IMAGE_BYTES = 25 * 1024 * 1024;
  const MAX_TOTAL_UPLOAD_BYTES = 8 * 1024 * 1024;
  const ALLOWED_IMAGE_TYPES = new Set([
    "image/jpeg",
    "image/png",
    "image/gif",
    "image/webp"
  ]);

  document.querySelectorAll("[data-post-editor]").forEach((form) => {
    setupCharacterCounters(form);
    setupGalleryUpload(form);
    setupLocationAssistant(form);
    setupValidationFeedback(form);
  });

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
        coverButton.textContent = file === coverFile ? "当前封面" : "设为封面";
        coverButton.classList.toggle("is-cover", file === coverFile);
        coverButton.addEventListener("click", () => {
          coverFile = file;
          syncInputFiles();
          render();
        });
        card.append(image, number, coverButton);
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
      const [moved] = selectedFiles.splice(from, 1);
      selectedFiles.splice(to, 0, moved);
      syncInputFiles();
      render();
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
