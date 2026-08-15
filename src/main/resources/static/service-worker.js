const CACHE_VERSION = "travelfootprint-offline-v28";
const SHELL_CACHE = [
  "/",
  "/map",
  "/login",
  "/register",
  "/offline.html",
  "/css/style.css?v=20260808-16",
  "/css/premium.css?v=20260809-1",
  "/js/app-shell.js?v=20260809-1",
  "/js/image-compression.js?v=20260815-1",
  "/js/post-editor.js?v=20260815-1",
  "/manifest.webmanifest?v=20260808-2"
];
const SENSITIVE_PATH_PREFIXES = [
  "/messages",
  "/notifications",
  "/search",
  "/recommendations",
  "/insights",
  "/settings",
  "/admin",
  "/api/",
  "/h2-console"
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_VERSION).then((cache) => cache.addAll(SHELL_CACHE)).then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((key) => key !== CACHE_VERSION).map((key) => caches.delete(key)))
    ).then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  const { request } = event;
  if (request.method !== "GET") {
    return;
  }

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) {
    return;
  }

  if (request.mode === "navigate") {
    if (SENSITIVE_PATH_PREFIXES.some((prefix) => url.pathname.startsWith(prefix))) {
      event.respondWith(networkOnly(request));
      return;
    }
    event.respondWith(networkFirst(request));
    return;
  }

  event.respondWith(staleWhileRevalidate(request));
});

async function networkFirst(request) {
  const cache = await caches.open(CACHE_VERSION);
  try {
    const response = await fetch(request);
    if (response.ok) {
      cache.put(request, response.clone());
    }
    return response;
  } catch (error) {
    const cached = await cache.match(request);
    if (cached) {
      return cached;
    }
    const offlinePage = await cache.match("/offline.html");
    return offlinePage || Response.error();
  }
}

async function networkOnly(request) {
  try {
    return await fetch(request);
  } catch (error) {
    const cache = await caches.open(CACHE_VERSION);
    return await cache.match("/offline.html") || Response.error();
  }
}

async function staleWhileRevalidate(request) {
  const cache = await caches.open(CACHE_VERSION);
  const cached = await cache.match(request);

  const networkPromise = fetch(request)
    .then((response) => {
      if (response.ok) {
        cache.put(request, response.clone());
      }
      return response;
    })
    .catch(() => null);

  return cached || networkPromise || Response.error();
}
