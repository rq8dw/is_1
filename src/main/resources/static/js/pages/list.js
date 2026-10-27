import {api} from '../api.js';
import {deleteOrganizationWithConfirm, openEditDialog} from '../dialogs.js';
import {clear, formatCoordinates, formatDate, formatString, formatZip, h, show, TYPES, typeChip} from '../dom.js';
import {renderLayout} from '../layout.js';
import {debounce, onChange} from '../realtime.js';

renderLayout('index.html');

const COLUMNS = [
    {key: 'id', title: 'ID', cls: 'num', render: o => o.id},
    {key: 'name', title: 'Название', render: o => o.name},
    {key: 'coordinates', title: 'Координаты (x; y)', cls: 'nowrap', render: o => formatCoordinates(o.coordinates)},
    {key: 'creationDate', title: 'Дата создания', render: o => formatDate(o.creationDate)},
    {key: 'officialAddress', title: 'Офиц. адрес (индекс)', render: o => formatZip(o.officialAddress)},
    {key: 'annualTurnover', title: 'Годовой оборот', cls: 'num', render: o => show(o.annualTurnover)},
    {key: 'employeesCount', title: 'Сотрудники', cls: 'num', render: o => o.employeesCount},
    {key: 'rating', title: 'Рейтинг', cls: 'num', render: o => show(o.rating)},
    {key: 'fullName', title: 'Полное название', render: o => formatString(o.fullName)},
    {key: 'type', title: 'Тип', render: o => typeChip(o.type)},
    {key: 'postalAddress', title: 'Почт. адрес (индекс)', render: o => formatZip(o.postalAddress)},
];

// Фильтры по строковым колонкам, полное совпадение.
// canBeEmpty — значение поля может быть пустой строкой, и по ней тоже можно отфильтровать.
const FILTERS = [
    {key: 'name', label: 'Название'},
    {key: 'fullName', label: 'Полное название', canBeEmpty: true},
    {key: 'type', label: 'Тип', options: TYPES},
    {key: 'officialZipCode', label: 'Индекс офиц. адреса', canBeEmpty: true},
    {key: 'postalZipCode', label: 'Индекс почт. адреса', canBeEmpty: true},
];

const PAGE_SIZES = [5, 10, 20, 50];

// ---------- состояние (хранится в адресной строке) ----------

const state = readState();

function readState() {
    const params = new URLSearchParams(location.search);
    const filters = {};
    for (const f of FILTERS) {
        const value = params.get(f.key);
        if (value || (value === '' && f.canBeEmpty)) {
            filters[f.key] = value;
        }
    }
    const size = Number(params.get('size'));
    const sort = params.get('sort');
    return {
        page: Math.max(0, Number.parseInt(params.get('page'), 10) || 0),
        size: PAGE_SIZES.includes(size) ? size : 10,
        sort: COLUMNS.some(c => c.key === sort) ? sort : 'id',
        direction: params.get('direction') === 'desc' ? 'desc' : 'asc',
        filters,
    };
}

function writeState() {
    const params = new URLSearchParams({
        page: state.page, size: state.size, sort: state.sort, direction: state.direction, ...state.filters,
    });
    history.replaceState(null, '', `?${params}`);
}

// ---------- разметка ----------

const app = document.getElementById('app');
const filterInputs = {};
const emptyChecks = {};

const filtersForm = h('form', {class: 'card filters', autocomplete: 'off'},
    FILTERS.map(f => {
        const input = f.options
            ? h('select', {}, h('option', {value: ''}, 'Любой'), f.options.map(o => h('option', {value: o}, o)))
            : h('input', {type: 'text', placeholder: 'Точное значение', maxlength: 255});
        input.value = state.filters[f.key] ?? '';
        filterInputs[f.key] = input;
        if (!f.canBeEmpty) {
            return h('label', {class: 'field'}, h('span', {class: 'field-label'}, f.label), input);
        }
        // Пустое поле ввода означает «без фильтра», поэтому пустая строка выбирается флажком
        const empty = h('input', {
            type: 'checkbox',
            checked: state.filters[f.key] === '',
            onchange: () => {
                input.disabled = empty.checked;
                if (empty.checked) {
                    input.value = '';
                }
            },
        });
        input.disabled = empty.checked;
        emptyChecks[f.key] = empty;
        return h('div', {class: 'field'},
            h('label', {class: 'field'}, h('span', {class: 'field-label'}, f.label), input),
            h('label', {class: 'hint check'}, empty, ' пустая строка'));
    }),
    h('div', {class: 'btn-row'},
        h('span', {class: 'hint'}, 'Строка показывается, только если значение совпадает полностью'),
        h('button', {type: 'button', class: 'btn', onclick: resetFilters}, 'Сбросить'),
        h('button', {type: 'submit', class: 'btn btn--primary'}, 'Применить')));
filtersForm.addEventListener('submit', (e) => {
    e.preventDefault();
    applyFilters();
});

const summary = h('div', {class: 'muted'});
const pager = h('div', {class: 'pager'});
const errorBox = h('div', {class: 'alert alert--error', role: 'alert'});
const thead = h('thead');
const tbody = h('tbody');

app.append(
    filtersForm,
    h('div', {class: 'toolbar'}, summary, pager),
    errorBox,
    h('div', {class: 'table-wrap'}, h('table', {class: 'data'}, thead, tbody)));

function renderHead() {
    clear(thead, h('tr', {},
        COLUMNS.map(c => {
            const sorted = state.sort === c.key;
            const mark = sorted ? (state.direction === 'asc' ? '▲' : '▼') : '↕';
            return h('th', {
                    class: `sortable${sorted ? ' sorted' : ''}${c.cls === 'num' ? ' num' : ''}`,
                    tabindex: 0,
                    title: 'Нажмите, чтобы отсортировать',
                    'aria-sort': sorted ? (state.direction === 'asc' ? 'ascending' : 'descending') : null,
                    onclick: () => toggleSort(c.key),
                    onkeydown: (e) => {
                        if (e.key === 'Enter' || e.key === ' ') {
                            e.preventDefault();
                            toggleSort(c.key);
                        }
                    },
                },
                c.title, h('span', {class: 'sort-mark'}, mark));
        }),
        h('th', {class: 'actions'}, 'Действия')));
}

function renderRows(rows, highlight) {
    if (!rows.length) {
        const filtered = Object.keys(state.filters).length > 0;
        clear(tbody, h('tr', {}, h('td', {class: 'empty', colspan: COLUMNS.length + 1},
            filtered ? 'По заданным фильтрам ничего не найдено' : 'Организаций пока нет. ',
            filtered ? null : h('a', {href: 'create.html'}, 'Создать первую'))));
        return;
    }
    clear(tbody, rows.map(o => h('tr', {class: highlight.has(o.id) ? 'flash' : null},
        COLUMNS.map(c => h('td', {class: c.cls}, c.render(o))),
        h('td', {class: 'actions'},
            h('a', {class: 'btn btn--sm btn--ghost', href: `view.html?id=${o.id}`}, 'Открыть'),
            ' ',
            h('button', {type: 'button', class: 'btn btn--sm', onclick: () => edit(o.id)}, 'Изменить'),
            ' ',
            h('button', {type: 'button', class: 'btn btn--sm btn--danger', onclick: () => remove(o)}, 'Удалить')))));
}

function renderPager(data) {
    const total = data.totalElements;
    const from = total === 0 ? 0 : data.page * data.size + 1;
    const to = data.page * data.size + data.content.length;
    summary.textContent = total === 0 ? 'Записей нет' : `Показаны ${from}–${to} из ${total}`;

    const pages = Math.max(1, data.totalPages);
    const go = (page) => () => {
        state.page = page;
        refresh();
    };
    const sizeSelect = h('select', {
        'aria-label': 'Строк на странице',
        onchange: (e) => {
            state.size = Number(e.target.value);
            state.page = 0;
            refresh();
        },
    }, PAGE_SIZES.map(s => h('option', {value: String(s)}, `${s} на странице`)));
    sizeSelect.value = String(state.size);

    clear(pager,
        h('button', {type: 'button', class: 'btn btn--sm', disabled: data.page === 0, onclick: go(0), title: 'Первая'}, '«'),
        h('button', {type: 'button', class: 'btn btn--sm', disabled: data.page === 0, onclick: go(data.page - 1)}, '‹ Назад'),
        h('span', {class: 'page-info'}, `Страница ${data.page + 1} из ${pages}`),
        h('button', {type: 'button', class: 'btn btn--sm', disabled: data.page + 1 >= pages, onclick: go(data.page + 1)}, 'Вперёд ›'),
        h('button', {type: 'button', class: 'btn btn--sm', disabled: data.page + 1 >= pages, onclick: go(pages - 1), title: 'Последняя'}, '»'),
        sizeSelect);
}

// ---------- действия ----------

let requestCounter = 0;

async function load(highlightIds = []) {
    const current = ++requestCounter;
    try {
        const data = await api.listOrganizations({
            page: state.page, size: state.size, sort: state.sort, direction: state.direction, ...state.filters,
        });
        if (current !== requestCounter) {
            return;
        }
        // Если последнюю страницу опустошили (например, удаление у другого пользователя) — шаг назад
        if (data.totalPages > 0 && state.page >= data.totalPages) {
            state.page = data.totalPages - 1;
            writeState();
            return load(highlightIds);
        }
        clear(errorBox);
        renderRows(data.content, new Set(highlightIds));
        renderPager(data);
    } catch (e) {
        if (current === requestCounter) {
            clear(errorBox, e.message);
        }
    }
}

function refresh() {
    writeState();
    renderHead();
    return load();
}

function toggleSort(key) {
    if (state.sort === key) {
        state.direction = state.direction === 'asc' ? 'desc' : 'asc';
    } else {
        state.sort = key;
        state.direction = 'asc';
    }
    state.page = 0;
    refresh();
}

function applyFilters() {
    state.filters = {};
    for (const [key, input] of Object.entries(filterInputs)) {
        if (emptyChecks[key]?.checked) {
            state.filters[key] = '';
        } else if (input.value !== '') {
            state.filters[key] = input.value;
        }
    }
    state.page = 0;
    refresh();
}

function resetFilters() {
    Object.values(filterInputs).forEach(input => {
        input.value = '';
        input.disabled = false;
    });
    Object.values(emptyChecks).forEach(check => {
        check.checked = false;
    });
    applyFilters();
}

async function edit(id) {
    if (await openEditDialog(id)) {
        load([id]);
    }
}

async function remove(organization) {
    if (await deleteOrganizationWithConfirm(organization)) {
        load();
    }
}

// ---------- синхронизация с другими пользователями ----------

const changedIds = new Set();
const reloadAfterChanges = debounce(() => {
    const ids = [...changedIds];
    changedIds.clear();
    load(ids);
});

onChange((event) => {
    if (event.entity === 'ORGANIZATION' || event.entity === '*') {
        if (event.change === 'CREATED' || event.change === 'UPDATED') {
            event.ids.forEach(id => changedIds.add(id));
        }
        reloadAfterChanges();
    }
});

refresh();
