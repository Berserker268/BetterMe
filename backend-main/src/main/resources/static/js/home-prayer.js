(() => {
  'use strict';
  const name = document.getElementById('home-prayer-name');
  const time = document.getElementById('home-prayer-time');
  if (!name || !time) return;
  const prefsKey = 'betterme.prayer.prefs';
  let pending = false;

  function settings() {
    const query = new URLSearchParams();
    try {
      const prefs = JSON.parse(localStorage.getItem(prefsKey) || 'null');
      if (prefs && typeof prefs === 'object') {
        for (const key of ['city', 'country', 'method', 'school']) {
          const value = prefs[key];
          if ((typeof value === 'string' || typeof value === 'number') && String(value).trim()) {
            query.set(key, String(value).trim());
          }
        }
      }
    } catch (error) { /* Use the Prayer service defaults when preferences are unavailable. */ }
    try {
      const zone = Intl.DateTimeFormat().resolvedOptions().timeZone;
      if (zone) query.set('tz', zone);
    } catch (error) { /* The Prayer service has a default timezone. */ }
    return query;
  }

  async function day(query) {
    const response = await fetch('/prayer/api/day?' + query.toString(), {
      credentials: 'same-origin', cache: 'no-store', headers: { Accept: 'application/json' }
    });
    if (!response.ok || response.redirected || !response.headers.get('content-type')?.includes('json')) {
      throw new Error('Open Prayer to check your connection and location settings.');
    }
    const result = await response.json();
    if (!result.timings?.available) throw new Error(result.timings?.message || 'Prayer times are temporarily unavailable.');
    return result;
  }

  async function refresh() {
    if (pending || document.hidden) return;
    pending = true;
    try {
      const query = settings();
      const today = await day(query);
      let next = today.prayers.find(prayer => prayer.next && prayer.time);
      let tomorrow = false;
      if (!next && today.nextDate) {
        query.set('date', today.nextDate);
        const followingDay = await day(query);
        next = followingDay.prayers.find(prayer => prayer.key === 'FAJR' && prayer.time);
        tomorrow = true;
      }
      if (!next) throw new Error('No upcoming prayer time is available. Open Prayer for details.');
      name.textContent = next.label;
      time.textContent = (tomorrow ? 'Tomorrow · ' : '') + next.time;
      name.title = `${today.city}, ${today.country}`;
      time.title = today.timings.timezone || '';
    } catch (error) {
      name.textContent = 'Unavailable';
      time.textContent = 'Open Prayer for details';
      name.title = error.message;
      time.title = error.message;
    } finally { pending = false; }
  }

  refresh();
  window.setInterval(refresh, 60000);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
  window.addEventListener('storage', event => { if (event.key === prefsKey || event.key === null) refresh(); });
  window.addEventListener('online', refresh);
})();
