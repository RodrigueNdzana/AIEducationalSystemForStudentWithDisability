'use strict';
/* AdaptIQ front end – plain JavaScript, no build step. All dynamic text is inserted with textContent
   (never innerHTML) so user-supplied content cannot inject markup. */

const S = { token: null, user: null, profile: null, view: null };
const $ = (sel) => document.querySelector(sel);

function h(tag, attrs, ...kids) {
  const e = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v == null || v === false) continue;
    if (k === 'class') e.className = v;
    else if (k === 'for') e.htmlFor = v;
    else if (k.startsWith('on')) e.addEventListener(k.slice(2), v);
    else if (k === 'value') e.value = v;
    else if (k === 'checked') e.checked = true;
    else e.setAttribute(k, v === true ? '' : v);
  }
  for (const kid of kids.flat()) {
    if (kid == null || kid === false) continue;
    e.append(kid.nodeType ? kid : document.createTextNode(String(kid)));
  }
  return e;
}

/* ---------------- API ---------------- */
async function api(path, opts = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (S.token) headers.Authorization = 'Bearer ' + S.token;
  const res = await fetch(path, { method: opts.method || 'GET', headers, body: opts.body ? JSON.stringify(opts.body) : undefined });
  if (opts.raw) {
    if (!res.ok) throw new Error('Download failed (' + res.status + ')');
    return res;
  }
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch (e) { /* non-JSON body */ }
  if (!res.ok) {
    const err = new Error((data && data.message) || 'Request failed (' + res.status + ')');
    err.status = res.status;
    err.details = data && data.details;
    if (res.status === 401 && S.token) logout(true);
    throw err;
  }
  return data;
}

function showError(e) {
  const box = $('#alert');
  const parts = [e.message || String(e)];
  if (e.details && Array.isArray(e.details.errors)) parts.push(...e.details.errors);
  box.replaceChildren(...parts.map((p, i) => h(i === 0 ? 'strong' : 'div', {}, p)));
  box.hidden = false;
}
function clearError() { const b = $('#alert'); b.hidden = true; b.replaceChildren(); }
function announce(msg) { const s = $('#status'); s.textContent = ''; setTimeout(() => { s.textContent = msg; }, 30); }
function notice(msg) { return h('div', { class: 'notice good', role: 'status' }, msg); }
async function guard(fn) { try { clearError(); return await fn(); } catch (e) { showError(e); } }

/* ---------------- accessibility settings (FR-09) ---------------- */
function applySettings(p) {
  if (!p) return;
  const root = document.documentElement;
  root.setAttribute('data-theme', p.theme || 'default');
  root.style.setProperty('--scale', p.fontScale || 1);
  root.classList.toggle('reduce-motion', !!p.reduceMotion);
  root.classList.toggle('dyslexia-font', (p.disabilityCategories || []).includes('COGNITIVE') && p.theme === 'sepia');
  ['protanopia', 'deuteranopia', 'tritanopia', 'greyscale'].forEach((c) => root.classList.remove('cf-' + c));
  if (p.colourFilter && p.colourFilter !== 'none') root.classList.add('cf-' + p.colourFilter);
  try { localStorage.setItem('adaptiq.prefs', JSON.stringify({ theme: p.theme, fontScale: p.fontScale })); } catch (e) { /* storage unavailable */ }
}
function restoreLocalPrefs() {
  try {
    const p = JSON.parse(localStorage.getItem('adaptiq.prefs') || 'null');
    if (p) applySettings({ theme: p.theme, fontScale: p.fontScale });
  } catch (e) { /* ignore */ }
}

/* ---------------- shell ---------------- */
const ROLE_LABEL = { STUDENT: 'Student', EDUCATOR: 'Educator', DISABILITY_SUPPORT_OFFICER: 'Disability support officer', ADMIN: 'Administrator', ML_ENGINEER: 'ML engineer' };

function tabsFor(role) {
  const common = [{ id: 'notifications', label: 'Notifications', render: viewNotifications }];
  if (role === 'STUDENT') {
    return [
      { id: 'dashboard', label: 'My progress', render: viewStudentDashboard },
      { id: 'recs', label: 'Recommended for me', render: viewRecommendations },
      { id: 'learn', label: 'Learn', render: viewLearn },
      { id: 'feedback', label: 'Feedback', render: viewFeedbackInbox },
      { id: 'settings', label: 'Accessibility settings', render: viewSettings },
      ...common];
  }
  const staff = [
    { id: 'class', label: 'Class dashboard', render: viewClassDashboard },
    { id: 'alerts', label: 'Alerts', render: viewAlerts }];
  if (role === 'EDUCATOR' || role === 'ADMIN') {
    staff.push({ id: 'give', label: 'Give feedback', render: viewGiveFeedback }, { id: 'upload', label: 'Upload content', render: viewUpload });
  }
  if (role === 'ADMIN' || role === 'DISABILITY_SUPPORT_OFFICER') staff.push({ id: 'reports', label: 'Reports', render: viewReports });
  if (role === 'ADMIN') staff.push({ id: 'users', label: 'Users', render: viewUsers });
  if (role === 'ADMIN' || role === 'ML_ENGINEER') staff.push({ id: 'models', label: 'AI model monitor', render: viewModels });
  if (role === 'ML_ENGINEER') return [{ id: 'models', label: 'AI model monitor', render: viewModels }, ...common];
  return [...staff, ...common];
}

function renderNav() {
  const nav = $('#nav');
  nav.replaceChildren(...S.tabs.map((t) => h('button', {
    type: 'button', 'data-id': t.id, 'aria-current': S.view === t.id ? 'page' : null, onclick: () => go(t.id)
  }, t.label)));
  nav.hidden = false;
}

async function go(id) {
  S.view = id;
  renderNav();
  const tab = S.tabs.find((t) => t.id === id);
  const main = $('#main');
  main.replaceChildren(h('p', {}, 'Loading…'));
  clearError();
  await guard(async () => { await tab.render(main); });
  main.focus();
  announce('Showing ' + tab.label);
}

function logout(expired) {
  if (S.token && !expired) api('/api/auth/logout', { method: 'POST' }).catch(() => {});
  S.token = null; S.user = null; S.profile = null; S.tabs = [];
  $('#nav').hidden = true; $('#user-box').hidden = true;
  viewLogin($('#main'));
  if (expired) showError(new Error('Your session expired. Please log in again.'));
}

/* ---------------- login / register (FR-01) ---------------- */
function viewLogin(main) {
  const email = h('input', { id: 'li-email', type: 'email', autocomplete: 'username', required: true });
  const pass = h('input', { id: 'li-pass', type: 'password', autocomplete: 'current-password', required: true });
  const form = h('form', { class: 'card stack', 'aria-labelledby': 'login-h' },
    h('h2', { id: 'login-h' }, 'Log in'),
    h('div', { class: 'field' }, h('label', { for: 'li-email' }, 'Email'), email),
    h('div', { class: 'field' }, h('label', { for: 'li-pass' }, 'Password'), pass),
    h('button', { class: 'btn', type: 'submit' }, 'Log in'));
  form.addEventListener('submit', (ev) => { ev.preventDefault(); guard(async () => {
    const r = await api('/api/auth/login', { method: 'POST', body: { email: email.value, password: pass.value } });
    await startSession(r);
  }); });

  const rn = h('input', { id: 'rg-name', type: 'text', autocomplete: 'name', required: true });
  const re = h('input', { id: 'rg-email', type: 'email', autocomplete: 'email', required: true });
  const rp = h('input', { id: 'rg-pass', type: 'password', autocomplete: 'new-password', required: true, 'aria-describedby': 'rg-hint' });
  const reg = h('form', { class: 'card stack', 'aria-labelledby': 'reg-h' },
    h('h2', { id: 'reg-h' }, 'New student? Register'),
    h('div', { class: 'field' }, h('label', { for: 'rg-name' }, 'Full name'), rn),
    h('div', { class: 'field' }, h('label', { for: 'rg-email' }, 'Email'), re),
    h('div', { class: 'field' }, h('label', { for: 'rg-pass' }, 'Password'), rp,
      h('div', { id: 'rg-hint', class: 'muted' }, 'At least 8 characters, with a letter and a digit.')),
    h('button', { class: 'btn', type: 'submit' }, 'Create account'));
  reg.addEventListener('submit', (ev) => { ev.preventDefault(); guard(async () => {
    await api('/api/auth/register', { method: 'POST', body: { name: rn.value, email: re.value, password: rp.value } });
    const r = await api('/api/auth/login', { method: 'POST', body: { email: re.value, password: rp.value } });
    await startSession(r);
  }); });

  main.replaceChildren(
    h('h2', {}, 'Welcome to AdaptIQ'),
    h('p', {}, 'Learning content that adapts to how you learn best.'),
    h('div', { class: 'grid' }, form, reg),
    h('details', { class: 'card' },
      h('summary', {}, 'Demo accounts (password for all: Passw0rd!)'),
      h('ul', {},
        h('li', {}, 'thandi@student.ac.za – blind/low-vision student'),
        h('li', {}, 'sipho@student.ac.za – Deaf/hard-of-hearing student'),
        h('li', {}, 'ayanda@student.ac.za – student with a learning disability (has an early-difficulty alert)'),
        h('li', {}, 'educator@adaptiq.ac.za, dso@adaptiq.ac.za, admin@adaptiq.ac.za, engineer@adaptiq.ac.za'))));
}

async function startSession(r) {
  S.token = r.token; S.user = r.user;
  $('#user-label').textContent = r.user.name + ' (' + (ROLE_LABEL[r.user.role] || r.user.role) + ')';
  $('#user-box').hidden = false;
  if (r.user.role === 'STUDENT') { S.profile = await api('/api/students/' + r.user.id + '/profile'); applySettings(S.profile); }
  S.tabs = tabsFor(r.user.role);
  await go(S.tabs[0].id);
}

/* ---------------- shared helpers ---------------- */
function table(caption, headers, rows) {
  return h('div', { class: 'table-wrap' }, h('table', {},
    h('caption', {}, caption),
    h('thead', {}, h('tr', {}, headers.map((x) => h('th', { scope: 'col' }, x)))),
    h('tbody', {}, rows.length ? rows.map((r) => h('tr', {}, r.map((c) => h('td', {}, c))))
      : h('tr', {}, h('td', { colspan: headers.length }, 'Nothing to show yet.')))));
}
const pct = (v) => (v == null ? 'n/a' : v + '%');
const when = (iso) => (iso ? new Date(iso).toLocaleDateString() : '–');
function statusPill(text, kind) { return h('span', { class: 'pill ' + kind }, text); }
function field(id, label, control, hint) {
  return h('div', { class: 'field' }, h('label', { for: id }, label), control, hint && h('div', { class: 'muted', id: id + '-hint' }, hint));
}
function check(id, label, checked) {
  return h('label', { class: 'check', for: id }, h('input', { id, type: 'checkbox', checked }), label);
}

/* ---------------- student views ---------------- */
async function viewStudentDashboard(main) {
  const from = h('input', { id: 'd-from', type: 'date' });
  const to = h('input', { id: 'd-to', type: 'date' });
  const body = h('div', { class: 'stack' });
  async function load() {
    const q = [from.value && 'from=' + from.value, to.value && 'to=' + to.value].filter(Boolean).join('&');
    const d = await api('/api/students/' + S.user.id + '/dashboard' + (q ? '?' + q : ''));
    const behind = d.completionPercent < d.expectedPercent;
    body.replaceChildren(
      h('div', { class: 'grid' },
        h('div', { class: 'card' }, h('h3', { id: 'p-h' }, 'Module completion'),
          h('progress', { max: 100, value: d.completionPercent, 'aria-labelledby': 'p-h' }),
          h('p', {}, h('span', { class: 'score' }, d.completionPercent + '%'), ' complete (expected ' + d.expectedPercent + '% by now) ',
            statusPill(behind ? 'Behind expected pace' : 'On track', behind ? 'warn' : 'ok'))),
        h('div', { class: 'card' }, h('h3', {}, 'Scores and time'),
          h('p', {}, 'Average score: ', h('strong', {}, pct(d.averageScore))),
          h('p', {}, 'Time spent: ', h('strong', {}, d.totalMinutes + ' minutes')))),
      h('div', { class: 'card' }, h('h3', {}, 'AI insights'), h('ul', {}, d.insights.map((i) => h('li', {}, i)))),
      table('Modules', ['Module', 'Activities', 'Status', 'Best score'],
        d.modules.map((m) => [m.moduleId, m.contentCount, m.completed ? 'Completed' : 'Not completed', pct(m.bestScore)])),
      table('Score history', ['Date', 'Activity', 'Score'], d.timeline.map((t) => [when(t.date), t.contentTitle, t.score + '%'])));
  }
  const form = h('form', { class: 'row', 'aria-label': 'Filter progress by date' },
    h('div', {}, h('label', { for: 'd-from' }, 'From'), from), h('div', {}, h('label', { for: 'd-to' }, 'To'), to),
    h('button', { class: 'btn secondary', type: 'submit' }, 'Apply dates'));
  form.addEventListener('submit', (e) => { e.preventDefault(); guard(load); });
  main.replaceChildren(h('h2', {}, 'My progress'), form, body);
  await load();
}

async function viewRecommendations(main) {
  const list = h('div', { class: 'grid' });
  async function load(refresh) {
    const recs = await api('/api/students/' + S.user.id + '/recommendations' + (refresh ? '/refresh' : ''), refresh ? { method: 'POST' } : {});
    list.replaceChildren(...recs.map((v) => {
      const r = v.recommendation;
      return h('article', { class: 'card stack', 'aria-labelledby': 'rec-' + r.id },
        h('h3', { id: 'rec-' + r.id }, v.title),
        h('p', {}, v.format + ' · ' + v.difficulty + ' · module ' + v.moduleId),
        h('p', {}, 'Match: ', h('span', { class: 'score' }, r.matchScore + '%')),
        h('meter', { min: 0, max: 100, value: r.matchScore, 'aria-label': 'Match score ' + r.matchScore + ' percent' }),
        h('p', { class: 'muted' }, 'Why: ' + r.reason),
        h('div', { class: 'row' },
          h('button', { class: 'btn', type: 'button', onclick: () => openContent(v.recommendation.contentId) }, 'Open'),
          rateBtn(r, 'UP', 'Helpful', v.title), rateBtn(r, 'DOWN', 'Not helpful', v.title)),
        r.rating && h('p', {}, statusPill('You rated this: ' + (r.rating === 'UP' ? 'helpful' : 'not helpful'), 'ok')));
    }));
    if (!recs.length) list.replaceChildren(h('p', {}, 'No recommendations yet. Complete an activity to get started.'));
  }
  function rateBtn(r, rating, label, title) {
    return h('button', { class: 'btn secondary', type: 'button', 'aria-label': label + ': ' + title, onclick: () => guard(async () => {
      await api('/api/recommendations/' + r.id + '/rating', { method: 'POST', body: { rating } });
      announce('Thanks – rating saved'); await load(false);
    }) }, rating === 'UP' ? '👍 ' + label : '👎 ' + label);
  }
  main.replaceChildren(h('h2', {}, 'Recommended for me'),
    h('p', { class: 'muted' }, 'Chosen from content that is already accessible for your needs. Rate them to improve future suggestions.'),
    h('button', { class: 'btn secondary', type: 'button', onclick: () => guard(() => load(true)) }, 'Refresh recommendations'), list);
  await load(false);
}

async function viewLearn(main) {
  const items = await api('/api/content');
  main.replaceChildren(h('h2', {}, 'Learn'),
    table('Available content', ['Title', 'Format', 'Level', 'Module', ''],
      items.map((c) => [c.title, c.format, c.difficulty, c.moduleId,
        h('button', { class: 'btn secondary', type: 'button', 'aria-label': 'Open ' + c.title, onclick: () => openContent(c.id) }, 'Open')])));
}

function defaultMode(c) {
  const p = S.profile || {};
  const cats = p.disabilityCategories || [], prefs = p.preferredFormats || [];
  if (cats.includes('HEARING') && (c.format === 'VIDEO' || c.format === 'AUDIO')) return 'CAPTIONS';
  if (cats.includes('VISUAL') || prefs[0] === 'AUDIO') return 'AUDIO';
  if (cats.includes('COGNITIVE')) return 'SIMPLIFIED_TEXT';
  return 'TEXT';
}

async function openContent(id) {
  S.view = 'learn'; renderNav();
  const main = $('#main'); main.replaceChildren(h('p', {}, 'Loading…')); clearError();
  await guard(async () => {
    const c = await api('/api/content/' + id);
    const modeSel = h('select', { id: 'mode' },
      [['TEXT', 'Text'], ['SIMPLIFIED_TEXT', 'Simplified text'], ['AUDIO', 'Audio (read aloud)'], ['CAPTIONS', 'Captions / transcript']]
        .map(([v, l]) => h('option', { value: v, selected: v === defaultMode(c) }, l)));
    const out = h('div', { class: 'stack', 'aria-live': 'polite' });
    async function render() {
      const a = await api('/api/content/' + id + '/adapt?mode=' + modeSel.value);
      const parts = [];
      if (a.gradeBefore != null && a.gradeAfter != null) parts.push(h('p', { class: 'muted' }, 'Reading level: grade ' + a.gradeBefore + ' → grade ' + a.gradeAfter));
      else if (a.gradeBefore != null) parts.push(h('p', { class: 'muted' }, 'Reading level: grade ' + a.gradeBefore));
      if (a.note) parts.push(h('p', { class: 'muted' }, a.note));
      if (a.audioScript) {
        const rate = h('input', { id: 'rate', type: 'range', min: 0.6, max: 1.6, step: 0.1, value: 1 });
        parts.push(h('div', { class: 'card' },
          h('p', {}, 'About ' + a.estimatedAudioSeconds + ' seconds of audio.'),
          h('div', { class: 'row' },
            h('button', { class: 'btn', type: 'button', onclick: () => speak(a.audioScript, parseFloat(rate.value)) }, '▶ Play'),
            h('button', { class: 'btn secondary', type: 'button', onclick: () => window.speechSynthesis && speechSynthesis.cancel() }, '■ Stop')),
          field('rate', 'Speaking speed', rate)));
      }
      if (a.captions) parts.push(h('ol', { class: 'captions', 'aria-label': 'Captions' },
        a.captions.map((s) => h('li', {}, h('span', { class: 'muted' }, s.startSeconds + 's – '), s.text))));
      else parts.push(h('div', { class: 'reading', tabindex: 0, 'aria-label': 'Lesson text' }, a.text));
      out.replaceChildren(...parts);
    }
    modeSel.addEventListener('change', () => guard(render));

    const score = h('input', { id: 'sc', type: 'number', min: 0, max: 100 });
    const mins = h('input', { id: 'mi', type: 'number', min: 0, value: 10 });
    const done = h('input', { id: 'dn', type: 'checkbox' });
    const log = h('form', { class: 'card stack', 'aria-labelledby': 'log-h' },
      h('h3', { id: 'log-h' }, 'Record my progress'),
      field('sc', 'Quiz score (0–100, optional)', score), field('mi', 'Minutes spent', mins),
      h('label', { class: 'check', for: 'dn' }, done, 'I completed this activity'),
      h('button', { class: 'btn', type: 'submit' }, 'Save progress'));
    log.addEventListener('submit', (e) => { e.preventDefault(); guard(async () => {
      await api('/api/students/' + S.user.id + '/interactions', { method: 'POST', body: {
        contentId: id, score: score.value === '' ? null : Number(score.value), completed: done.checked,
        minutes: Number(mins.value || 0), formatUsed: c.format } });
      announce('Progress saved. Your recommendations were refreshed.');
      log.append(notice('Progress saved. Your recommendations were refreshed.'));
    }); });

    main.replaceChildren(h('h2', {}, c.title), h('p', { class: 'muted' }, c.format + ' · ' + c.difficulty + ' · module ' + c.moduleId),
      field('mode', 'How would you like to read this?', modeSel), out, log,
      h('button', { class: 'btn secondary', type: 'button', onclick: () => go('learn') }, '← Back to content'));
    await render();
  });
  main.focus();
}

function speak(text, rate) {
  if (!('speechSynthesis' in window)) { showError(new Error('Text-to-speech is not supported by this browser.')); return; }
  speechSynthesis.cancel();
  const u = new SpeechSynthesisUtterance(text); u.rate = rate || 1; speechSynthesis.speak(u);
}

async function viewFeedbackInbox(main) {
  const items = await api('/api/students/' + S.user.id + '/feedback');
  main.replaceChildren(h('h2', {}, 'Feedback from my educators'),
    ...(items.length ? items.map((v) => {
      const f = v.feedback;
      const resp = h('textarea', { id: 'resp-' + f.id, 'aria-label': 'Your reply' });
      return h('article', { class: 'card stack', 'aria-label': 'Feedback from ' + f.educatorName },
        h('h3', {}, f.educatorName + ' · ' + when(f.createdAt)),
        h('p', { class: 'muted' }, 'Shown as: ' + v.renderedFormat.replace('_', ' ').toLowerCase()),
        f.attachmentName && h('p', {}, 'Attachment: ' + f.attachmentName),
        h('div', { class: 'reading' }, v.renderedText),
        v.audioScript && h('button', { class: 'btn secondary', type: 'button', onclick: () => speak(v.audioScript, 1) }, '▶ Listen'),
        f.acknowledged ? statusPill('Acknowledged', 'ok')
          : h('div', { class: 'stack' }, field('resp-' + f.id, 'Reply (optional)', resp),
            h('button', { class: 'btn', type: 'button', onclick: () => guard(async () => {
              await api('/api/students/' + S.user.id + '/feedback/' + f.id + '/acknowledge', { method: 'POST', body: { response: resp.value } });
              await go('feedback');
            }) }, 'Acknowledge')));
    }) : [h('p', {}, 'No feedback yet.')]));
}

async function viewSettings(main) {
  const p = await api('/api/students/' + S.user.id + '/profile');
  const cats = ['VISUAL', 'HEARING', 'MOTOR', 'COGNITIVE', 'NONE'];
  const fmts = ['TEXT', 'AUDIO', 'VIDEO', 'INTERACTIVE'];
  const themes = ['default', 'high-contrast', 'high-contrast-yellow', 'dark', 'sepia', 'colour-blind-safe'];
  const filters = ['none', 'protanopia', 'deuteranopia', 'tritanopia', 'greyscale'];
  const catBoxes = cats.map((c) => check('cat-' + c, c.charAt(0) + c.slice(1).toLowerCase(), (p.disabilityCategories || []).includes(c)));
  const fmtBoxes = fmts.map((f) => check('fmt-' + f, f.charAt(0) + f.slice(1).toLowerCase(), (p.preferredFormats || []).includes(f)));
  const tech = h('input', { id: 'tech', type: 'text', value: (p.assistiveTech || []).join(', ') });
  const goals = h('input', { id: 'goals', type: 'text', value: p.goals || '' });
  const font = h('input', { id: 'font', type: 'range', min: 0.8, max: 2.5, step: 0.1, value: p.fontScale || 1 });
  const theme = h('select', { id: 'theme' }, themes.map((t) => h('option', { value: t, selected: t === p.theme }, t.replace(/-/g, ' '))));
  const cf = h('select', { id: 'cf' }, filters.map((t) => h('option', { value: t, selected: t === p.colourFilter }, t)));
  const motion = check('motion', 'Reduce motion and animation', p.reduceMotion);
  const extra = check('extra', 'I need extra time on timed activities', p.extraTime);

  const current = () => ({
    disabilityCategories: cats.filter((c) => $('#cat-' + c).checked), preferredFormats: fmts.filter((f) => $('#fmt-' + f).checked),
    assistiveTech: tech.value.split(',').map((s) => s.trim()).filter(Boolean), goals: goals.value,
    extraTime: $('#extra').checked, fontScale: Number(font.value), theme: theme.value, colourFilter: cf.value, reduceMotion: $('#motion').checked });
  // preview display settings immediately (FR-09: applied within 1 second)
  [font, theme, cf].forEach((el) => el.addEventListener('input', () => applySettings({ ...S.profile, ...current() })));
  motion.querySelector('input').addEventListener('change', () => applySettings({ ...S.profile, ...current() }));

  const form = h('form', { class: 'stack', 'aria-labelledby': 'set-h' },
    h('fieldset', {}, h('legend', {}, 'Display'),
      field('font', 'Text size', font, 'Drag to make text larger or smaller.'), field('theme', 'Colour theme', theme), field('cf', 'Colour filter', cf), motion),
    h('fieldset', {}, h('legend', {}, 'My accessibility needs'), ...catBoxes,
      field('tech', 'Assistive technology I use (comma separated)', tech), extra),
    h('fieldset', {}, h('legend', {}, 'How I like to learn'), ...fmtBoxes, field('goals', 'My learning goals / topics', goals)),
    h('div', { class: 'row' },
      h('button', { class: 'btn', type: 'submit' }, 'Save settings'),
      h('button', { class: 'btn secondary', type: 'button', onclick: () => guard(async () => {
        S.profile = await api('/api/students/' + S.user.id + '/profile', { method: 'PUT', body: { theme: 'default', fontScale: 1, colourFilter: 'none', reduceMotion: false } });
        applySettings(S.profile); await go('settings');
      }) }, 'Reset display to default')));
  form.addEventListener('submit', (e) => { e.preventDefault(); guard(async () => {
    S.profile = await api('/api/students/' + S.user.id + '/profile', { method: 'PUT', body: current() });
    applySettings(S.profile); announce('Settings saved'); form.append(notice('Settings saved. They apply on every page and device.'));
  }); });
  main.replaceChildren(h('h2', { id: 'set-h' }, 'Accessibility settings'), form);
}

async function viewNotifications(main) {
  const items = await api('/api/notifications');
  main.replaceChildren(h('h2', {}, 'Notifications'),
    items.length ? h('ul', { class: 'stack', style: 'padding:0;list-style:none' }, items.map((n) => h('li', { class: 'card' },
      h('div', {}, h('strong', {}, n.channel === 'EMAIL' ? 'Email · ' : ''), n.message),
      h('div', { class: 'muted' }, new Date(n.createdAt).toLocaleString()),
      n.read ? statusPill('Read', 'ok') : h('button', { class: 'btn secondary', type: 'button', onclick: () => guard(async () => {
        await api('/api/notifications/' + n.id + '/read', { method: 'POST' }); await go('notifications'); }) }, 'Mark as read'))))
      : h('p', {}, 'You have no notifications.'));
}

/* ---------------- educator / staff views ---------------- */
async function viewClassDashboard(main) {
  const dis = h('select', { id: 'f-dis' }, ['ANY', 'VISUAL', 'HEARING', 'MOTOR', 'COGNITIVE', 'NONE'].map((x) => h('option', { value: x }, x)));
  const lvl = h('select', { id: 'f-lvl' }, ['', 'LOW', 'MEDIUM', 'HIGH'].map((x) => h('option', { value: x }, x || 'Any')));
  const course = h('input', { id: 'f-course', type: 'text', placeholder: 'e.g. CS101' });
  const risk = check('f-risk', 'At-risk only', false);
  const out = h('div', { class: 'stack' });
  async function load() {
    const q = new URLSearchParams();
    if (dis.value !== 'ANY') q.set('disability', dis.value);
    if (lvl.value) q.set('progressLevel', lvl.value);
    if (course.value.trim()) q.set('course', course.value.trim());
    if ($('#f-risk').checked) q.set('riskOnly', 'true');
    const d = await api('/api/educator/dashboard' + (q.toString() ? '?' + q : ''));
    out.replaceChildren(
      h('p', {}, d.totalStudents + ' students · ', h('strong', {}, d.atRiskCount + ' at risk'), ' · expected progress ' + d.expectedPercent + '%'),
      table('Students (at-risk first)', ['Student', 'Needs', 'Completion', 'Avg score', 'Status', 'Open alerts', 'Last active', ''],
        d.students.map((s) => [s.name, s.disabilityCategories.join(', ').toLowerCase(), s.completionPercent + '%', pct(s.averageScore),
          statusPill(s.atRisk ? 'At risk' : 'On track', s.atRisk ? 'bad' : 'ok'), s.openAlerts, when(s.lastActivity),
          h('button', { class: 'btn secondary', type: 'button', 'aria-label': 'View activity log for ' + s.name, onclick: () => guard(() => showActivity(s, out)) }, 'Activity')])));
  }
  async function showActivity(s, target) {
    const acts = await api('/api/educator/students/' + s.studentId + '/activity');
    target.append(table('Activity log – ' + s.name, ['Date', 'Content', 'Module', 'Score', 'Completed', 'Minutes'],
      acts.map((a) => [when(a.timestamp), a.contentId, a.moduleId, a.score == null ? '–' : a.score + '%', a.completed ? 'Yes' : 'No', a.minutes])));
  }
  const form = h('form', { class: 'row', 'aria-label': 'Filter students' },
    field('f-dis', 'Disability', dis), field('f-lvl', 'Progress level', lvl), field('f-course', 'Course', course), risk,
    h('button', { class: 'btn', type: 'submit' }, 'Apply filters'));
  form.addEventListener('submit', (e) => { e.preventDefault(); guard(load); });
  main.replaceChildren(h('h2', {}, 'Class dashboard'), form, out);
  await load();
}

async function viewAlerts(main) {
  const status = h('select', { id: 'al-st' }, ['OPEN', 'ACKNOWLEDGED', 'ESCALATED', 'DISMISSED', ''].map((x) => h('option', { value: x }, x || 'All')));
  const out = h('div', { class: 'stack' });
  async function load() {
    const list = await api('/api/educator/alerts' + (status.value ? '?status=' + status.value : ''));
    out.replaceChildren(...(list.length ? list.map((a) => h('article', { class: 'card stack', 'aria-label': 'Alert for ' + a.studentName },
      h('h3', {}, a.studentName + ' – ' + a.type.replace('_', ' ').toLowerCase()),
      h('p', {}, a.message), h('p', { class: 'muted' }, new Date(a.createdAt).toLocaleString() + ' · ' + a.status),
      a.status === 'OPEN' ? h('div', { class: 'row' }, ['ACKNOWLEDGE', 'DISMISS', 'ESCALATE'].map((act) =>
        h('button', { class: act === 'ESCALATE' ? 'btn danger' : 'btn secondary', type: 'button', onclick: () => guard(async () => {
          await api('/api/educator/alerts/' + a.id, { method: 'PATCH', body: { action: act } }); announce('Alert updated'); await load(); }) },
          act.charAt(0) + act.slice(1).toLowerCase()))) : statusPill(a.status, 'ok'))) : [h('p', {}, 'No alerts.')]));
  }
  status.addEventListener('change', () => guard(load));
  main.replaceChildren(h('h2', {}, 'Early-difficulty alerts'), field('al-st', 'Show', status), out);
  await load();
}

async function viewGiveFeedback(main) {
  const students = await api('/api/students');
  const stu = h('select', { id: 'fb-stu' }, students.map((s) => h('option', { value: s.id }, s.name)));
  const type = h('select', { id: 'fb-type' }, ['TEXT', 'AUDIO', 'FILE'].map((x) => h('option', { value: x }, x)));
  const text = h('textarea', { id: 'fb-text' });
  const secs = h('input', { id: 'fb-secs', type: 'number', min: 1, max: 300 });
  const file = h('input', { id: 'fb-file', type: 'text' });
  const form = h('form', { class: 'card stack', 'aria-labelledby': 'fb-h' }, h('h3', { id: 'fb-h' }, 'New feedback'),
    field('fb-stu', 'Student', stu), field('fb-type', 'Type', type),
    field('fb-text', 'Feedback text (for audio: the transcript)', text),
    field('fb-secs', 'Audio length in seconds (audio only, maximum 300)', secs), field('fb-file', 'Attachment file name (file only)', file),
    h('button', { class: 'btn', type: 'submit' }, 'Send feedback'));
  form.addEventListener('submit', (e) => { e.preventDefault(); guard(async () => {
    await api('/api/educator/feedback', { method: 'POST', body: { studentId: stu.value, type: type.value, text: text.value,
      audioSeconds: secs.value ? Number(secs.value) : null, attachmentName: file.value || null } });
    announce('Feedback sent'); text.value = ''; form.append(notice('Feedback sent. The student was notified in the platform and by email.'));
  }); });
  main.replaceChildren(h('h2', {}, 'Give feedback'), form);
}

async function viewUpload(main) {
  const f = {};
  const mk = (id, label, el, hint) => { f[id] = el; return field(id, label, el, hint); };
  const sel = (id, opts) => h('select', { id }, opts.map((o) => h('option', { value: o }, o)));
  const report = h('div', { class: 'stack', 'aria-live': 'polite' });
  const form = h('form', { class: 'stack', 'aria-labelledby': 'up-h' },
    mk('up-title', 'Title', h('input', { id: 'up-title', type: 'text', required: true })),
    mk('up-format', 'Format', sel('up-format', ['TEXT', 'AUDIO', 'VIDEO', 'INTERACTIVE'])),
    mk('up-diff', 'Difficulty', sel('up-diff', ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'])),
    mk('up-mod', 'Module id', h('input', { id: 'up-mod', type: 'text', placeholder: 'M1' })),
    mk('up-course', 'Course', h('input', { id: 'up-course', type: 'text', value: 'CS101' })),
    mk('up-topic', 'Topic tag', h('input', { id: 'up-topic', type: 'text' })),
    mk('up-file', 'File name', h('input', { id: 'up-file', type: 'text', placeholder: 'lecture.mp4' }), 'Allowed types: PDF, DOCX, MP4, MP3, HTML.'),
    mk('up-body', 'Lesson text', h('textarea', { id: 'up-body' })),
    mk('up-tr', 'Transcript', h('textarea', { id: 'up-tr' })),
    h('fieldset', {}, h('legend', {}, 'Accessibility features'),
      check('up-cc', 'Has captions', false), check('up-img', 'Contains images', false), check('up-alt', 'Images have alt text', false),
      check('up-narr', 'Has audio description / narration', false), check('up-motor', 'Needs fine motor control', false)),
    h('div', { class: 'row' },
      h('button', { class: 'btn secondary', type: 'button', id: 'chk' }, 'Check accessibility'),
      h('button', { class: 'btn', type: 'submit' }, 'Publish')),
    S.user.role === 'ADMIN' && check('up-ovr', 'Administrator override: publish even if checks fail', false));
  const payload = () => ({
    title: f['up-title'].value, format: f['up-format'].value, difficulty: f['up-diff'].value, moduleId: f['up-mod'].value || null,
    course: f['up-course'].value, topic: f['up-topic'].value, fileName: f['up-file'].value || null, body: f['up-body'].value, transcript: f['up-tr'].value,
    hasCaptions: $('#up-cc').checked, hasImages: $('#up-img').checked, hasAltText: $('#up-alt').checked,
    hasAudioNarration: $('#up-narr').checked, requiresFineMotor: $('#up-motor').checked });
  function show(r) {
    report.replaceChildren(h('div', { class: 'card' }, h('h3', {}, 'Accessibility check ', statusPill(r.passed ? 'Passed' : 'Failed', r.passed ? 'ok' : 'bad')),
      r.errors.length ? h('ul', {}, r.errors.map((x) => h('li', {}, 'Error: ' + x))) : null,
      r.warnings.length ? h('ul', {}, r.warnings.map((x) => h('li', {}, 'Warning: ' + x))) : null,
      !r.errors.length && !r.warnings.length ? h('p', {}, 'No issues found.') : null));
  }
  form.querySelector('#chk').addEventListener('click', () => guard(async () => { show(await api('/api/content/validate', { method: 'POST', body: payload() })); }));
  form.addEventListener('submit', (e) => { e.preventDefault(); guard(async () => {
    const ovr = $('#up-ovr') && $('#up-ovr').checked;
    try {
      const r = await api('/api/content' + (ovr ? '?override=true' : ''), { method: 'POST', body: payload() });
      show(r.report); report.append(notice('Published: ' + r.content.title)); announce('Content published');
    } catch (err) { if (err.details && err.details.errors) show(err.details); throw err; }
  }); });
  main.replaceChildren(h('h2', { id: 'up-h' }, 'Upload content'),
    h('p', { class: 'muted' }, 'Content is checked against WCAG 2.1 AA rules before it can be published.'), form, report);
}

async function viewReports(main) {
  const from = h('input', { id: 'rp-from', type: 'date' }), to = h('input', { id: 'rp-to', type: 'date' });
  const out = h('div', { class: 'stack' });
  const q = () => [from.value && 'from=' + from.value, to.value && 'to=' + to.value].filter(Boolean).join('&');
  async function load() {
    const r = await api('/api/admin/reports' + (q() ? '?' + q() : ''));
    out.replaceChildren(
      h('div', { class: 'grid' },
        h('div', { class: 'card' }, h('h3', {}, 'Engagement'), h('p', {}, r.totalStudents + ' students, ' + r.activeStudents + ' active'),
          h('p', {}, r.totalInteractions + ' interactions · average score ' + pct(r.averageScore)), h('p', {}, 'Average completion ' + r.averageCompletionPercent + '%')),
        h('div', { class: 'card' }, h('h3', {}, 'Accessibility compliance'),
          h('p', {}, r.compliantContent + ' of ' + r.publishedContent + ' published items pass WCAG checks'),
          h('progress', { max: 100, value: r.compliancePercent, 'aria-label': 'Compliance percent' })),
        h('div', { class: 'card' }, h('h3', {}, 'At-risk students (' + r.atRiskCount + ')'),
          r.atRiskStudents.length ? h('ul', {}, r.atRiskStudents.map((n) => h('li', {}, n))) : h('p', {}, 'None'))),
      table('By disability category', ['Category', 'Students', 'Average score', 'Average completion'],
        Object.entries(r.byDisability).map(([k, v]) => [k, v.students, pct(v.averageScore), v.averageCompletion + '%'])),
      table('Accommodation and assistive-technology usage', ['Item', 'Students'], Object.entries(r.accommodationUsage).map(([k, v]) => [k, v])));
  }
  async function download(fmt) {
    const res = await api('/api/admin/reports/export?format=' + fmt + (q() ? '&' + q() : ''), { raw: true });
    const url = URL.createObjectURL(await res.blob());
    const a = h('a', { href: url, download: 'adaptiq-report.' + fmt }); document.body.append(a); a.click(); a.remove(); URL.revokeObjectURL(url);
    announce('Report downloaded');
  }
  const form = h('form', { class: 'row', 'aria-label': 'Report period' }, field('rp-from', 'From', from), field('rp-to', 'To', to),
    h('button', { class: 'btn', type: 'submit' }, 'Update report'),
    h('button', { class: 'btn secondary', type: 'button', onclick: () => guard(() => download('csv')) }, 'Export CSV'),
    h('button', { class: 'btn secondary', type: 'button', onclick: () => guard(() => download('pdf')) }, 'Export PDF'));
  form.addEventListener('submit', (e) => { e.preventDefault(); guard(load); });
  main.replaceChildren(h('h2', {}, 'Reports'), form, out);
  await load();
}

async function viewUsers(main) {
  const out = h('div');
  const name = h('input', { id: 'nu-name', type: 'text', required: true }), email = h('input', { id: 'nu-email', type: 'email', required: true });
  const pw = h('input', { id: 'nu-pw', type: 'password', required: true });
  const role = h('select', { id: 'nu-role' }, ['STUDENT', 'EDUCATOR', 'DISABILITY_SUPPORT_OFFICER', 'ADMIN', 'ML_ENGINEER'].map((r) => h('option', { value: r }, ROLE_LABEL[r])));
  async function load() {
    const users = await api('/api/admin/users');
    out.replaceChildren(table('Accounts', ['Name', 'Email', 'Role', 'Status', ''], users.map((u) => [u.name, u.email, ROLE_LABEL[u.role], u.locked ? statusPill('Locked', 'bad') : 'Active',
      u.locked ? h('button', { class: 'btn secondary', type: 'button', onclick: () => guard(async () => { await api('/api/admin/users/' + u.id + '/unlock', { method: 'POST' }); await load(); }) }, 'Unlock ' + u.name) : ''])));
  }
  const form = h('form', { class: 'card stack', 'aria-labelledby': 'nu-h' }, h('h3', { id: 'nu-h' }, 'Create account'),
    field('nu-name', 'Name', name), field('nu-email', 'Email', email), field('nu-pw', 'Temporary password', pw), field('nu-role', 'Role', role),
    h('button', { class: 'btn', type: 'submit' }, 'Create'));
  form.addEventListener('submit', (e) => { e.preventDefault(); guard(async () => {
    await api('/api/admin/users', { method: 'POST', body: { name: name.value, email: email.value, password: pw.value, role: role.value } });
    form.reset(); announce('Account created'); await load(); }); });
  main.replaceChildren(h('h2', {}, 'Users'), form, out);
  await load();
}

async function viewModels(main) {
  const out = h('div', { class: 'stack' });
  async function load() {
    const [m, v] = await Promise.all([api('/api/models/metrics'), api('/api/models/versions')]);
    out.replaceChildren(
      h('div', { class: 'grid' },
        h('div', { class: 'card' }, h('h3', {}, 'Recommendation relevance'), h('p', {}, h('span', { class: 'score' }, m.overallRelevance == null ? 'n/a' : Math.round(m.overallRelevance * 100) + '%'),
          ' of ' + m.ratedRecommendations + ' rated recommendations were helpful'), h('p', {}, 'Design target: 85%')),
        h('div', { class: 'card' }, h('h3', {}, 'Bias audit'), h('p', {}, 'Fairness gap: ' + (m.fairnessGapPoints == null ? 'n/a' : m.fairnessGapPoints + ' percentage points')),
          statusPill(m.biasAlert ? 'Bias alert (more than 5 points)' : 'Within threshold', m.biasAlert ? 'bad' : 'ok')),
        h('div', { class: 'card' }, h('h3', {}, 'Current model'), h('p', {}, 'Version ' + m.currentVersion), h('p', {}, 'Similarity weight ' + m.similarityWeight + ' · preference weight ' + m.preferenceWeight))),
      table('Relevance by disability category', ['Category', 'Ratings', 'Relevance', 'Audit'],
        m.byCategory.map((c) => [c.category, c.ratedCount, c.relevance == null ? 'n/a' : Math.round(c.relevance * 100) + '%', statusPill(c.flagged ? 'Flagged' : 'OK', c.flagged ? 'bad' : 'ok')])),
      m.notes.length ? h('ul', {}, m.notes.map((n) => h('li', {}, n))) : null,
      table('Model versions', ['Version', 'Weights (sim / pref)', 'Trained', 'By', 'Changelog'],
        v.map((x) => [x.version, x.similarityWeight + ' / ' + x.preferenceWeight, new Date(x.trainedAt).toLocaleString(), x.trainedBy, x.changelog])));
  }
  main.replaceChildren(h('h2', {}, 'AI model monitor'),
    h('button', { class: 'btn', type: 'button', onclick: () => guard(async () => { await api('/api/models/retrain', { method: 'POST' }); announce('Model retrained'); await load(); }) }, 'Retrain model'), out);
  await load();
}

/* ---------------- boot ---------------- */
document.addEventListener('DOMContentLoaded', () => {
  restoreLocalPrefs();
  $('#logout-btn').addEventListener('click', () => logout(false));
  viewLogin($('#main'));
});
