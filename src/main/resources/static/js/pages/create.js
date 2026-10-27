import {api} from '../api.js';
import {clear, h} from '../dom.js';
import {renderLayout, toast} from '../layout.js';
import {OrganizationForm} from '../organization-form.js';

renderLayout('create.html');

const app = document.getElementById('app');
const success = h('div', {class: 'alert alert--success', role: 'status'});

const form = new OrganizationForm({
    submitLabel: 'Создать организацию',
    onSubmit: async (request) => {
        const created = await api.createOrganization(request);
        form.reset();
        clear(success,
            `Организация #${created.id} «${created.name}» создана. `,
            h('a', {href: `view.html?id=${created.id}`}, 'Открыть'),
            ' · ',
            h('a', {href: 'index.html'}, 'Перейти к списку'));
        toast(`Организация #${created.id} создана`, 'success');
        window.scrollTo({top: 0, behavior: 'smooth'});
    },
});

app.append(success, form.element);
form.init().catch((e) => form.showErrors({}, `Не удалось загрузить существующие координаты и адреса: ${e.message}`));
