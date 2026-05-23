const API = window.API_BASE || '/api/v1';
const STORE = {
    get access() { return localStorage.getItem('bts_access'); },
    set access(v) { v ? localStorage.setItem('bts_access', v) : localStorage.removeItem('bts_access'); },
    get refresh() { return localStorage.getItem('bts_refresh'); },
    set refresh(v) { v ? localStorage.setItem('bts_refresh', v) : localStorage.removeItem('bts_refresh'); },
    get user() { return localStorage.getItem('bts_user'); },
    set user(v) { v ? localStorage.setItem('bts_user', v) : localStorage.removeItem('bts_user'); }
};

/* ---------- Tabs ---------- */
document.querySelectorAll('.tabs button').forEach(btn => {
    btn.addEventListener('click', () => {
        document.querySelectorAll('.tabs button').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        const id = btn.dataset.tab;
        document.querySelectorAll('section').forEach(s => s.classList.toggle('active', s.dataset.panel === id));
    });
});

/* ---------- Auth status badge ---------- */
function refreshAuthBadge() {
    const badge = document.getElementById('authStatus');
    const btn = document.getElementById('logoutBtn');
    if (STORE.access) {
        badge.textContent = 'Connecté' + (STORE.user ? ' • ' + STORE.user : '');
        badge.className = 'badge ok';
        btn.hidden = false;
    } else {
        badge.textContent = 'Non connecté';
        badge.className = 'badge muted';
        btn.hidden = true;
    }
}
document.getElementById('logoutBtn').addEventListener('click', () => {
    STORE.access = null;
    STORE.refresh = null;
    STORE.user = null;
    refreshAuthBadge();
});
refreshAuthBadge();

/* ---------- HTTP helpers ---------- */
async function request(method, path, body, requireAuth = false) {
    const headers = { 'Accept': 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (requireAuth) {
        if (!STORE.access) throw new Error('Vous devez d\'abord vous connecter (onglet Auth).');
        headers['Authorization'] = 'Bearer ' + STORE.access;
    }
    const opts = { method, headers };
    if (body !== undefined) opts.body = JSON.stringify(body);
    const res = await fetch(API + path, opts);
    const text = await res.text();
    let parsed;
    try { parsed = text ? JSON.parse(text) : null; } catch { parsed = text; }
    return { status: res.status, ok: res.ok, body: parsed };
}

function show(elId, result) {
    const el = document.getElementById(elId);
    const cls = result.ok ? 'ok' : 'err';
    const head = `<span class="${cls}">HTTP ${result.status} ${result.ok ? 'OK' : 'ERROR'}</span>\n`;
    el.innerHTML = head + (typeof result.body === 'string'
        ? escapeHtml(result.body)
        : escapeHtml(JSON.stringify(result.body, null, 2)));
}

function escapeHtml(s) {
    return String(s).replace(/[&<>]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]));
}

/* ---------- Auth actions ---------- */
async function signup() {
    try {
        const r = await request('POST', '/auth/signup', {
            username: val('su_username'),
            email: val('su_email'),
            password: val('su_password')
        });
        show('out_signup', r);
    } catch (e) { showError('out_signup', e); }
}

async function login() {
    try {
        const r = await request('POST', '/auth/login', {
            email: val('li_email'),
            password: val('li_password')
        });
        show('out_login', r);
        if (r.ok && r.body.accessToken) {
            STORE.access = r.body.accessToken;
            STORE.refresh = r.body.refreshToken;
            STORE.user = val('li_email');
            refreshAuthBadge();
        }
    } catch (e) { showError('out_login', e); }
}

async function refresh() {
    try {
        if (!STORE.refresh) throw new Error('Pas de refresh token en mémoire — connecte-toi d\'abord.');
        const r = await request('POST', '/auth/refresh', { refreshToken: STORE.refresh });
        show('out_refresh', r);
        if (r.ok && r.body.accessToken) {
            STORE.access = r.body.accessToken;
            refreshAuthBadge();
        }
    } catch (e) { showError('out_refresh', e); }
}

/* ---------- Geolocate ---------- */
async function geolocate() {
    try {
        const r = await request('POST', '/geolocate/priority', {
            mcc: val('g_mcc'),
            mnc: val('g_mnc'),
            lac: val('g_lac'),
            cellId: val('g_cellid')
        }, true);
        show('out_geo', r);
    } catch (e) { showError('out_geo', e); }
}

function fillOrange() {
    set('g_mcc', '624'); set('g_mnc', '02'); set('g_lac', '100'); set('g_cellid', '5660227');
}
function fillMtn() {
    set('g_mcc', '624'); set('g_mnc', '01'); set('g_lac', '102'); set('g_cellid', '21031');
}

/* ---------- Cells by area ---------- */
async function cellsByArea() {
    try {
        const q = encodeURIComponent(val('c_query'));
        const p = encodeURIComponent(val('c_provider'));
        const r = await request('GET', `/cells/by-area?query=${q}&provider=${p}`, undefined, true);
        show('out_cells', r);
    } catch (e) { showError('out_cells', e); }
}

/* ---------- Coverage ---------- */
async function coverage() {
    try {
        const r = await request('POST', '/coverage/penetration', {
            area: val('cv_area'),
            radiusMeters: parseFloat(val('cv_radius')),
            provider: val('cv_provider')
        }, true);
        show('out_cov', r);
    } catch (e) { showError('out_cov', e); }
}

/* ---------- utils ---------- */
function val(id) { return document.getElementById(id).value.trim(); }
function set(id, v) { document.getElementById(id).value = v; }
function showError(elId, e) {
    document.getElementById(elId).innerHTML = `<span class="err">ERROR</span>\n${escapeHtml(e.message || String(e))}`;
}

window.signup = signup;
window.login = login;
window.refresh = refresh;
window.geolocate = geolocate;
window.fillOrange = fillOrange;
window.fillMtn = fillMtn;
window.cellsByArea = cellsByArea;
window.coverage = coverage;
