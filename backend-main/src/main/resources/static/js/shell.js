var btn = document.getElementById("avatarBtn");
var menu = document.getElementById("avatarMenu");
if (btn && menu) {
  btn.addEventListener("click", function (e) {
    e.stopPropagation();
    menu.classList.toggle("open");
  });
  document.addEventListener("click", function () { menu.classList.remove("open"); });
}

// Keep presence fresh on authenticated pages that use the shared top bar.
(() => {
  if (!document.querySelector('.sh-topbar')) return;
  const token = document.querySelector('meta[name="csrf-token"]')?.content
    || document.querySelector('form[action$="/logout"] input[name="_csrf"]')?.value;
  const header = document.querySelector('meta[name="csrf-header"]')?.content || 'X-CSRF-TOKEN';
  if (!token) return;
  let pending = false;
  let stopped = false;
  async function heartbeat() {
    if (pending || stopped) return;
    pending = true;
    try {
      const response = await fetch('/social/api/presence', {
        method: 'POST', credentials: 'same-origin', cache: 'no-store', headers: { [header]: token }
      });
      if (response.redirected || response.status === 401 || response.status === 403) stopped = true;
    } catch (error) {
      // A temporary connection failure will be retried at the next heartbeat.
    } finally { pending = false; }
  }
  heartbeat();
  window.setInterval(heartbeat, 30000);
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) heartbeat();
  });
})();
