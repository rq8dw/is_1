// Небольшие помощники для построения DOM без innerHTML (защита от XSS в пользовательских строках).

export function h(tag, props = {}, ...children) {
    const el = document.createElement(tag);
    for (const [key, value] of Object.entries(props || {})) {
        if (value === undefined || value === null || value === false) {
            continue;
        }
        if (key === 'class') {
            el.className = value;
        } else if (key === 'dataset') {
            Object.assign(el.dataset, value);
        } else if (key.startsWith('on') && typeof value === 'function') {
            el.addEventListener(key.slice(2).toLowerCase(), value);
        } else if (key === 'value' || key === 'checked' || key === 'disabled' || key === 'selected') {
            el[key] = value;
        } else {
            el.setAttribute(key, value === true ? '' : value);
        }
    }
    append(el, children);
    return el;
}

function append(el, children) {
    for (const child of children.flat(Infinity)) {
        if (child === undefined || child === null || child === false) {
            continue;
        }
        el.append(child instanceof Node ? child : String(child));
    }
}

export function clear(el, ...children) {
    el.replaceChildren();
    append(el, children);
    return el;
}

export const TYPES = ['GOVERNMENT', 'TRUST', 'PRIVATE_LIMITED_COMPANY'];

export function nullValue() {
    return h('span', {class: 'null'}, '—');
}

export function show(value) {
    return value === null || value === undefined ? nullValue() : String(value);
}

export function typeChip(type) {
    return h('span', {class: `chip chip--${type}`}, type);
}

export function formatDate(iso) {
    if (!iso) {
        return nullValue();
    }
    const date = new Date(iso);
    return Number.isNaN(date.getTime()) ? iso : date.toLocaleString('ru-RU');
}

export function formatCoordinates(c) {
    return c ? `(${c.x}; ${c.y})` : nullValue();
}

export function formatZip(address) {
    if (!address) {
        return nullValue();
    }
    return address.zipCode === '' ? h('span', {class: 'null'}, '«пустой индекс»') : address.zipCode;
}

export function formatString(value) {
    if (value === null || value === undefined) {
        return nullValue();
    }
    return value === '' ? h('span', {class: 'null'}, '«пустая строка»') : value;
}

export function pluralOrganizations(n) {
    const mod10 = n % 10;
    const mod100 = n % 100;
    if (mod10 === 1 && mod100 !== 11) {
        return `${n} организация`;
    }
    if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
        return `${n} организации`;
    }
    return `${n} организаций`;
}
