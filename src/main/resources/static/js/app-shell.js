(() => {
  registerServiceWorker();
  setupOfflineDraftForms();
})();

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
