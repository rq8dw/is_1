// Форма организации: используется на странице создания и в диалоге изменения.
// Проверки повторяют серверные, чтобы пользователь сразу видел причину ошибки.

import {api, ApiError} from './api.js';
import {clear, h, pluralOrganizations, TYPES} from './dom.js';
import {debounce, onChange} from './realtime.js';

const MAX_LENGTH = 255;
const MAX_X = 549;
const FLOAT_MAX = 3.4028234663852886e38;

let uidCounter = 0;
const uid = () => `of${++uidCounter}`;

// ---------- Разбор и ограничение ввода чисел ----------

export function parseInteger(raw) {
    const s = raw.trim();
    if (s === '') {
        return {empty: true};
    }
    if (!/^[+-]?\d+$/.test(s)) {
        return {error: 'Введите целое число'};
    }
    const value = Number(s);
    if (!Number.isSafeInteger(value)) {
        return {error: `Слишком большое число (максимум ${Number.MAX_SAFE_INTEGER})`};
    }
    return {value};
}

export function parseDecimal(raw) {
    const s = raw.trim().replace(',', '.');
    if (s === '') {
        return {empty: true};
    }
    if (!/^[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?$/.test(s)) {
        return {error: 'Введите число, например 12.5'};
    }
    const value = Number(s);
    if (!Number.isFinite(value)) {
        return {error: 'Слишком большое по модулю число'};
    }
    return {value};
}

/**
 * Текстовое поле, в которое нельзя ввести символы, недопустимые для числа.
 * При попытке ввода такого символа рядом с полем на секунду появляется подсказка.
 */
export function numberInput(kind, onRejected) {
    const disallowed = kind === 'integer' ? /[^\d+-]/g : /[^\d.,eE+-]/g;
    const input = h('input', {
        type: 'text',
        inputmode: kind === 'integer' ? 'numeric' : 'decimal',
        autocomplete: 'off',
        maxlength: 40,
    });
    input.addEventListener('input', () => {
        const {value} = input;
        const cleaned = value.replace(disallowed, '');
        if (cleaned !== value) {
            const caret = value.slice(0, input.selectionStart ?? value.length).replace(disallowed, '').length;
            input.value = cleaned;
            input.setSelectionRange(caret, caret);
            onRejected?.(kind === 'integer'
                ? 'Можно вводить только цифры'
                : 'Можно вводить только цифры, знак, точку или запятую');
        }
    });
    return input;
}

// ---------- Форма ----------

export class OrganizationForm {

    /**
     * @param submitLabel текст кнопки отправки
     * @param onSubmit async (request) => void; ApiError отображается в форме
     * @param onCancel необязательная кнопка "Отмена"
     */
    constructor({submitLabel = 'Сохранить', onSubmit, onCancel = null}) {
        this.onSubmit = onSubmit;
        this.fields = {};
        this.submitted = false;
        this.coordinatesOptions = [];
        this.addressOptions = [];
        this.element = this.build(submitLabel, onCancel);
        this.unsubscribe = onChange(debounce(() => this.loadOptions().catch(() => {
        }), 200));
    }

    async init(organization = null) {
        await this.loadOptions();
        if (organization) {
            this.setValues(organization);
        }
    }

    destroy() {
        this.unsubscribe();
    }

    // ----- построение -----

    build(submitLabel, onCancel) {
        this.generalError = h('div', {class: 'alert alert--error', role: 'alert'});
        this.inputs = {
            name: h('input', {type: 'text', maxlength: MAX_LENGTH, autocomplete: 'off'}),
            fullName: h('input', {type: 'text', maxlength: MAX_LENGTH, autocomplete: 'off'}),
            type: h('select', {},
                h('option', {value: ''}, '— выберите —'),
                TYPES.map(t => h('option', {value: t}, t))),
            employeesCount: numberInput('integer', msg => this.flashError('employeesCount', msg)),
            annualTurnover: numberInput('integer', msg => this.flashError('annualTurnover', msg)),
            rating: numberInput('decimal', msg => this.flashError('rating', msg)),
        };

        const main = h('div', {class: 'form-grid'},
            this.field('name', 'Название', this.inputs.name,
                {required: true, hint: 'Не может быть пустым, до 255 символов'}),
            this.field('fullName', 'Полное название', this.inputs.fullName,
                {required: true, hint: 'Обязательно, но может быть пустой строкой'}),
            this.field('type', 'Тип', this.inputs.type, {required: true}),
            this.field('employeesCount', 'Количество сотрудников', this.inputs.employeesCount,
                {required: true, hint: 'Целое число больше 0'}),
            this.field('annualTurnover', 'Годовой оборот', this.inputs.annualTurnover,
                {hint: 'Необязательно. Целое число больше 0'}),
            this.field('rating', 'Рейтинг', this.inputs.rating,
                {hint: 'Необязательно. Число больше 0'}));

        this.coordinates = this.buildCoordinatesPicker();
        this.officialAddress = this.buildAddressPicker('officialAddress', 'Официальный адрес');
        this.postalAddress = this.buildAddressPicker('postalAddress', 'Почтовый адрес');

        this.submitButton = h('button', {type: 'submit', class: 'btn btn--primary'}, submitLabel);
        const actions = h('div', {class: 'form-actions'},
            onCancel ? h('button', {type: 'button', class: 'btn', onclick: onCancel}, 'Отмена') : null,
            this.submitButton);

        const form = h('form', {novalidate: true, autocomplete: 'off'},
            this.generalError,
            h('div', {class: 'form-section-title'}, 'Основные данные'),
            main,
            h('div', {class: 'form-section-title'}, 'Связанные объекты'),
            h('div', {class: 'relations-grid'},
                this.coordinates.element, this.officialAddress.element, this.postalAddress.element),
            actions);

        form.addEventListener('submit', (e) => {
            e.preventDefault();
            this.submit();
        });
        // После первой попытки отправки ошибки пересчитываются на лету
        const revalidate = () => {
            if (this.submitted) {
                this.showErrors(this.collect().errors);
            }
        };
        form.addEventListener('input', revalidate);
        form.addEventListener('change', revalidate);
        return form;
    }

    field(key, label, control, {required = false, hint = null} = {}) {
        const id = `${uid()}-${key.replace('.', '-')}`;
        control.id = id;
        const error = h('div', {class: 'error-text', id: `${id}-error`});
        control.setAttribute('aria-describedby', error.id);
        const wrap = h('div', {class: 'field'},
            h('label', {for: id}, label, required ? h('span', {class: 'req'}, ' *') : null),
            control,
            hint ? h('div', {class: 'hint'}, hint) : null,
            error);
        this.fields[key] = {wrap, error, control};
        return wrap;
    }

    /** Переключатель режима: несколько радиокнопок в виде сегментированного контрола. */
    segmented(options) {
        const name = uid();
        const radios = {};
        const el = h('div', {class: 'segmented', role: 'radiogroup'},
            options.map(([value, label]) => {
                radios[value] = h('input', {type: 'radio', name, value});
                return h('label', {}, radios[value], label);
            }));
        return {el, radios};
    }

    buildCoordinatesPicker() {
        const {el: switcher, radios} = this.segmented([['new', 'Новые'], ['existing', 'Существующие']]);
        const x = numberInput('decimal', msg => this.flashError('coordinates.x', msg));
        const y = numberInput('decimal', msg => this.flashError('coordinates.y', msg));
        const select = h('select', {});
        const selectId = uid();
        select.id = selectId;

        const newBox = h('div', {class: 'form-grid'},
            this.field('coordinates.x', 'X', x, {required: true, hint: 'Не больше 549'}),
            this.field('coordinates.y', 'Y', y, {required: true}));
        const existingBox = h('div', {class: 'field'},
            h('label', {for: selectId}, 'Выберите из уже созданных'),
            select);
        const error = h('div', {class: 'error-text'});
        const element = h('fieldset', {class: 'group'},
            h('legend', {}, 'Координаты', h('span', {class: 'req'}, ' *')),
            switcher, newBox, existingBox, error);
        this.fields.coordinates = {wrap: element, error, control: select};

        const picker = {element, radios, x, y, select, mode: () => (radios.existing.checked ? 'existing' : 'new')};
        picker.sync = () => {
            newBox.hidden = picker.mode() !== 'new';
            existingBox.hidden = picker.mode() !== 'existing';
        };
        radios.new.addEventListener('change', () => {
            // Удобно поправить значения выбранных координат: получатся новые координаты
            const selected = this.coordinatesOptions.find(c => String(c.id) === select.value);
            if (selected && x.value === '' && y.value === '') {
                x.value = String(selected.x);
                y.value = String(selected.y);
            }
            picker.sync();
        });
        radios.existing.addEventListener('change', picker.sync);
        radios.new.checked = true;
        picker.sync();
        return picker;
    }

    buildAddressPicker(key, title) {
        const {el: switcher, radios} = this.segmented([['none', 'Нет'], ['new', 'Новый'], ['existing', 'Существующий']]);
        const zip = h('input', {type: 'text', maxlength: MAX_LENGTH, autocomplete: 'off'});
        const select = h('select', {});
        const selectId = uid();
        select.id = selectId;

        const newBox = this.field(`${key}.zipCode`, 'Почтовый индекс (zipCode)', zip,
            {required: true, hint: 'Может быть пустой строкой, до 255 символов'});
        const existingBox = h('div', {class: 'field'},
            h('label', {for: selectId}, 'Выберите из уже созданных'),
            select);
        const error = h('div', {class: 'error-text'});
        const element = h('fieldset', {class: 'group'},
            h('legend', {}, title),
            switcher, newBox, existingBox, error);
        this.fields[key] = {wrap: element, error, control: select};

        const picker = {
            key, element, radios, zip, select,
            mode: () => Object.keys(radios).find(m => radios[m].checked),
        };
        picker.sync = () => {
            newBox.hidden = picker.mode() !== 'new';
            existingBox.hidden = picker.mode() !== 'existing';
        };
        Object.values(radios).forEach(r => r.addEventListener('change', picker.sync));
        radios.none.checked = true;
        picker.sync();
        return picker;
    }

    // ----- список существующих вспомогательных объектов -----

    async loadOptions() {
        const [coordinates, addresses] = await Promise.all([api.listCoordinates(), api.listAddresses()]);
        this.coordinatesOptions = coordinates;
        this.addressOptions = addresses;

        this.fillSelect('coordinates', this.coordinates, coordinates,
            c => `#${c.id}: (${c.x}; ${c.y}) — ${pluralOrganizations(c.usages)}`,
            id => `Выбранные координаты #${id} удалены другим пользователем, выберите другие`);
        for (const picker of [this.officialAddress, this.postalAddress]) {
            this.fillSelect(picker.key, picker, addresses,
                a => `#${a.id}: ${a.zipCode === '' ? '«пустой индекс»' : a.zipCode} — ${pluralOrganizations(a.usages)}`,
                id => `Выбранный адрес #${id} удалён другим пользователем, выберите другой`);
        }
    }

    fillSelect(key, picker, items, label, goneMessage) {
        const {select, radios} = picker;
        const current = select.value;
        clear(select,
            h('option', {value: ''}, items.length ? '— выберите —' : '— пока нет ни одного —'),
            items.map(item => h('option', {value: String(item.id)}, label(item))));
        if (current && items.some(item => String(item.id) === current)) {
            select.value = current;
        } else if (current && picker.mode() === 'existing') {
            this.setFieldError(key, goneMessage(current));
        }
        radios.existing.disabled = items.length === 0 && picker.mode() !== 'existing';
    }

    // ----- значения -----

    setValues(o) {
        this.inputs.name.value = o.name;
        this.inputs.fullName.value = o.fullName;
        this.inputs.type.value = o.type;
        this.inputs.employeesCount.value = String(o.employeesCount);
        this.inputs.annualTurnover.value = o.annualTurnover ?? '';
        this.inputs.rating.value = o.rating ?? '';

        const c = this.coordinates;
        c.radios.existing.disabled = false;
        c.radios.existing.checked = true;
        c.select.value = String(o.coordinates.id);
        c.x.value = String(o.coordinates.x);
        c.y.value = String(o.coordinates.y);
        c.sync();

        for (const [picker, address] of [[this.officialAddress, o.officialAddress], [this.postalAddress, o.postalAddress]]) {
            picker.zip.value = address ? address.zipCode : '';
            if (address) {
                picker.radios.existing.disabled = false;
                picker.radios.existing.checked = true;
                picker.select.value = String(address.id);
            } else {
                picker.radios.none.checked = true;
            }
            picker.sync();
        }
        this.submitted = false;
        this.showErrors({});
    }

    reset() {
        this.element.reset();
        this.coordinates.radios.new.checked = true;
        this.coordinates.sync();
        for (const picker of [this.officialAddress, this.postalAddress]) {
            picker.radios.none.checked = true;
            picker.sync();
        }
        this.submitted = false;
        this.showErrors({});
    }

    /** Собирает запрос и проверяет его так же, как сервер. */
    collect() {
        const errors = {};
        const i = this.inputs;

        const name = i.name.value;
        if (name.trim() === '') {
            errors.name = 'Название не может быть пустым';
        }
        const type = i.type.value;
        if (!type) {
            errors.type = 'Выберите тип организации';
        }

        const request = {
            name,
            coordinates: this.collectCoordinates(errors),
            officialAddress: this.collectAddress(this.officialAddress, errors),
            annualTurnover: this.readPositiveInteger('annualTurnover', null,
                'Годовой оборот должен быть больше 0', errors),
            employeesCount: this.readPositiveInteger('employeesCount', 'Укажите количество сотрудников',
                'Количество сотрудников должно быть больше 0', errors),
            rating: this.readRating(errors),
            fullName: i.fullName.value,
            type: type || null,
            postalAddress: this.collectAddress(this.postalAddress, errors),
        };
        return {request, errors};
    }

    /** @param requiredMessage null — поле необязательное */
    readPositiveInteger(key, requiredMessage, positiveMessage, errors) {
        const parsed = parseInteger(this.inputs[key].value);
        if (parsed.empty) {
            if (requiredMessage) {
                errors[key] = requiredMessage;
            }
            return null;
        }
        if (parsed.error) {
            errors[key] = parsed.error;
            return null;
        }
        if (parsed.value <= 0) {
            errors[key] = positiveMessage;
            return null;
        }
        return parsed.value;
    }

    readRating(errors) {
        const parsed = parseDecimal(this.inputs.rating.value);
        if (parsed.empty) {
            return null;
        }
        if (parsed.error) {
            errors.rating = parsed.error;
            return null;
        }
        // Рейтинг хранится как float: слишком маленькое значение превратится в 0
        if (Math.fround(parsed.value) <= 0) {
            errors.rating = 'Рейтинг должен быть больше 0';
            return null;
        }
        if (parsed.value > FLOAT_MAX) {
            errors.rating = 'Рейтинг слишком большой (максимум ≈ 3.4e38)';
            return null;
        }
        return parsed.value;
    }

    collectCoordinates(errors) {
        const c = this.coordinates;
        if (c.mode() === 'existing') {
            if (!c.select.value) {
                errors.coordinates = 'Выберите координаты из списка';
                return null;
            }
            return {id: Number(c.select.value)};
        }
        const x = parseDecimal(c.x.value);
        const y = parseDecimal(c.y.value);
        if (x.empty) {
            errors['coordinates.x'] = 'Укажите X';
        } else if (x.error) {
            errors['coordinates.x'] = x.error;
        } else if (x.value > MAX_X) {
            errors['coordinates.x'] = 'X не может быть больше 549';
        }
        if (y.empty) {
            errors['coordinates.y'] = 'Укажите Y';
        } else if (y.error) {
            errors['coordinates.y'] = y.error;
        }
        return {x: x.value ?? null, y: y.value ?? null};
    }

    collectAddress(picker, errors) {
        switch (picker.mode()) {
            case 'new':
                return {zipCode: picker.zip.value};
            case 'existing':
                if (!picker.select.value) {
                    errors[picker.key] = 'Выберите адрес из списка';
                    return null;
                }
                return {id: Number(picker.select.value)};
            default:
                return null;
        }
    }

    // ----- ошибки -----

    showErrors(fieldErrors, generalMessage = null) {
        const unknown = [];
        for (const [key, field] of Object.entries(this.fields)) {
            const message = fieldErrors[key] ?? null;
            field.error.textContent = message ?? '';
            field.wrap.classList.toggle('invalid', Boolean(message));
        }
        for (const [key, message] of Object.entries(fieldErrors)) {
            if (!this.fields[this.fieldKey(key)]) {
                unknown.push(message);
            } else if (!this.fields[key]) {
                this.setFieldError(this.fieldKey(key), message);
            }
        }
        if (generalMessage || unknown.length) {
            clear(this.generalError,
                generalMessage,
                unknown.length ? h('ul', {}, unknown.map(m => h('li', {}, m))) : null);
        } else {
            clear(this.generalError);
        }
    }

    /** "coordinates.id" -> "coordinates": такие ошибки показываются у группы полей. */
    fieldKey(key) {
        return this.fields[key] ? key : key.split('.')[0];
    }

    setFieldError(key, message) {
        const field = this.fields[key];
        if (field) {
            field.error.textContent = message;
            field.wrap.classList.add('invalid');
        }
    }

    flashError(key, message) {
        const field = this.fields[key];
        if (!field || field.error.textContent) {
            return;
        }
        field.error.textContent = message;
        setTimeout(() => {
            if (field.error.textContent === message) {
                field.error.textContent = '';
            }
        }, 1800);
    }

    focusFirstInvalid() {
        const invalid = Object.values(this.fields).find(f => f.wrap.classList.contains('invalid'));
        const target = invalid?.wrap.querySelector('input:not([type=radio]), select');
        (target ?? invalid?.wrap)?.focus?.();
        invalid?.wrap.scrollIntoView({block: 'center', behavior: 'smooth'});
    }

    setSubmitEnabled(enabled) {
        this.blocked = !enabled;
        this.submitButton.disabled = this.blocked;
    }

    // ----- отправка -----

    async submit() {
        this.submitted = true;
        const {request, errors} = this.collect();
        if (Object.keys(errors).length) {
            this.showErrors(errors, 'Исправьте ошибки в выделенных полях');
            this.focusFirstInvalid();
            return;
        }
        this.showErrors({});
        this.submitButton.disabled = true;
        try {
            await this.onSubmit(request);
        } catch (e) {
            if (e instanceof ApiError) {
                this.showErrors(e.fieldErrors, e.message);
                this.focusFirstInvalid();
            } else {
                this.showErrors({}, 'Непредвиденная ошибка: ' + e.message);
                console.error(e);
            }
        } finally {
            this.submitButton.disabled = Boolean(this.blocked);
        }
    }
}
