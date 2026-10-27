import {api, ApiError} from '../api.js';
import {clear, formatString, h, pluralOrganizations, TYPES, typeChip} from '../dom.js';
import {renderLayout, toast} from '../layout.js';
import {numberInput, parseInteger} from '../organization-form.js';
import {debounce, onChange} from '../realtime.js';

renderLayout('special.html');

const app = document.getElementById('app');
let uid = 0;

// ---------- общие помощники ----------

function field(label, control, hint = null) {
    const id = `sp-${++uid}`;
    control.id = id;
    const error = h('div', {class: 'error-text', id: `${id}-error`});
    control.setAttribute('aria-describedby', error.id);
    const wrap = h('div', {class: 'field'}, h('label', {for: id}, label), control,
        hint ? h('div', {class: 'hint'}, hint) : null, error);
    return {wrap, control, error};
}

function setError(f, message) {
    f.error.textContent = message ?? '';
    f.wrap.classList.toggle('invalid', Boolean(message));
}

function typeSelect() {
    return h('select', {}, h('option', {value: ''}, '— выберите —'), TYPES.map(t => h('option', {value: t}, t)));
}

function idInput(f) {
    return numberInput('integer', (msg) => setError(f(), msg));
}

/** Проверка поля ID: возвращает число или null, показывая ошибку у поля. */
function readId(f) {
    const parsed = parseInteger(f.control.value);
    let error = null;
    if (parsed.empty) {
        error = 'Введите ID организации';
    } else if (parsed.error) {
        error = parsed.error;
    } else if (parsed.value <= 0) {
        error = 'ID должен быть больше 0';
    }
    setError(f, error);
    return error ? null : parsed.value;
}

function operationCard(title, description, fields, buttonText, run, wide = false) {
    const result = h('div', {class: 'result', 'aria-live': 'polite'});
    const button = h('button', {type: 'submit', class: 'btn btn--primary'}, buttonText);
    const form = h('form', {class: 'inline-form', novalidate: true}, fields.map(f => f.wrap), button);
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        button.disabled = true;
        try {
            await run(result);
        } catch (err) {
            showError(result, fields, err);
        } finally {
            button.disabled = false;
        }
    });
    app.append(h('section', {class: wide ? 'card wide' : 'card'},
        h('h2', {}, title), h('p', {class: 'muted'}, description), form, result));
    return result;
}

function showError(result, fields, err) {
    if (err instanceof ApiError) {
        // Ошибки по полям (например, absorbedId) показываем у соответствующих полей
        for (const f of fields) {
            if (f.name && err.fieldErrors[f.name]) {
                setError(f, err.fieldErrors[f.name]);
            }
        }
        clear(result, h('div', {class: 'alert alert--error'}, err.message));
    } else {
        clear(result, h('div', {class: 'alert alert--error'}, `Непредвиденная ошибка: ${err.message}`));
        console.error(err);
    }
}

function organizationsTable(list) {
    if (!list.length) {
        return h('div', {class: 'alert alert--warning'}, 'Подходящих организаций нет');
    }
    return h('div', {class: 'table-wrap'}, h('table', {class: 'data'},
        h('thead', {}, h('tr', {},
            ['ID', 'Название', 'Полное название', 'Тип', 'Сотрудники'].map(t => h('th', {}, t)))),
        h('tbody', {}, list.map(o => h('tr', {},
            h('td', {class: 'num'}, h('a', {href: `view.html?id=${o.id}`}, o.id)),
            h('td', {}, o.name),
            h('td', {}, formatString(o.fullName)),
            h('td', {}, typeChip(o.type)),
            h('td', {class: 'num'}, o.employeesCount))))));
}

// Запросы 1–3 перевыполняются, когда данные меняют другие пользователи
const liveQueries = [];

// ---------- 1. Количество с заданным type ----------
{
    const typeField = field('Тип', typeSelect());
    let lastType = null;
    const result = operationCard(
        'Количество организаций заданного типа',
        'Возвращает число организаций, у которых поле type равно выбранному значению.',
        [typeField], 'Посчитать',
        async (out) => {
            const type = typeField.control.value;
            setError(typeField, type ? null : 'Выберите тип');
            if (!type) {
                return;
            }
            lastType = type;
            await show(out, type);
        });
    const show = async (out, type) => {
        const {count} = await api.countByType(type);
        clear(out, h('div', {class: 'big-number'}, count), h('div', {class: 'muted'}, `с типом ${type}`));
    };
    liveQueries.push(() => lastType && show(result, lastType));
}

// ---------- 2. Количество с fullName больше заданного ----------
{
    const nameField = field('fullName', h('input', {type: 'text', maxlength: 255, autocomplete: 'off'}),
        'Строки сравниваются в БД; пустое значение тоже допустимо');
    let lastValue = null;
    const result = operationCard(
        'Количество организаций с fullName больше заданного',
        'Возвращает число организаций, у которых полное название лексикографически больше введённой строки.',
        [nameField], 'Посчитать',
        async (out) => {
            lastValue = nameField.control.value;
            await show(out, lastValue);
        });
    const show = async (out, value) => {
        const {count} = await api.countFullNameGreater(value);
        clear(out, h('div', {class: 'big-number'}, count),
            h('div', {class: 'muted'}, value === '' ? 'с fullName больше пустой строки' : `с fullName > «${value}»`));
    };
    liveQueries.push(() => lastValue !== null && show(result, lastValue));
}

// ---------- 3. Организации с type меньше заданного ----------
{
    const typeField = field('Тип', typeSelect(), `Порядок: ${TYPES.join(' < ')}`);
    let lastType = null;
    const result = operationCard(
        'Организации с типом меньше заданного',
        'Возвращает массив организаций, у которых type меньше выбранного (в порядке объявления перечисления).',
        [typeField], 'Найти',
        async (out) => {
            const type = typeField.control.value;
            setError(typeField, type ? null : 'Выберите тип');
            if (!type) {
                return;
            }
            lastType = type;
            await show(out, type);
        }, true);
    const show = async (out, type) => {
        const list = await api.typeLessThan(type);
        clear(out, h('p', {class: 'muted'}, `Найдено: ${pluralOrganizations(list.length)}`), organizationsTable(list));
    };
    liveQueries.push(() => lastType && show(result, lastType));
}

// ---------- 4. Добавить сотрудника ----------
{
    let idField;
    idField = field('ID организации', idInput(() => idField));
    operationCard(
        'Добавить сотрудника',
        'Увеличивает количество сотрудников организации с указанным ID на одного.',
        [idField], 'Добавить сотрудника',
        async (out) => {
            const id = readId(idField);
            if (id === null) {
                return;
            }
            const o = await api.addEmployee(id);
            clear(out, h('div', {class: 'alert alert--success'},
                `В организации #${o.id} «${o.name}» теперь сотрудников: ${o.employeesCount}`));
            toast(`Сотрудник добавлен в организацию #${o.id}`, 'success');
        });
}

// ---------- 5. Поглощение ----------
{
    let absorberField;
    let absorbedField;
    absorberField = field('ID поглощающей', idInput(() => absorberField));
    absorberField.name = 'absorberId';
    absorbedField = field('ID поглощаемой', idInput(() => absorbedField));
    absorbedField.name = 'absorbedId';
    operationCard(
        'Поглощение организации',
        'Все сотрудники поглощаемой организации переходят в поглощающую (никто не увольняется), '
        + 'годовые обороты суммируются, поглощаемая организация удаляется.',
        [absorberField, absorbedField], 'Поглотить',
        async (out) => {
            const absorberId = readId(absorberField);
            const absorbedId = readId(absorbedField);
            if (absorberId === null || absorbedId === null) {
                return;
            }
            if (absorberId === absorbedId) {
                setError(absorbedField, 'Организация не может поглотить саму себя');
                return;
            }
            const r = await api.absorb(absorberId, absorbedId);
            clear(out, h('div', {class: 'alert alert--success'},
                `Организация #${r.absorber.id} «${r.absorber.name}» поглотила организацию #${r.absorbedId}. `
                + `Перешло сотрудников: ${r.transferredEmployees}, теперь их ${r.absorber.employeesCount}.`));
            toast(`Организация #${r.absorbedId} поглощена`, 'success');
        });
}

const rerun = debounce(() => liveQueries.forEach(q => q()?.catch?.(() => {
})), 300);
onChange((event) => {
    if (event.entity === 'ORGANIZATION' || event.entity === '*') {
        rerun();
    }
});
