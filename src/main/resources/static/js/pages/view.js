import {api} from '../api.js';
import {deleteOrganizationWithConfirm, openEditDialog} from '../dialogs.js';
import {clear, formatDate, formatString, h, nullValue, pluralOrganizations, show, typeChip} from '../dom.js';
import {renderLayout} from '../layout.js';
import {numberInput, parseInteger} from '../organization-form.js';
import {debounce, onChange} from '../realtime.js';

renderLayout('view.html');

const app = document.getElementById('app');

// ---------- поиск по ID ----------

const idError = h('div', {class: 'error-text', id: 'id-error'});
const idInput = numberInput('integer', (msg) => {
    idError.textContent = msg;
});
idInput.id = 'org-id';
idInput.setAttribute('aria-describedby', 'id-error');
idInput.placeholder = 'Например, 1';

const searchForm = h('form', {class: 'card inline-form', novalidate: true},
    h('div', {class: 'field'}, h('label', {for: 'org-id'}, 'ID организации'), idInput, idError),
    h('button', {type: 'submit', class: 'btn btn--primary'}, 'Показать'));
searchForm.addEventListener('submit', (e) => {
    e.preventDefault();
    const parsed = parseInteger(idInput.value);
    let error = null;
    if (parsed.empty) {
        error = 'Введите ID';
    } else if (parsed.error) {
        error = parsed.error;
    } else if (parsed.value <= 0) {
        error = 'ID должен быть больше 0';
    }
    idError.textContent = error ?? '';
    searchForm.classList.toggle('invalid', Boolean(error));
    if (!error) {
        open(parsed.value);
    }
});

const content = h('div', {});
app.append(searchForm, content);

// ---------- отображение ----------

let currentId = null;
let deleted = false;

function open(id) {
    currentId = id;
    deleted = false;
    history.replaceState(null, '', `?id=${id}`);
    load();
}

async function load(flash = false) {
    const id = currentId;
    try {
        const [organization, coordinates, addresses] = await Promise.all([
            api.getOrganization(id), api.listCoordinates(), api.listAddresses()]);
        if (id !== currentId) {
            return;
        }
        render(organization,
            new Map(coordinates.map(c => [c.id, c.usages])),
            new Map(addresses.map(a => [a.id, a.usages])),
            flash);
    } catch (e) {
        if (id !== currentId) {
            return;
        }
        clear(content, h('div', {class: 'card'},
            h('div', {class: `alert ${e.status === 404 ? 'alert--warning' : 'alert--error'}`}, e.message),
            h('a', {href: 'index.html'}, '← К списку организаций')));
    }
}

function render(o, coordinatesUsages, addressUsages, flash) {
    const usage = (count) => count === undefined ? null
        : h('div', {class: 'hint'}, `Используется: ${pluralOrganizations(count)}`);
    const address = (title, a) => h('div', {class: 'related-card'},
        h('h3', {}, title),
        a ? h('dl', {class: 'props'},
            h('dt', {}, 'ID'), h('dd', {}, a.id),
            h('dt', {}, 'Индекс (zipCode)'), h('dd', {}, formatString(a.zipCode))) : h('div', {class: 'muted'}, 'Не указан'),
        a ? usage(addressUsages.get(a.id)) : null);

    clear(content,
        h('div', {class: `card${flash ? ' flash' : ''}`},
            h('div', {class: 'page-head'},
                h('h2', {}, `#${o.id} · ${o.name}`),
                h('div', {class: 'btn-row'},
                    h('button', {type: 'button', class: 'btn btn--primary', onclick: edit}, 'Изменить'),
                    h('button', {type: 'button', class: 'btn btn--danger', onclick: () => remove(o)}, 'Удалить'))),
            h('dl', {class: 'props'},
                h('dt', {}, 'ID'), h('dd', {}, o.id),
                h('dt', {}, 'Название (name)'), h('dd', {}, o.name),
                h('dt', {}, 'Полное название (fullName)'), h('dd', {}, formatString(o.fullName)),
                h('dt', {}, 'Тип (type)'), h('dd', {}, typeChip(o.type)),
                h('dt', {}, 'Дата создания'), h('dd', {}, formatDate(o.creationDate)),
                h('dt', {}, 'Количество сотрудников'), h('dd', {}, o.employeesCount),
                h('dt', {}, 'Годовой оборот'), h('dd', {}, show(o.annualTurnover)),
                h('dt', {}, 'Рейтинг'), h('dd', {}, o.rating ?? nullValue()))),
        h('div', {class: 'card'},
            h('h2', {}, 'Связанные объекты'),
            h('div', {class: 'relations-grid'},
                h('div', {class: 'related-card'},
                    h('h3', {}, 'Координаты'),
                    h('dl', {class: 'props'},
                        h('dt', {}, 'ID'), h('dd', {}, o.coordinates.id),
                        h('dt', {}, 'X'), h('dd', {}, o.coordinates.x),
                        h('dt', {}, 'Y'), h('dd', {}, o.coordinates.y)),
                    usage(coordinatesUsages.get(o.coordinates.id))),
                address('Официальный адрес', o.officialAddress),
                address('Почтовый адрес', o.postalAddress))));
}

function showDeleted() {
    deleted = true;
    clear(content, h('div', {class: 'card'},
        h('div', {class: 'alert alert--warning'}, `Организация #${currentId} была удалена.`),
        h('a', {href: 'index.html'}, '← К списку организаций')));
}

async function edit() {
    if (await openEditDialog(currentId)) {
        load(true);
    }
}

async function remove(organization) {
    if (await deleteOrganizationWithConfirm(organization)) {
        showDeleted();
    }
}

// ---------- синхронизация ----------

const reload = debounce((flash) => load(flash));

onChange((event) => {
    if (currentId === null || deleted) {
        return;
    }
    const affectsCurrent = event.entity === 'ORGANIZATION' && event.ids.includes(currentId);
    if (affectsCurrent && event.change === 'DELETED') {
        showDeleted();
    } else {
        // Изменение самой организации или счётчиков использования её координат/адресов
        reload(affectsCurrent);
    }
});

const initialId = parseInteger(new URLSearchParams(location.search).get('id') ?? '');
if (initialId.value > 0) {
    idInput.value = String(initialId.value);
    open(initialId.value);
} else {
    idInput.focus();
}
