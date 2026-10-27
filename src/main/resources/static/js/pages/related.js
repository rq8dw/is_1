import {api} from '../api.js';
import {confirmDialog} from '../dialogs.js';
import {clear, formatString, h, pluralOrganizations} from '../dom.js';
import {renderLayout, toast} from '../layout.js';
import {debounce, onChange} from '../realtime.js';

renderLayout('related.html');

const app = document.getElementById('app');

function section(title) {
    const body = h('div', {}, h('p', {class: 'muted'}, 'Загрузка…'));
    app.append(h('section', {class: 'card'}, h('h2', {}, title), body));
    return body;
}

const coordinatesBox = section('Координаты');
const addressesBox = section('Адреса');

function table(headers, rows, emptyText) {
    if (!rows.length) {
        return h('div', {class: 'muted'}, emptyText);
    }
    return h('div', {class: 'table-wrap'}, h('table', {class: 'data'},
        h('thead', {}, h('tr', {},
            headers.map(t => h('th', {class: t === 'ID' || t === 'X' || t === 'Y' ? 'num' : null}, t)),
            h('th', {}, ''))),
        h('tbody', {}, rows)));
}

async function remove(kind, item, describe) {
    const confirmed = await confirmDialog({
        title: `Удаление: ${describe}`,
        message: item.usages > 0
            ? [`Вместе с объектом будут удалены ${pluralOrganizations(item.usages)}, которые на него ссылаются.`,
                'Действие нельзя отменить.']
            : 'Объект не используется организациями. Действие нельзя отменить.',
        confirmText: 'Удалить',
        danger: true,
    });
    if (!confirmed) {
        return;
    }
    try {
        if (kind === 'coordinates') {
            await api.deleteCoordinates(item.id);
        } else {
            await api.deleteAddress(item.id);
        }
        toast(`Удалено: ${describe}`, 'success');
        load();
    } catch (e) {
        toast(e.message, 'error', 6000);
    }
}

async function load() {
    try {
        const [coordinates, addresses] = await Promise.all([api.listCoordinates(), api.listAddresses()]);
        clear(coordinatesBox, table(['ID', 'X', 'Y', 'Используется'],
            coordinates.map(c => h('tr', {},
                h('td', {class: 'num'}, c.id),
                h('td', {class: 'num'}, c.x),
                h('td', {class: 'num'}, c.y),
                h('td', {}, pluralOrganizations(c.usages)),
                h('td', {class: 'actions'}, h('button', {
                    type: 'button', class: 'btn btn--sm btn--danger',
                    onclick: () => remove('coordinates', c, `координаты #${c.id}`),
                }, 'Удалить')))),
            'Координат пока нет'));
        clear(addressesBox, table(['ID', 'Индекс (zipCode)', 'Используется'],
            addresses.map(a => h('tr', {},
                h('td', {class: 'num'}, a.id),
                h('td', {}, formatString(a.zipCode)),
                h('td', {}, pluralOrganizations(a.usages)),
                h('td', {class: 'actions'}, h('button', {
                    type: 'button', class: 'btn btn--sm btn--danger',
                    onclick: () => remove('address', a, `адрес #${a.id}`),
                }, 'Удалить')))),
            'Адресов пока нет'));
    } catch (e) {
        clear(coordinatesBox, h('div', {class: 'alert alert--error'}, e.message));
        clear(addressesBox);
    }
}

onChange(debounce(load));
load();
