// ==UserScript==
// @name         CYCM YouTube Bridge
// @namespace    http://tampermonkey.net/
// @version      2.4
// @author       CraftLLC
// @match        https://studio.youtube.com/*
// @match        https://www.youtube.com/live_chat*
// @grant        GM_xmlhttpRequest
// @connect      127.0.0.1
// @allFrames    true
// ==/UserScript==

(function() {
    'use strict';

    if (!window.location.href.includes('live_chat')) return;

    // Використовуємо звичайний HTTP порт твого сервера
    const SERVER_URL = 'http://127.0.0.1:21456';

    const processMessage = (node) => {
        if (node.nodeName !== 'YT-LIVE-CHAT-TEXT-MESSAGE-RENDERER') return;

        let rawId = node.__data?.data?.messageId || node.id;
        if (!rawId) return;

        const messageId = rawId.startsWith('LCC.') ? rawId : 'LCC.' + rawId;
        const author = node.querySelector('#author-name')?.innerText.trim();
        const messageText = node.querySelector('#message')?.innerText.trim() || "";

        const payload = JSON.stringify({
            id: messageId,
            author: author,
            text: messageText,
            timestamp: Date.now()
        });

        console.log(`%c[Chat] %c${messageId} %c${author}: ${messageText}`, 'color: #00ffff', 'color: #777', 'color: #fff');

        // Відправка через GM_xmlhttpRequest (ігнорує CSP YouTube)
        GM_xmlhttpRequest({
            method: "POST",
            url: SERVER_URL,
            data: payload,
            headers: { "Content-Type": "application/json" },
            timeout: 2000,
            onload: (res) => {
                if (res.status === 200) console.log('%c[OK] Відправлено на сервер', 'color: #00ff00; font-size: 10px;');
            },
            onerror: (err) => {
                console.error('[Error] Сервер не відповідає на 21456');
            }
        });
    };

    const observer = new MutationObserver((mutations) => {
        for (const mutation of mutations) {
            mutation.addedNodes.forEach(node => {
                if (node.nodeType === 1) processMessage(node);
            });
        }
    });

    const init = () => {
        const container = document.querySelector('#items.yt-live-chat-item-list-renderer');
        if (container) {
            observer.observe(container, { childList: true });
            console.log('%c[System] Ultra-Stable Mode Active', 'background: #222; color: #bada55');
        } else {
            setTimeout(init, 1000);
        }
    };

    init();
})();