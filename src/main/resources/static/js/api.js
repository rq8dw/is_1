// Клиент REST API. Все операции выполняются на сервере.

export class ApiError extends Error {
    constructor(status, message, fieldErrors = {}) {
        super(message);
        this.status = status;
        this.fieldErrors = fieldErrors;
    }
}

async function request(method, url, body) {
    const options = {method, headers: {Accept: 'application/json'}};
    if (body !== undefined) {
        options.headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(body);
    }

    let response;
    try {
        response = await fetch(url, options);
    } catch {
        throw new ApiError(0, 'Сервер недоступен. Проверьте подключение и повторите попытку.');
    }

    const text = await response.text();
    let data = null;
    if (text) {
        try {
            data = JSON.parse(text);
        } catch {
            data = null;
        }
    }
    if (!response.ok) {
        throw new ApiError(response.status, data?.message || `Ошибка сервера (${response.status})`,
            data?.fieldErrors || {});
    }
    return data;
}

/** @param keepEmpty передавать пустые строки (фильтр «значение равно пустой строке») */
function query(params, keepEmpty = false) {
    const search = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
        if (value !== undefined && value !== null && (keepEmpty || value !== '')) {
            search.set(key, value);
        }
    }
    const s = search.toString();
    return s ? `?${s}` : '';
}

export const api = {
    listOrganizations: (params) => request('GET', `api/organizations${query(params, true)}`),
    getOrganization: (id) => request('GET', `api/organizations/${encodeURIComponent(id)}`),
    createOrganization: (body) => request('POST', 'api/organizations', body),
    updateOrganization: (id, body) => request('PUT', `api/organizations/${encodeURIComponent(id)}`, body),
    deleteOrganization: (id) => request('DELETE', `api/organizations/${encodeURIComponent(id)}`),

    listCoordinates: () => request('GET', 'api/coordinates'),
    deleteCoordinates: (id) => request('DELETE', `api/coordinates/${encodeURIComponent(id)}`),
    listAddresses: () => request('GET', 'api/addresses'),
    deleteAddress: (id) => request('DELETE', `api/addresses/${encodeURIComponent(id)}`),

    countByType: (type) => request('GET', `api/special/count-by-type${query({type})}`),
    // fullName может быть пустой строкой, поэтому параметр передаётся всегда
    countFullNameGreater: (fullName) =>
        request('GET', `api/special/count-full-name-greater?fullName=${encodeURIComponent(fullName)}`),
    typeLessThan: (type) => request('GET', `api/special/type-less-than${query({type})}`),
    addEmployee: (id) => request('POST', `api/special/organizations/${encodeURIComponent(id)}/employees`),
    absorb: (absorberId, absorbedId) => request('POST', 'api/special/absorb', {absorberId, absorbedId}),
};
