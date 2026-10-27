// Модальные окна: изменение организации и подтверждение удаления.

import {api} from './api.js';
import {clear, h} from './dom.js';
import {toast} from './layout.js';
import {OrganizationForm} from './organization-form.js';
import {onChange} from './realtime.js';

/** @returns Promise<boolean> — подтвердил ли пользователь действие */
export function confirmDialog({title, message, confirmText = 'Подтвердить', danger = false}) {
    return new Promise((resolve) => {
        const cancel = h('button', {type: 'button', class: 'btn', onclick: () => dialog.close('cancel')}, 'Отмена');
        const dialog = h('dialog', {class: 'modal modal--small'},
            h('div', {class: 'modal__head'}, h('h2', {}, title)),
            h('div', {class: 'modal__body'},
                [message].flat().map(line => h('p', {}, line)),
                h('div', {class: 'form-actions'},
                    cancel,
                    h('button', {
                        type: 'button',
                        class: `btn ${danger ? 'btn--danger' : 'btn--primary'}`,
                        onclick: () => dialog.close('confirm'),
                    }, confirmText))));
        dialog.addEventListener('close', () => {
            resolve(dialog.returnValue === 'confirm');
            dialog.remove();
        });
        document.body.append(dialog);
        dialog.showModal();
        cancel.focus();
    });
}

/**
 * Отдельное окно изменения организации.
 * @returns Promise с сохранённой организацией или null, если окно закрыли без сохранения
 */
export function openEditDialog(organizationId) {
    const id = Number(organizationId);
    return new Promise((resolve) => {
        let saved = null;
        let saving = false;

        const notice = h('div', {class: 'alert alert--warning', role: 'status'});
        const body = h('div', {class: 'modal__body'}, h('p', {class: 'muted'}, 'Загрузка…'));
        const dialog = h('dialog', {class: 'modal'},
            h('div', {class: 'modal__head'},
                h('h2', {}, `Изменение организации #${id}`),
                h('button', {type: 'button', class: 'icon-btn', 'aria-label': 'Закрыть', onclick: () => dialog.close()}, '×')),
            body);

        const form = new OrganizationForm({
            submitLabel: 'Сохранить изменения',
            onCancel: () => dialog.close(),
            onSubmit: async (request) => {
                saving = true;
                try {
                    saved = await api.updateOrganization(id, request);
                    toast(`Организация #${id} сохранена`, 'success');
                    dialog.close();
                } finally {
                    saving = false;
                }
            },
        });

        const load = async () => {
            clear(notice);
            form.setSubmitEnabled(true);
            try {
                const organization = await api.getOrganization(id);
                await form.init(organization);
                clear(body, notice, form.element);
            } catch (e) {
                clear(body,
                    h('div', {class: 'alert alert--error'}, e.message),
                    h('div', {class: 'form-actions'},
                        h('button', {type: 'button', class: 'btn', onclick: () => dialog.close()}, 'Закрыть')));
            }
        };

        // Пока окно открыто, объект могут изменить или удалить другие пользователи
        const unsubscribe = onChange((event) => {
            if (saving || event.entity !== 'ORGANIZATION' || !event.ids.includes(id)) {
                return;
            }
            if (event.change === 'DELETED') {
                clear(notice, 'Эту организацию только что удалил другой пользователь — сохранить изменения не получится.');
                form.setSubmitEnabled(false);
            } else if (event.change === 'UPDATED') {
                clear(notice, 'Другой пользователь только что изменил эту организацию. ',
                    h('button', {type: 'button', class: 'btn btn--sm', onclick: load}, 'Загрузить актуальные данные'));
            }
        });

        dialog.addEventListener('close', () => {
            unsubscribe();
            form.destroy();
            dialog.remove();
            resolve(saved);
        });
        document.body.append(dialog);
        dialog.showModal();
        load();
    });
}

/** @returns Promise<boolean> — была ли организация удалена */
export async function deleteOrganizationWithConfirm(organization) {
    const confirmed = await confirmDialog({
        title: 'Удаление организации',
        message: [
            `Организация #${organization.id} «${organization.name}» будет удалена без возможности восстановления.`,
            'Связанные с ней координаты и адреса тоже будут удалены, если их больше не использует ни одна организация.',
        ],
        confirmText: 'Удалить',
        danger: true,
    });
    if (!confirmed) {
        return false;
    }
    try {
        await api.deleteOrganization(organization.id);
        toast(`Организация #${organization.id} удалена`, 'success');
        return true;
    } catch (e) {
        toast(e.message, 'error', 6000);
        return false;
    }
}
