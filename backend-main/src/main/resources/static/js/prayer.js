(() => {
  'use strict';
  const $ = (id) => document.getElementById(id);
  const csrfToken = document.querySelector('meta[name="csrf-token"]')?.content;
  const csrfHeader = document.querySelector('meta[name="csrf-header"]')?.content;
  const tz = (() => { try { return Intl.DateTimeFormat().resolvedOptions().timeZone || ''; } catch (e) { return ''; } })();
  const PREFS_KEY = 'betterme.prayer.prefs';
  let current = null;   // the Day currently shown
  let busy = false;

  // ---- per-browser convenience: remember location/method (never trusted by the server) ----
  function loadPrefs() {
    try {
      const p = JSON.parse(localStorage.getItem(PREFS_KEY) || 'null');
      if (!p) return;
      if (p.city) $('prCity').value = p.city;
      if (p.country) $('prCountry').value = p.country;
      if (p.method) $('prMethod').value = p.method;
      if (p.school !== undefined) $('prSchool').value = p.school;
    } catch (e) { /* storage unavailable: ignore */ }
  }
  function savePrefs() {
    try {
      localStorage.setItem(PREFS_KEY, JSON.stringify({
        city: $('prCity').value, country: $('prCountry').value,
        method: $('prMethod').value, school: $('prSchool').value }));
    } catch (e) { /* ignore */ }
  }

  // ---- fetch helper ----
  async function api(path, options = {}) {
    const headers = { 'Accept': 'application/json', ...(options.headers || {}) };
    if (options.body) headers['Content-Type'] = 'application/json';
    if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;
    const res = await fetch(path, { credentials: 'same-origin', ...options, headers });
    if (res.redirected || !(res.headers.get('content-type') || '').includes('json')) {
      window.location.reload();           // session expired -> login page
      throw new Error('Please sign in again.');
    }
    const body = await res.json();
    if (!res.ok) throw new Error(body.message || 'Something went wrong. Please try again.');
    return body;
  }

  function query(date) {
    const p = new URLSearchParams();
    if (date) p.set('date', date);
    p.set('city', $('prCity').value.trim());
    p.set('country', $('prCountry').value.trim());
    p.set('method', $('prMethod').value);
    p.set('school', $('prSchool').value);
    if (tz) p.set('tz', tz);
    return p.toString();
  }

  function say(text, isError) {
    const el = $('prMessage');
    el.textContent = text || '';
    el.hidden = !text;
    el.classList.toggle('is-error', !!isError);
  }

  // ---- rendering (textContent only; never innerHTML with server data) ----
  function renderDay(day) {
    current = day;
    $('prDateLabel').textContent = day.dateLabel + (day.today ? ' (today)' : '');
    $('prWhere').textContent = day.city + ', ' + day.country;
    $('prPrev').disabled = !day.prevDate;
    $('prNext').disabled = !day.nextDate;
    $('prToday').hidden = day.today;

    const warn = $('prUnavailable');
    warn.hidden = day.timings.available;
    warn.textContent = day.timings.available ? '' : day.timings.message;
    $('prMethodNote').textContent = day.timings.available
      ? 'Calculated with ' + (day.timings.methodName || 'the selected method') + ', ' +
        (day.school === 1 ? 'Hanafi' : 'standard') + ' Asr. Times can differ from your local mosque timetable.'
      : '';

    const list = $('prList');
    list.replaceChildren();
    for (const item of day.prayers) {
      const li = document.createElement('li');
      li.className = 'pr-row' + (item.done ? ' is-done' : '') + (item.next ? ' is-next' : '');

      const name = document.createElement('span');
      name.className = 'pr-name';
      name.textContent = item.label;
      if (item.next) {
        const tag = document.createElement('span');
        tag.className = 'pr-tag';
        tag.textContent = 'Next';
        name.append(' ', tag);
      }

      const time = document.createElement('span');
      time.className = 'pr-time';
      time.textContent = item.time || '–';

      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'pr-toggle';
      btn.setAttribute('aria-pressed', String(item.done));
      btn.disabled = day.future;
      btn.title = day.future ? "You can't log a prayer for a future date" : '';
      const icon = document.createElement('i');
      icon.className = 'bi ' + (item.done ? 'bi-check-circle-fill' : 'bi-circle');
      btn.append(icon, ' ' + (item.done ? 'Prayed' : 'Mark as prayed'));
      btn.addEventListener('click', () => toggle(item.key, !item.done));

      li.append(name, time, btn);
      list.append(li);
    }
    renderProgress(day.doneCount, day.percent);
  }

  function renderProgress(count, percent) {
    $('prCount').textContent = count + '/5 marked';
    $('prPercent').textContent = percent + '%';
    $('prBarFill').style.width = percent + '%';
    $('prBar').setAttribute('aria-valuenow', String(percent));
  }

  function renderStats(s) {
    const box = $('prBeads');
    box.replaceChildren();
    for (const d of s.week) {
      const col = document.createElement('div');
      col.className = 'pr-col' + (d.today ? ' is-today' : '');
      col.setAttribute('role', 'img');
      col.setAttribute('aria-label', d.label + ': ' + d.completed + ' of 5 prayers marked');
      const beads = document.createElement('div');
      beads.className = 'pr-bead-stack';
      // top-to-bottom = Isha..Fajr so the stack fills upward through the day
      [...d.beads].reverse().forEach((on) => {
        const b = document.createElement('span');
        b.className = 'pr-bead' + (on ? ' on' : '');
        beads.append(b);
      });
      const label = document.createElement('span');
      label.className = 'pr-col-label';
      label.textContent = d.label;
      const n = document.createElement('span');
      n.className = 'pr-col-count';
      n.textContent = d.completed + '/5';
      col.append(beads, label, n);
      box.append(col);
    }
    $('prEmpty').hidden = s.hasAnyLogs;
    $('prWeek').textContent = s.weekCompleted + '/35 (' + s.weekPercent + '%)';
    $('prMonth').textContent = s.monthCompleted + '/150 (' + s.monthPercent + '%)';
    $('prStreak').textContent = s.streak === 0 ? 'None yet'
      : s.streak + (s.streakCapped ? '+' : '') + (s.streak === 1 ? ' day' : ' days');
  }

  // ---- actions ----
  async function loadDay(date) {
    try {
      renderDay(await api('/prayer/api/day?' + query(date)));
      say('');
    } catch (e) { say(e.message, true); }
  }

  async function loadStats() {
    try { renderStats(await api('/prayer/api/stats' + (tz ? '?tz=' + encodeURIComponent(tz) : ''))); }
    catch (e) { say(e.message, true); }
  }

  async function toggle(key, completed) {
    if (busy || !current || current.future) return;
    busy = true;
    try {
      const result = await api('/prayer/api/log', {
        method: 'PUT',
        body: JSON.stringify({ prayer: key, date: current.date, completed, tz }) });
      const label = current.prayers.find((p) => p.key === key)?.label || 'Prayer';
      say(result.completed ? label + ' marked as prayed.' : label + ' unmarked.');
      await Promise.all([loadDay(current.date), loadStats()]);
    } catch (e) { say(e.message, true); }
    finally { busy = false; }
  }

  $('prLocation').addEventListener('submit', (ev) => { ev.preventDefault(); savePrefs(); loadDay(current?.date); });
  $('prPrev').addEventListener('click', () => current?.prevDate && loadDay(current.prevDate));
  $('prNext').addEventListener('click', () => current?.nextDate && loadDay(current.nextDate));
  $('prToday').addEventListener('click', () => loadDay(''));

  loadPrefs();
  loadDay('');
  loadStats();
})();
