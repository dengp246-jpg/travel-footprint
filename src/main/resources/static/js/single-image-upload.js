(() => {
  document.querySelectorAll("[data-auto-compress-image]").forEach((input) => {
    const form = input.closest("form");
    const hint = input.closest("label")?.querySelector("[data-image-upload-hint]");

    input.addEventListener("change", async () => {
      const file = input.files?.[0];
      if (!file) return;
      const submitButton = form?.querySelector("button[type='submit']");
      input.disabled = true;
      if (submitButton) submitButton.disabled = true;
      if (hint) hint.textContent = "正在优化图片，请稍候…";

      try {
        const optimized = await window.TravelImageCompression.compress(file, {
          targetBytes: 1200 * 1024,
          maxOutputBytes: 2 * 1024 * 1024,
          maxDimension: 1600
        });
        const transfer = new DataTransfer();
        transfer.items.add(optimized);
        input.files = transfer.files;
        if (hint) {
          const saved = Math.max(0, file.size - optimized.size);
          hint.textContent = saved > 0
            ? `已自动优化，上传大小 ${(optimized.size / 1024 / 1024).toFixed(2)}MB`
            : "图片已就绪，可以保存资料。";
        }
      } catch (error) {
        input.value = "";
        if (hint) {
          hint.textContent = error.message || "图片处理失败，请重新选择。";
          hint.setAttribute("role", "alert");
        } else {
          window.alert(error.message || "图片处理失败，请重新选择。");
        }
      } finally {
        input.disabled = false;
        if (submitButton) submitButton.disabled = false;
      }
    });
  });
})();
