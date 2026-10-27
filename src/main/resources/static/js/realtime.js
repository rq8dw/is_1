// Подписка на изменения, которые сервер рассылает по WebSocket после каждой транзакции.
// Событие: {entity: 'ORGANIZATION'|'COORDINATES'|'ADDRESS', change: 'CREATED'|'UPDATED'|'DELETED', ids: [...]}.
// После переподключения приходит {entity: '*', change: 'RESYNC'}: пока связи не было, события могли потеряться.

const listeners = new Set();
let socket = null;
let attempt = 0;
let everConnected = false;

export function onChange(listener) {
    listeners.add(listener);
    connect();
    return () => listeners.delete(listener);
}

function emit(event) {
    for (const listener of [...listeners]) {
        try {
            listener(event);
        } catch (e) {
            console.error('Ошибка обработчика обновлений', e);
        }
    }
}

function setStatus(online) {
    document.dispatchEvent(new CustomEvent('live-status', {detail: {online}}));
}

function connect() {
    if (socket) {
        return;
    }
    const url = new URL('ws/updates', location.href);
    url.protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    socket = new WebSocket(url);

    socket.addEventListener('open', () => {
        setStatus(true);
        if (everConnected) {
            emit({entity: '*', change: 'RESYNC', ids: []});
        }
        everConnected = true;
        attempt = 0;
    });
    socket.addEventListener('message', (message) => {
        try {
            emit(JSON.parse(message.data));
        } catch (e) {
            console.error('Некорректное сообщение об изменении', e);
        }
    });
    socket.addEventListener('close', () => {
        setStatus(false);
        socket = null;
        const delay = Math.min(10_000, 500 * 2 ** attempt++);
        setTimeout(connect, delay);
    });
}

/** Склеивает пачку событий в один вызов (например, удаление адреса удаляет несколько организаций). */
export function debounce(fn, ms = 150) {
    let timer = null;
    return (...args) => {
        clearTimeout(timer);
        timer = setTimeout(() => fn(...args), ms);
    };
}
