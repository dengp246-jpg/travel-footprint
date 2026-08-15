(() => {
  const SUPPORTED_TYPES = new Set(["image/jpeg", "image/png", "image/gif", "image/webp"]);

  function canvasBlob(canvas, type, quality) {
    return new Promise((resolve, reject) => {
      canvas.toBlob((blob) => {
        if (blob) resolve(blob);
        else reject(new Error("浏览器无法处理这张图片，请改用 JPG、PNG 或 WebP。"));
      }, type, quality);
    });
  }

  function loadImage(file) {
    return new Promise((resolve, reject) => {
      const url = URL.createObjectURL(file);
      const image = new Image();
      image.onload = () => {
        URL.revokeObjectURL(url);
        resolve(image);
      };
      image.onerror = () => {
        URL.revokeObjectURL(url);
        reject(new Error(`无法读取图片“${file.name}”，请换成 JPG、PNG、GIF 或 WebP。`));
      };
      image.src = url;
    });
  }

  function renamedFile(file, blob, outputType) {
    const extension = outputType === "image/jpeg" ? ".jpg" : ".webp";
    const baseName = file.name.replace(/\.[^.]+$/, "") || "photo";
    return new File([blob], `${baseName}${extension}`, {
      type: outputType,
      lastModified: file.lastModified
    });
  }

  async function compress(file, options = {}) {
    const targetBytes = options.targetBytes || 850 * 1024;
    const maxOutputBytes = options.maxOutputBytes || 2 * 1024 * 1024;
    const maxSourceBytes = options.maxSourceBytes || 25 * 1024 * 1024;
    const maxDimension = options.maxDimension || 1920;

    if (!SUPPORTED_TYPES.has(file.type)) {
      throw new Error(`图片“${file.name}”格式不支持，请选择 JPG、PNG、GIF 或 WebP。`);
    }
    if (file.size > maxSourceBytes) {
      throw new Error(`图片“${file.name}”超过 25MB，请先在相册中裁剪后再上传。`);
    }
    if (file.type === "image/gif") {
      if (file.size > maxOutputBytes) {
        throw new Error(`动图“${file.name}”无法自动压缩，请选择小于 2MB 的 GIF。`);
      }
      return file;
    }
    if (file.size <= targetBytes) return file;

    const image = await loadImage(file);
    const sourceWidth = image.naturalWidth || image.width;
    const sourceHeight = image.naturalHeight || image.height;
    if (!sourceWidth || !sourceHeight) {
      throw new Error(`无法读取图片“${file.name}”的尺寸。`);
    }

    const outputType = file.type === "image/jpeg" ? "image/jpeg" : "image/webp";
    const initialScale = Math.min(1, maxDimension / Math.max(sourceWidth, sourceHeight));
    let width = Math.max(1, Math.round(sourceWidth * initialScale));
    let height = Math.max(1, Math.round(sourceHeight * initialScale));
    let bestBlob = null;
    const qualities = [0.86, 0.78, 0.70, 0.62, 0.54];

    for (let resizeRound = 0; resizeRound < 4; resizeRound += 1) {
      const canvas = document.createElement("canvas");
      canvas.width = width;
      canvas.height = height;
      const context = canvas.getContext("2d", { alpha: outputType !== "image/jpeg" });
      if (!context) throw new Error("浏览器无法启动图片压缩，请更新浏览器后重试。");
      if (outputType === "image/jpeg") {
        context.fillStyle = "#ffffff";
        context.fillRect(0, 0, width, height);
      }
      context.drawImage(image, 0, 0, width, height);

      for (const quality of qualities) {
        const blob = await canvasBlob(canvas, outputType, quality);
        if (!bestBlob || blob.size < bestBlob.size) bestBlob = blob;
        if (blob.size <= targetBytes) return renamedFile(file, blob, outputType);
      }
      width = Math.max(1, Math.round(width * 0.8));
      height = Math.max(1, Math.round(height * 0.8));
    }

    if (!bestBlob || bestBlob.size > maxOutputBytes) {
      throw new Error(`图片“${file.name}”压缩后仍然过大，请裁剪后重试。`);
    }
    return renamedFile(file, bestBlob, outputType);
  }

  window.TravelImageCompression = { compress };
})();
