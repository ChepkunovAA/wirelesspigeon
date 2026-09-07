/* Локальные времена в таймзоне браузера.
 *
 * Конвенция хранения (П.7 релиза 2.4.0): все LocalDateTime приложения —
 * wall-clock UTC. Сервер отдаёт разметку вида
 *     <time data-utc="2026-09-06T14:46:27.725242Z">06.09.2026 14:46</time>
 * (текст внутри — UTC-фоллбэк без JS). Этот скрипт переводит каждое такое
 * время в часовой пояс браузера; с data-utc-mode="date" выводится только дата.
 * Для динамически подгруженного контента можно вызвать window.corpsmanTimes().
 */
(function (window, document) {
    'use strict';

    var ISO_UTC = /^[0-9]{4,}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}/;

    function pad2(n) { return (n < 10 ? '0' : '') + n; }

    function parseUtc(iso) {
        if (typeof iso !== 'string' || !ISO_UTC.test(iso)) { return null; }
        var d = new Date(iso);
        return isNaN(d.getTime()) ? null : d;
    }

    function formatLocal(iso, mode) {
        var d = parseUtc(iso);
        if (!d) { return iso; }
        var date = pad2(d.getDate()) + '.' + pad2(d.getMonth() + 1) + '.' + d.getFullYear();
        return mode === 'date' ? date : date + ' ' + pad2(d.getHours()) + ':' + pad2(d.getMinutes());
    }

    function convertTimes(root) {
        var items = (root || document).querySelectorAll('time[data-utc]');
        for (var i = 0; i < items.length; i++) {
            var el = items[i];
            var local = formatLocal(el.getAttribute('data-utc'),
                el.getAttribute('data-utc-mode') || 'datetime');
            if (el.textContent !== local) { el.textContent = local; }
        }
    }

    function init() {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', init);
            return;
        }
        convertTimes();
    }
    init();

    window.corpsmanTimes = convertTimes;
})(window, document);