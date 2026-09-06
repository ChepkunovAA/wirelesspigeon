(function () {
    'use strict';
    var KEY = 'corpsman-theme';

    // Идемпотентность: если скрипт попал на страницу дважды, второй раз не
    // навешиваем дубли-слушатели (иначе один клик переключал бы дважды — и
    // переключатель «не работал» бы вообще).
    if (window.__corpsmanThemeInit) return;
    window.__corpsmanThemeInit = true;

    function currentTheme() {
        var stored = null;
        try { stored = localStorage.getItem(KEY); } catch (e) {}
        if (stored === 'dark' || stored === 'light') return stored;
        if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) return 'dark';
        return 'light';
    }

    function applyTheme(theme) {
        document.documentElement.setAttribute('data-bs-theme', theme);
    }

    function refreshButton(btn) {
        var theme = document.documentElement.getAttribute('data-bs-theme') || currentTheme();
        var icon = btn.querySelector('.bi');
        if (icon) {
            icon.className = 'bi ' + (theme === 'dark' ? 'bi-sun' : 'bi-moon-stars');
        }
        var label = theme === 'dark' ? 'Светлая тема' : 'Тёмная тема';
        btn.setAttribute('aria-label', label);
        btn.title = label;
    }

    applyTheme(currentTheme());

    function init() {
        var btn = document.getElementById('theme-toggle');
        if (!btn) return;
        refreshButton(btn);
        btn.addEventListener('click', function () {
            // Если атрибут по какой-то причине отсутствует — считаем тему из хранилища,
            // иначе каждый клик превращался бы в «переключение в тёмную».
            var current = document.documentElement.getAttribute('data-bs-theme') || currentTheme();
            var next = current === 'dark' ? 'light' : 'dark';
            try { localStorage.setItem(KEY, next); } catch (e) {}
            applyTheme(next);
            refreshButton(btn);
        });
    }

    // Подключаем слушатель и тогда, когда DOMContentLoaded уже случился
    // (скрипт подтянут поздним фрагментом) — иначе клик не работал бы вовсе.
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();