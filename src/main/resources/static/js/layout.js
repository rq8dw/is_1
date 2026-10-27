import {h} from './dom.js';

const MENU = [
    {href: 'index.html', title: 'Организации'},
    {href: 'create.html', title: 'Создать'},
    {href: 'view.html', title: 'Поиск по ID'},
    {href: 'special.html', title: 'Спецоперации'},
    {href: 'related.html', title: 'Координаты и адреса'},
];

let toastBox = null;

/** Меню навигации между частями системы и индикатор live-обновлений. */
export function renderLayout(active) {
    const status = h('span', {class: 'live-status', title: 'Синхронизация с другими пользователями'}, 'Подключение…');
    document.addEventListener('live-status', (e) => {
        const online = e.detail.online;
        status.classList.toggle('online', online);
        status.classList.toggle('offline', !online);
        status.textContent = online ? 'Онлайн' : 'Нет связи, переподключение…';
    });

    const header = h('header', {class: 'app-header'},
        h('div', {class: 'app-header__inner'},
            h('a', {class: 'brand', href: 'index.html'}, 'ИС «Организации»'),
            h('nav', {class: 'menu'},
                MENU.map(item => h('a', {href: item.href, class: item.href === active ? 'active' : null}, item.title))),
            status));
    document.body.prepend(header);

    toastBox = h('div', {class: 'toasts', 'aria-live': 'polite'});
    document.body.append(toastBox);
}

export function toast(message, type = 'info', timeout = 4000) {
    if (!toastBox) {
        return;
    }
    const el = h('div', {class: `toast toast--${type}`, role: type === 'error' ? 'alert' : 'status'}, message);
    toastBox.append(el);
    setTimeout(() => el.remove(), timeout);
}
