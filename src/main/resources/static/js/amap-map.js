(function () {
    'use strict';

    const escapeHtml = function (value) {
        return String(value || '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    };

    const loadAmap = function (key, securityCode, serviceHost) {
        return new Promise(function (resolve, reject) {
            if (window.AMap && window.AMap.Map) {
                resolve(window.AMap);
                return;
            }
            if (serviceHost) {
                window._AMapSecurityConfig = { serviceHost: serviceHost };
            } else if (securityCode) {
                window._AMapSecurityConfig = { securityJsCode: securityCode };
            }
            const initialize = function () {
                window.AMapLoader.load({
                    key: key,
                    version: '2.0',
                    plugins: ['AMap.Scale', 'AMap.ToolBar', 'AMap.MapType']
                }).then(resolve).catch(reject);
            };
            if (window.AMapLoader) {
                initialize();
                return;
            }
            const script = document.createElement('script');
            script.src = 'https://webapi.amap.com/loader.js';
            script.async = true;
            script.onload = initialize;
            script.onerror = function () { reject(new Error('高德地图加载器下载失败')); };
            document.head.appendChild(script);
        });
    };

    document.addEventListener('DOMContentLoaded', function () {
        const frame = document.querySelector('[data-map-frame]');
        const canvas = document.getElementById('travel-amap');
        if (!frame || !canvas) {
            return;
        }

        const key = (frame.dataset.amapKey || '').trim();
        const securityCode = (frame.dataset.amapSecurityCode || '').trim();
        let serviceHost = (frame.dataset.amapServiceHost || '').trim();
        if (frame.dataset.amapLocalProxy === 'true') {
            serviceHost = window.location.origin + '/_AMapService';
        }
        const configMessage = frame.querySelector('[data-amap-config-message]');
        const coordinateLabel = frame.querySelector('[data-amap-coordinates]');
        const markerNodes = Array.from(document.querySelectorAll('[data-amap-marker]'));
        const cards = Array.from(document.querySelectorAll('[data-map-post-card]'));
        const selectionStatus = document.querySelector('[data-map-selection-status]');
        const activeTitle = document.querySelector('[data-map-active-title]');
        const activeLocation = document.querySelector('[data-map-active-location]');
        const activeMeta = document.querySelector('[data-map-active-meta]');
        const storyDrawer = document.querySelector('[data-map-story-drawer]');
        const storyPhoto = document.querySelector('[data-map-story-photo]');
        const storyVideo = document.querySelector('[data-map-story-video]');
        const storyEyebrow = document.querySelector('[data-map-story-eyebrow]');
        const storyTitle = document.querySelector('[data-map-story-title]');
        const storyMeta = document.querySelector('[data-map-story-meta]');
        const storyExcerpt = document.querySelector('[data-map-story-excerpt]');
        const storyLink = document.querySelector('[data-map-story-link]');
        const personalMap = frame.dataset.mapMode === 'personal';
        const atlasShell = document.querySelector('.map-atlas-shell');
        const fullscreenButton = document.querySelector('[data-map-fullscreen]');
        const fullscreenLabel = document.querySelector('[data-map-fullscreen-label]');
        const storyClose = document.querySelector('[data-map-story-close]');
        const resultGrid = document.querySelector('[data-map-result-grid]');
        const viewButtons = Array.from(document.querySelectorAll('[data-map-view]'));
        const shareButton = document.querySelector('[data-share-map]');
        const resetButtons = Array.from(document.querySelectorAll('[data-map-reset], [data-map-reset-secondary]'));
        let mapInstance = null;

        const updateFullscreenControl = function () {
            const fallbackActive = atlasShell && atlasShell.classList.contains('is-map-expanded');
            const active = document.fullscreenElement === atlasShell || fallbackActive;
            fullscreenButton?.setAttribute('aria-pressed', String(active));
            if (fullscreenLabel) {
                fullscreenLabel.textContent = active ? '退出全屏' : '全屏地图';
            }
            window.setTimeout(function () { mapInstance?.resize(); }, 80);
        };

        fullscreenButton?.addEventListener('click', async function () {
            if (!atlasShell) return;
            if (document.fullscreenElement === atlasShell) {
                await document.exitFullscreen();
                return;
            }
            if (atlasShell.classList.contains('is-map-expanded')) {
                atlasShell.classList.remove('is-map-expanded');
                document.body.classList.remove('map-fullscreen-fallback');
                updateFullscreenControl();
                return;
            }
            try {
                if (atlasShell.requestFullscreen) {
                    await atlasShell.requestFullscreen();
                    return;
                }
            } catch (ignored) {
                // Fall back to an in-page immersive view when native fullscreen is unavailable.
            }
            atlasShell.classList.add('is-map-expanded');
            document.body.classList.add('map-fullscreen-fallback');
            updateFullscreenControl();
        });
        document.addEventListener('fullscreenchange', updateFullscreenControl);

        const closeStory = function () {
            if (!storyDrawer) return;
            storyDrawer.classList.remove('is-visible');
            if (storyVideo) storyVideo.pause();
            window.setTimeout(function () { storyDrawer.hidden = true; }, 180);
        };
        storyClose?.addEventListener('click', closeStory);
        document.addEventListener('keydown', function (event) {
            if (event.key !== 'Escape') return;
            closeStory();
            if (atlasShell?.classList.contains('is-map-expanded')) {
                atlasShell.classList.remove('is-map-expanded');
                document.body.classList.remove('map-fullscreen-fallback');
                updateFullscreenControl();
            }
        });

        const applyResultView = function (view, remember) {
            const normalized = view === 'list' ? 'list' : 'grid';
            resultGrid?.classList.toggle('is-list-view', normalized === 'list');
            viewButtons.forEach(function (button) {
                button.setAttribute('aria-pressed', String(button.dataset.mapView === normalized));
            });
            if (remember) {
                try {
                    window.localStorage.setItem('travel-map-result-view', normalized);
                } catch (ignored) {
                    // The current view still works when browser storage is unavailable.
                }
            }
        };
        let rememberedView = 'grid';
        try {
            rememberedView = window.localStorage.getItem('travel-map-result-view') || 'grid';
        } catch (ignored) {
            rememberedView = 'grid';
        }
        applyResultView(rememberedView, false);
        viewButtons.forEach(function (button) {
            button.addEventListener('click', function () {
                applyResultView(button.dataset.mapView, true);
            });
        });

        shareButton?.addEventListener('click', async function () {
            const shareUrl = window.location.href;
            try {
                await navigator.clipboard.writeText(shareUrl);
                shareButton.textContent = '已复制';
                window.setTimeout(function () { shareButton.textContent = '复制当前视图'; }, 1800);
            } catch (ignored) {
                window.prompt('复制下面的地图链接', shareUrl);
            }
        });

        resetButtons.forEach(function (button) {
            button.hidden = !frame.dataset.selectedProvince;
            button.addEventListener('click', function () {
                const parameters = new URLSearchParams(window.location.search);
                parameters.delete('province');
                parameters.delete('city');
                window.location.href = '/map' + (parameters.size ? '?' + parameters.toString() : '');
            });
        });

        if (!key || (!securityCode && !serviceHost)) {
            canvas.hidden = true;
            if (configMessage) {
                configMessage.hidden = false;
            }
            return;
        }

        const points = markerNodes.map(function (node) {
            const longitude = Number(node.dataset.longitude);
            const latitude = Number(node.dataset.latitude);
            return {
                node: node,
                postId: node.dataset.postId || '',
                groupKey: node.dataset.groupKey || node.dataset.postId || '',
                longitude: longitude,
                latitude: latitude,
                position: [longitude, latitude],
                overlay: null
            };
        }).filter(function (point) {
            return Number.isFinite(point.longitude) && Number.isFinite(point.latitude);
        });

        const grouped = new Map();
        points.forEach(function (point) {
            if (!grouped.has(point.groupKey)) {
                grouped.set(point.groupKey, []);
            }
            grouped.get(point.groupKey).push(point);
        });

        const showStory = function (node) {
            if (!storyDrawer || !node) {
                return;
            }
            const photoPath = node.dataset.photoPath || '';
            const videoPath = node.dataset.videoPath || '';
            storyTitle.textContent = node.dataset.title || '未命名足迹';
            storyEyebrow.textContent = node.dataset.province || '足迹预览';
            storyMeta.textContent = [node.dataset.location, node.dataset.travelDate, node.dataset.author]
                .filter(Boolean).join(' · ');
            storyExcerpt.textContent = node.dataset.excerpt || '这条足迹暂时没有故事摘要。';
            storyLink.href = node.dataset.postUrl || '#';
            if (storyPhoto) {
                storyPhoto.hidden = !photoPath || Boolean(videoPath);
                if (!storyPhoto.hidden) {
                    storyPhoto.src = photoPath;
                    storyPhoto.alt = (node.dataset.title || '足迹') + '的旅行照片';
                }
            }
            if (storyVideo) {
                storyVideo.hidden = !videoPath;
                if (videoPath) {
                    storyVideo.src = videoPath;
                }
            }
            storyDrawer.classList.toggle('has-media', Boolean(photoPath || videoPath));
            storyDrawer.hidden = false;
            window.requestAnimationFrame(function () { storyDrawer.classList.add('is-visible'); });
        };

        const highlight = function (point, scrollToCard) {
            const node = point.node;
            cards.forEach(function (card) {
                const selected = card.dataset.postId === point.postId;
                card.classList.toggle('is-selected', selected);
                card.setAttribute('aria-current', selected ? 'true' : 'false');
                if (selected && scrollToCard) {
                    card.scrollIntoView({ behavior: 'smooth', block: 'center' });
                }
            });
            activeTitle.textContent = node.dataset.title || '未命名足迹';
            activeLocation.textContent = [node.dataset.province, node.dataset.location].filter(Boolean).join(' · ');
            activeMeta.textContent = [node.dataset.category, node.dataset.travelDate].filter(Boolean).join(' · ') || '旅行足迹';
            if (selectionStatus) {
                selectionStatus.textContent = '已选择：' + (node.dataset.title || '旅行足迹');
            }
            showStory(node);
        };

        const navigationUrl = function (point, node) {
            const name = node.dataset.location || node.dataset.title || '目的地';
            return 'https://uri.amap.com/navigation?to=' + point.position[0].toFixed(6) + ','
                + point.position[1].toFixed(6) + ',' + encodeURIComponent(name)
                + '&mode=car&policy=1&src=TravelFootprint&callnative=1';
        };

        const infoContent = function (group) {
            const first = group[0];
            const label = first.node.dataset.groupLabel || first.node.dataset.location || '旅行地点';
            const entries = group.map(function (point) {
                const node = point.node;
                return '<article class="amap-info-entry">'
                    + '<strong>' + escapeHtml(node.dataset.title || '未命名足迹') + '</strong>'
                    + '<small>' + escapeHtml([node.dataset.province, node.dataset.location, node.dataset.author].filter(Boolean).join(' · ')) + '</small>'
                    + '<div><button type="button" data-amap-focus-post="' + escapeHtml(point.postId) + '">在列表中定位</button>'
                    + '<a href="' + escapeHtml(node.dataset.postUrl || '#') + '">查看足迹</a>'
                    + '<a target="_blank" rel="noopener" href="' + escapeHtml(navigationUrl(point, node)) + '">高德导航</a></div>'
                    + '</article>';
            }).join('');
            return '<section class="amap-info-card"><header><span>旅行地点</span><strong>' + escapeHtml(label)
                + '</strong><small>' + group.length + ' 条足迹</small></header>' + entries + '</section>';
        };

        loadAmap(key, securityCode, serviceHost).then(function (AMap) {
            const map = new AMap.Map(canvas, {
                viewMode: '2D',
                zoom: frame.dataset.selectedProvince ? 7 : 4,
                center: [104.1954, 35.8617],
                mapStyle: 'amap://styles/whitesmoke',
                showLabel: true,
                resizeEnable: true
            });
            mapInstance = map;
            map.addControl(new AMap.Scale());
            map.addControl(new AMap.ToolBar({ position: { right: '18px', bottom: '74px' } }));
            map.addControl(new AMap.MapType({ defaultType: 0, showRoad: true }));

            const overlays = [];
            const infoWindow = new AMap.InfoWindow({ isCustom: true, autoMove: true, offset: new AMap.Pixel(0, -42) });
            grouped.forEach(function (group) {
                const count = group.length;
                const content = document.createElement('button');
                content.type = 'button';
                content.className = 'amap-footprint-marker';
                content.setAttribute('aria-label', (group[0].node.dataset.groupLabel || '旅行地点') + '，' + count + ' 条足迹');
                content.innerHTML = '<span></span>' + (count > 1 ? '<strong>' + count + '</strong>' : '');
                const marker = new AMap.Marker({
                    position: group[0].position,
                    content: content,
                    anchor: 'bottom-center',
                    zIndex: 120
                });
                group.forEach(function (point) { point.overlay = marker; });
                marker.on('click', function () {
                    infoWindow.setContent(infoContent(group));
                    infoWindow.open(map, group[0].position);
                    highlight(group[0], false);
                });
                overlays.push(marker);
            });
            map.add(overlays);

            let route = null;
            const timelinePoints = points.slice().sort(function (first, second) {
                const firstDate = first.node.dataset.travelDate || first.node.dataset.publishedAt || '';
                const secondDate = second.node.dataset.travelDate || second.node.dataset.publishedAt || '';
                return firstDate.localeCompare(secondDate);
            });
            if (personalMap && timelinePoints.length > 1) {
                route = new AMap.Polyline({
                    path: timelinePoints.map(function (point) { return point.position; }),
                    strokeColor: '#0891b2',
                    strokeOpacity: 0.88,
                    strokeWeight: 5,
                    strokeStyle: 'solid',
                    lineJoin: 'round',
                    showDir: true,
                    zIndex: 80
                });
                map.add(route);
                overlays.push(route);
            }

            if (overlays.length) {
                map.setFitView(overlays, false, [80, 80, 80, 80], 14);
            }

            const openPoint = function (point, scrollToCard) {
                map.setZoomAndCenter(Math.max(map.getZoom(), 13), point.position, false, 320);
                const group = grouped.get(point.groupKey) || [point];
                infoWindow.setContent(infoContent(group));
                infoWindow.open(map, point.position);
                highlight(point, scrollToCard);
            };

            cards.forEach(function (card) {
                const activate = function () {
                    const point = points.find(function (item) { return item.postId === card.dataset.postId; });
                    if (point) {
                        openPoint(point, false);
                    }
                };
                card.querySelector('[data-focus-map]')?.addEventListener('click', activate);
                card.addEventListener('keydown', function (event) {
                    if (event.key === 'Enter' || event.key === ' ') {
                        event.preventDefault();
                        activate();
                    }
                });
            });

            document.addEventListener('click', function (event) {
                const focusButton = event.target.closest('[data-amap-focus-post]');
                if (!focusButton) {
                    return;
                }
                const point = points.find(function (item) { return item.postId === focusButton.dataset.amapFocusPost; });
                if (point) {
                    highlight(point, true);
                }
            });

            const timelineRange = document.querySelector('[data-amap-timeline-range]');
            const timelineProgress = document.querySelector('[data-amap-timeline-progress]');
            const timelineTitle = document.querySelector('[data-amap-timeline-title]');
            const timelineMeta = document.querySelector('[data-amap-timeline-meta]');
            const timelinePlay = document.querySelector('[data-amap-timeline-play]');
            const timelinePrevious = document.querySelector('[data-amap-timeline-prev]');
            const timelineNext = document.querySelector('[data-amap-timeline-next]');
            const timelineReset = document.querySelector('[data-amap-timeline-reset]');
            let timelineIndex = -1;
            let timelineTimer = null;

            const stopTimeline = function () {
                if (timelineTimer) {
                    window.clearInterval(timelineTimer);
                    timelineTimer = null;
                }
                timelinePlay?.classList.remove('is-playing');
                if (timelinePlay) {
                    timelinePlay.textContent = '播放旅程';
                }
            };

            const renderTimeline = function (index) {
                if (!timelinePoints.length) {
                    return;
                }
                timelineIndex = Math.max(0, Math.min(index, timelinePoints.length - 1));
                const point = timelinePoints[timelineIndex];
                if (timelineRange) {
                    timelineRange.value = String(timelineIndex);
                }
                timelineProgress.textContent = (timelineIndex + 1) + ' / ' + timelinePoints.length;
                timelineTitle.textContent = point.node.dataset.title || '未命名足迹';
                timelineMeta.textContent = [point.node.dataset.travelDate, point.node.dataset.location].filter(Boolean).join(' · ');
                if (route) {
                    route.setPath(timelinePoints.slice(0, timelineIndex + 1).map(function (item) { return item.position; }));
                }
                openPoint(point, false);
            };

            timelineRange?.addEventListener('input', function () {
                stopTimeline();
                renderTimeline(Number(timelineRange.value));
            });
            timelinePrevious?.addEventListener('click', function () { stopTimeline(); renderTimeline(timelineIndex - 1); });
            timelineNext?.addEventListener('click', function () { stopTimeline(); renderTimeline(timelineIndex + 1); });
            timelineReset?.addEventListener('click', function () {
                stopTimeline();
                timelineIndex = -1;
                if (timelineRange) {
                    timelineRange.value = '0';
                }
                timelineProgress.textContent = '准备播放';
                timelineTitle.textContent = '从第一段旅程开始';
                timelineMeta.textContent = '路线将按照实际旅行日期依次出现';
                if (route) {
                    route.setPath(timelinePoints.map(function (item) { return item.position; }));
                }
                map.setFitView(overlays, false, [80, 80, 80, 80], 14);
            });
            timelinePlay?.addEventListener('click', function () {
                if (timelineTimer) {
                    stopTimeline();
                    return;
                }
                timelinePlay.classList.add('is-playing');
                timelinePlay.textContent = '暂停旅程';
                renderTimeline(timelineIndex < 0 || timelineIndex >= timelinePoints.length - 1 ? 0 : timelineIndex + 1);
                timelineTimer = window.setInterval(function () {
                    if (timelineIndex >= timelinePoints.length - 1) {
                        stopTimeline();
                        return;
                    }
                    renderTimeline(timelineIndex + 1);
                }, 2400);
            });

            map.on('moveend', function () {
                const center = map.getCenter();
                if (coordinateLabel && center) {
                    coordinateLabel.textContent = 'N ' + center.lat.toFixed(4) + '° · E ' + center.lng.toFixed(4) + '°';
                }
            });
            document.addEventListener('fullscreenchange', function () { window.setTimeout(function () { map.resize(); }, 80); });
            window.addEventListener('resize', function () { map.resize(); });
        }).catch(function (error) {
            canvas.hidden = true;
            if (configMessage) {
                configMessage.hidden = false;
                const paragraph = configMessage.querySelector('p');
                if (paragraph) {
                    paragraph.textContent = '地图加载失败，请检查高德 Key、安全密钥、域名白名单与网络连接。';
                }
            }
            console.error('高德地图初始化失败', error);
        });
    });
}());
