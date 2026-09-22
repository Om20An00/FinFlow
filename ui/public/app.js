'use strict';
const C = window.FINFLOW;
const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];
const state = { token: null, refresh: null, exp: 0, me: null, users: [], tab: 'dashboard', lastCid: '' };

/* ------------------------------------------------------------------ helpers */
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmt = n => '₹' + Number(n || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const ago = iso => {
  if (!iso) return '';
  const s = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
  if (s < 5) return 'just now'; if (s < 60) return Math.floor(s) + 's ago';
  if (s < 3600) return Math.floor(s / 60) + 'm ago'; if (s < 86400) return Math.floor(s / 3600) + 'h ago';
  return new Date(iso).toLocaleDateString();
};
const short = id => (id || '').slice(0, 8);
const b64url = buf => btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const rand = n => b64url(crypto.getRandomValues(new Uint8Array(n)));
const uuid = () => crypto.randomUUID();
function toast(msg, ms = 3500) {
  const t = $('#toast'); t.textContent = msg; t.classList.remove('hidden');
  clearTimeout(toast._t); toast._t = setTimeout(() => t.classList.add('hidden'), ms);
}

/* ------------------------------------------------------------------ OAuth2 / OIDC (Authorization Code + PKCE) */
async function login() {
  const verifier = rand(48);
  const challenge = b64url(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier)));
  const st = rand(16);
  sessionStorage.setItem('pkce', JSON.stringify({ verifier, st }));
  const p = new URLSearchParams({
    client_id: C.CLIENT_ID, redirect_uri: location.origin + '/', response_type: 'code', scope: 'openid',
    code_challenge: challenge, code_challenge_method: 'S256', state: st
  });
  location.href = `${C.KC}/auth?${p}`;
}
async function handleCallback() {
  const q = new URLSearchParams(location.search);
  if (!q.get('code')) return false;
  const saved = JSON.parse(sessionStorage.getItem('pkce') || '{}');
  history.replaceState({}, '', '/');
  if (q.get('state') !== saved.st) return false;
  const body = new URLSearchParams({
    grant_type: 'authorization_code', client_id: C.CLIENT_ID, code: q.get('code'),
    redirect_uri: location.origin + '/', code_verifier: saved.verifier
  });
  const r = await fetch(`${C.KC}/token`, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body });
  if (!r.ok) return false;
  setTokens(await r.json());
  return true;
}
function setTokens(t) {
  state.token = t.access_token; state.refresh = t.refresh_token;
  state.exp = JSON.parse(atob(t.access_token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))).exp * 1000;
  sessionStorage.setItem('tok', JSON.stringify({ a: state.token, r: state.refresh }));
}
function loadStored() {
  try {
    const s = JSON.parse(sessionStorage.getItem('tok') || 'null');
    if (s) setTokens({ access_token: s.a, refresh_token: s.r });
  } catch { sessionStorage.removeItem('tok'); }
}
async function ensureFresh() {
  if (state.exp - Date.now() > 30000) return;
  if (!state.refresh) return logout(true);
  const body = new URLSearchParams({ grant_type: 'refresh_token', client_id: C.CLIENT_ID, refresh_token: state.refresh });
  const r = await fetch(`${C.KC}/token`, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body });
  if (!r.ok) return logout(true);
  setTokens(await r.json());
}
function logout(silent) {
  sessionStorage.removeItem('tok');
  if (silent) { location.href = '/'; return; }
  location.href = `${C.KC}/logout?client_id=${C.CLIENT_ID}&post_logout_redirect_uri=${encodeURIComponent(location.origin + '/')}`;
}

/* ------------------------------------------------------------------ API client */
async function api(path, { method = 'GET', body, headers = {}, auth = true, cid, token } = {}) {
  const h = { ...headers };
  if (auth) { await ensureFresh(); h.Authorization = 'Bearer ' + (token || state.token); }
  const id = cid || 'ui-' + rand(6);
  h['X-Correlation-Id'] = id;
  if (body !== undefined) h['Content-Type'] = 'application/json';
  const r = await fetch(C.API + path, { method, headers: h, body: body !== undefined ? JSON.stringify(body) : undefined });
  const text = await r.text();
  let data = null; try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  return { ok: r.ok, status: r.status, data, cid: r.headers.get('X-Correlation-Id') || id, replayed: r.headers.get('Idempotent-Replayed') };
}

/* ------------------------------------------------------------------ app bootstrap */
async function startApp() {
  $('#login').classList.add('hidden'); $('#app').classList.remove('hidden');
  const me = await api('/api/v1/users/me');
  if (!me.ok) { toast('Could not load profile (' + me.status + ')'); return; }
  state.me = me.data;
  $('#who-name').textContent = me.data.displayName || me.data.username;
  $('#avatar').textContent = (me.data.username || '?')[0].toUpperCase();
  $('#who-roles').innerHTML = me.data.roles.map(r => `<span class="role">${esc(r)}</span>`).join('');
  $$('#nav a[data-role]').forEach(a => {
    const need = a.dataset.role.split(',');
    a.classList.toggle('hidden', !need.some(r => me.data.roles.includes(r)));
  });
  $('#links').innerHTML = [
    ['Grafana dashboards', C.LINKS.grafana, 'metrics'], ['Kafka UI', C.LINKS.kafka, 'topics · consumer groups · DLQ'],
    ['Keycloak console', C.LINKS.keycloak, 'admin / admin'], ['Prometheus', C.LINKS.prometheus, 'raw metrics']
  ].map(([n, u, d]) => `<a href="${u}" target="_blank" rel="noopener">${n}<span>${d}</span></a>`).join('');
  await loadUsers();
  switchTab('dashboard');
  setInterval(tick, 3000);
}
async function loadUsers() {
  const r = await api('/api/v1/users');
  if (!r.ok) return;
  state.users = r.data;
  const others = r.data.filter(u => u.id !== state.me.id);
  const sel = $('#p-to'), keep = sel.value;
  sel.innerHTML = others.map(u => `<option value="${esc(u.id)}">${esc(u.displayName)} (@${esc(u.username)})</option>`).join('');
  if (keep) sel.value = keep;
}
function switchTab(tab) {
  state.tab = tab;
  $$('.tab').forEach(t => t.classList.toggle('hidden', t.id !== 'tab-' + tab));
  $$('#nav a').forEach(a => a.classList.toggle('active', a.dataset.tab === tab));
  tick();
}
async function tick() {
  if (document.hidden || !state.me) return;
  try {
    if (state.tab === 'dashboard') await Promise.all([loadWallet(), loadNotifs(), loadLedger()]);
    else if (state.tab === 'payments') await loadPayments();
    else if (state.tab === 'insights') await loadInsights();
    else if (state.tab === 'admin') await Promise.all([loadAdminWallets(), loadAudit(), loadDlq()]);
  } catch (e) { console.warn(e); }
}

/* ------------------------------------------------------------------ dashboard */
async function loadWallet() {
  const r = await api('/api/v1/wallets/me');
  if (!r.ok) { $('#w-balance').textContent = '₹ —'; $('#w-sub').textContent = r.status === 404 ? 'Setting up your wallet (waiting for the UserRegistered event) …' : 'Error ' + r.status; return; }
  const w = r.data;
  $('#w-balance').textContent = fmt(w.balance);
  $('#w-sub').textContent = `@${w.username} · ${w.currency} · updated ${ago(w.updatedAt)}`;
  $('#w-badges').innerHTML =
    (w.frozen ? '<span class="badge frozen">frozen</span>' : '') +
    `<span class="badge ${w.source === 'redis' ? 'redis' : 'pg'}" title="Reads are cached in Redis for 15s; PostgreSQL is the source of truth">${w.source === 'redis' ? 'served from redis' : 'served from postgres'}</span>`;
}
async function loadNotifs() {
  const r = await api('/api/v1/notifications/me');
  if (!r.ok) return;
  $('#notif-list').innerHTML = r.data.length ? r.data.map(n => `
    <div class="item"><div><b>${esc(n.title)}</b><small>${esc(n.message)}</small></div><small>${ago(n.createdAt)}</small></div>`).join('')
    : '<div class="muted">No notifications yet. Send a payment and watch this fill up via Kafka.</div>';
}
async function loadLedger() {
  const r = await api('/api/v1/wallets/me/transactions');
  if (!r.ok) return;
  $('#ledger-list').innerHTML = r.data.length ? r.data.map(t => `
    <div class="item"><div><b>${esc(t.counterparty || '—')}</b><small>${t.direction === 'DEBIT' ? 'Debit' : 'Credit'} · balance ${fmt(t.balanceAfter)} · ${ago(t.createdAt)}</small></div>
    <span class="amt ${t.direction === 'DEBIT' ? 'minus' : 'plus'}">${t.direction === 'DEBIT' ? '−' : '+'}${fmt(t.amount)}</span></div>`).join('') : '<div class="muted">No entries.</div>';
}
function showResult(res, extra = '') {
  const el = $('#p-result'); el.classList.remove('hidden', 'ok', 'bad', 'warn');
  const d = res.data || {};
  let cls = 'bad', head;
  if (res.status === 201) { cls = 'ok'; head = `✅ Payment completed · ${fmt(d.amount)} to ${esc(d.receiverName || d.receiverId)}`; }
  else if (res.status === 200 && res.replayed === 'true') { cls = 'warn'; head = '♻️ Idempotent replay: same payment returned, no double charge'; }
  else if (res.status === 202) { cls = 'warn'; head = '⏳ Accepted: wallet unavailable, payment is PENDING and will be reconciled'; }
  else if (res.status === 422) { head = `❌ Payment failed: ${esc(d.failureReason)}`; }
  else if (res.status === 429) { head = '🚦 Rate limited by the gateway (429)'; }
  else { head = `❌ ${res.status}: ${esc(d.message || d.error || 'request failed')}`; }
  el.classList.add(cls);
  el.innerHTML = `${head}<small>correlation id: <code>${esc(res.cid)}</code>${extra}</small>`;
}
async function sendPayment() {
  const btn = $('#btn-send'); btn.disabled = true;
  try {
    const res = await api('/api/v1/payments', {
      method: 'POST', headers: { 'Idempotency-Key': uuid() },
      body: { toUserId: $('#p-to').value, amount: Number($('#p-amount').value), note: $('#p-note').value }
    });
    state.lastCid = res.cid; $('#trace-id').value = res.cid;
    showResult(res);
    $('#p-note').value = '';
    tick();
  } finally { btn.disabled = false; }
}

/* ------------------------------------------------------------------ payments / insights / admin */
async function loadPayments() {
  const r = await api('/api/v1/payments'); if (!r.ok) return;
  $('#pay-table tbody').innerHTML = r.data.map(p => {
    const sent = p.direction === 'SENT';
    return `<tr><td>${ago(p.createdAt)}</td><td>${sent ? '↗ Sent' : '↙ Received'}</td>
      <td>${esc(sent ? p.receiverName || short(p.receiverId) : p.senderName || short(p.senderId))}</td><td>${esc(p.note || '')}</td>
      <td class="r ${sent ? 'minus' : 'plus'}">${sent ? '−' : '+'}${fmt(p.amount)}</td>
      <td><span class="pill ${p.status}" title="${esc(p.failureReason || '')}">${p.status}${p.failureReason ? ' · ' + esc(p.failureReason) : ''}</span></td>
      <td class="mono">${short(p.id)}</td></tr>`;
  }).join('') || '<tr><td colspan="7" class="muted">No payments yet.</td></tr>';
}
async function loadInsights() {
  const r = await api('/api/v1/analytics/summary'); if (!r.ok) return;
  const s = r.data;
  let merchant = '';
  if (state.me.roles.includes('MERCHANT')) {
    const m = await api('/api/v1/payments/merchant/settlement');
    if (m.ok) merchant = `<div class="kpi"><span class="label">Your settlement</span><b>${fmt(m.data.totalReceived)}</b><small class="muted">${m.data.paymentsReceived} payments received</small></div>`;
  }
  $('#kpis').innerHTML = `
    <div class="kpi"><span class="label">Completed</span><b>${s.completed}</b></div>
    <div class="kpi"><span class="label">Volume</span><b>${fmt(s.volume)}</b></div>
    <div class="kpi"><span class="label">Success rate</span><b>${s.successRatePercent}%</b><small class="muted">${s.failed} failed</small></div>
    ${merchant || `<div class="kpi"><span class="label">Avg ticket</span><b>${fmt(s.averageTicket)}</b></div>`}`;
  const max = Math.max(1, ...s.last7Days.map(d => Number(d.volume)));
  $('#bars').innerHTML = s.last7Days.map(d => `<div class="bar" title="${fmt(d.volume)} · ${d.count} payments"><div style="height:${Math.max(2, Number(d.volume) / max * 100)}%"></div>${d.date.slice(5)}</div>`).join('');
  $('#top').innerHTML = s.topSenders.map(t => `<div class="item"><b>${esc(t.name)}</b><span class="amt">${fmt(t.volume)}</span></div>`).join('') || '<div class="muted">No data yet.</div>';
}
async function loadAdminWallets() {
  const r = await api('/api/v1/admin/wallets'); if (!r.ok) return;
  $('#adm-wallets tbody').innerHTML = r.data.map(w => `<tr><td><b>${esc(w.username)}</b> <span class="mono muted">${short(w.userId)}</span></td>
    <td class="r">${fmt(w.balance)}</td><td>${w.frozen ? '<span class="pill FAILED">FROZEN</span>' : '<span class="pill COMPLETED">ACTIVE</span>'}</td>
    <td><button class="btn small ${w.frozen ? 'ok' : 'danger'}" data-freeze="${esc(w.userId)}" data-state="${w.frozen ? 'unfreeze' : 'freeze'}">${w.frozen ? 'Unfreeze' : 'Freeze'}</button></td></tr>`).join('');
}
const auditRow = a => `<div class="item"><div><b>${esc(a.eventType)}</b><small>${esc(a.topic)} · ${esc(a.correlationId || '')}</small></div><small>${ago(a.occurredAt || a.recordedAt)}</small></div>`;
async function loadAudit() { const r = await api('/api/v1/audit?limit=25'); if (r.ok) $('#audit-list').innerHTML = r.data.map(auditRow).join('') || '<div class="muted">Empty</div>'; }
async function loadDlq() {
  const r = await api('/api/v1/audit/dead-letters'); if (!r.ok) return;
  $('#dlq-list').innerHTML = r.data.map(a => `<div class="item"><div><b>${esc(a.eventType)} <span class="pill FAILED">DLQ</span></b><small>${esc(a.errorMessage || '')}</small><small>${esc((a.payload || '').slice(0, 120))}…</small></div><small>${ago(a.recordedAt)}</small></div>`).join('') || '<div class="muted">No dead letters. Send a payment with #poison in the note.</div>';
}
async function trace() {
  const id = $('#trace-id').value.trim(); if (!id) return;
  const r = await api('/api/v1/audit/trace/' + encodeURIComponent(id));
  $('#trace-out').innerHTML = r.ok && r.data.length ? r.data.map(auditRow).join('') : '<div class="muted">No events found for that correlation id (events arrive within a second or two).</div>';
}

/* ------------------------------------------------------------------ demo lab */
const con = $('#console');
const log = (msg, cls = '') => { con.insertAdjacentHTML('beforeend', `<span class="${cls}">${esc(msg)}</span>\n`); con.scrollTop = con.scrollHeight; };
const balanceNow = async () => Number((await api('/api/v1/wallets/me')).data?.balance);
const otherUser = () => state.users.find(u => u.id !== state.me.id);
const pay = (amount, note, key) => api('/api/v1/payments', { method: 'POST', headers: { 'Idempotency-Key': key || uuid() }, body: { toUserId: otherUser().id, amount, note } });

const LAB = {
  async idem() {
    const key = uuid(); log(`\n▶ Idempotency demo · Idempotency-Key ${key}`, 'info');
    const before = await balanceNow(); log(`balance before: ${fmt(before)}`);
    const a = await pay(100, 'idempotency demo', key); log(`request #1 → HTTP ${a.status}  status=${a.data?.status}  replayed=${a.replayed}`, a.ok ? 'ok' : 'bad');
    const b = await pay(100, 'idempotency demo', key); log(`request #2 → HTTP ${b.status}  status=${b.data?.status}  replayed=${b.replayed}  same payment id: ${a.data?.id === b.data?.id}`, b.ok ? 'ok' : 'bad');
    await new Promise(r => setTimeout(r, 600));
    const after = await balanceNow(); log(`balance after: ${fmt(after)}  → charged ${fmt(before - after)} (expected ₹100.00)`, before - after === 100 ? 'ok' : 'bad');
  },
  async concurrent() {
    log('\n▶ Concurrency demo · 6 parallel payments of ₹10 from the same wallet', 'info');
    const before = await balanceNow();
    const rs = await Promise.all(Array.from({ length: 6 }, (_, i) => pay(10, 'parallel #' + (i + 1))));
    log('HTTP statuses: ' + rs.map(r => r.status).join(', '), rs.every(r => r.status === 201) ? 'ok' : 'bad');
    await new Promise(r => setTimeout(r, 800));
    const after = await balanceNow();
    log(`balance ${fmt(before)} → ${fmt(after)}  (charged ${fmt(before - after)}, expected ₹60.00)`, before - after === 60 ? 'ok' : 'bad');
    log('Both wallets are guarded by @Version; conflicting writers are retried (see wallet_optimistic_lock_retries_total in Grafana).');
  },
  async rate() {
    log('\n▶ Rate limit demo · 30 parallel GET /api/v1/payments (bucket: 5 req/s, burst 10)', 'info');
    const rs = await Promise.all(Array.from({ length: 30 }, () => api('/api/v1/payments')));
    const c = {}; rs.forEach(r => c[r.status] = (c[r.status] || 0) + 1);
    log('status counts: ' + JSON.stringify(c), c[429] ? 'ok' : 'bad');
    log('429 responses come from Spring Cloud Gateway RequestRateLimiter (Redis token bucket, keyed by user).');
  },
  async poison() {
    log('\n▶ Poison message demo', 'info');
    const r = await pay(1, '#poison lab message');
    log(`payment → HTTP ${r.status} ${r.data?.status} (payment itself succeeds; only the notification consumer fails)`, r.ok ? 'ok' : 'bad');
    log('notification-service: attempt 1, retry after 1s, retry, retry, then record goes to payments.DLT …');
    if (state.me.roles.includes('ADMIN')) {
      await new Promise(x => setTimeout(x, 6000));
      const d = await api('/api/v1/audit/dead-letters');
      log(`audit-service has archived ${d.data?.length ?? 0} dead-lettered message(s). Open Admin & Audit to inspect them.`, d.data?.length ? 'ok' : 'bad');
    } else log('Log in as admin to inspect the archived dead letter (Admin & Audit tab), or open Kafka UI → payments.DLT.');
  },
  async rbac() {
    log('\n▶ RBAC demo · GET /api/v1/admin/wallets with your token', 'info');
    const r = await api('/api/v1/admin/wallets');
    log(`HTTP ${r.status} for roles [${state.me.roles.join(', ')}]`, r.status === 403 || r.ok ? 'ok' : 'bad');
    log(r.status === 403 ? 'Rejected at the gateway (hasRole ADMIN). Services also check @PreAuthorize.' : 'Admin role accepted.');
  },
  async noauth() {
    log('\n▶ Authentication demo', 'info');
    const a = await api('/api/v1/wallets/me', { auth: false }); log(`no token → HTTP ${a.status}`, a.status === 401 ? 'ok' : 'bad');
    const t = state.token.slice(0, -6) + 'AAAAAA';
    const b = await api('/api/v1/wallets/me', { token: t }); log(`tampered token → HTTP ${b.status}`, b.status === 401 ? 'ok' : 'bad');
  }
};

/* ------------------------------------------------------------------ wiring */
document.addEventListener('click', async e => {
  const t = e.target.closest('[data-tab],[data-lab],[data-freeze],#btn-login,#btn-logout,#btn-send,#btn-topup,#btn-trace,#btn-clear');
  if (!t) return;
  if (t.id === 'btn-login') return login();
  if (t.id === 'btn-logout') return logout(false);
  if (t.id === 'btn-send') return sendPayment();
  if (t.id === 'btn-clear') { con.textContent = ''; return; }
  if (t.id === 'btn-trace') return trace();
  if (t.id === 'btn-topup') {
    const r = await api('/api/v1/wallets/me/topup', { method: 'POST', body: { amount: 5000 } });
    toast(r.ok ? 'Added ₹5,000 to your wallet' : 'Top-up failed: ' + (r.data?.message || r.status)); return tick();
  }
  if (t.dataset.tab) return switchTab(t.dataset.tab);
  if (t.dataset.freeze) {
    const r = await api(`/api/v1/admin/wallets/${t.dataset.freeze}/${t.dataset.state}`, { method: 'POST' });
    toast(r.ok ? `Wallet ${t.dataset.state}d. Notification event is on its way.` : 'Failed: ' + r.status); return loadAdminWallets();
  }
  if (t.dataset.lab) {
    if (!otherUser()) return toast('No other users loaded yet');
    t.disabled = true;
    try { await LAB[t.dataset.lab](); } catch (err) { log('error: ' + err.message, 'bad'); } finally { t.disabled = false; }
  }
});

(async function init() {
  if (location.search.includes('code=')) await handleCallback(); else loadStored();
  if (!state.token) { $('#login').classList.remove('hidden'); return; }
  try { await startApp(); } catch (e) { console.error(e); sessionStorage.removeItem('tok'); $('#app').classList.add('hidden'); $('#login').classList.remove('hidden'); }
})();
