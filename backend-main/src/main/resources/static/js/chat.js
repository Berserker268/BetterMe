(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const panel = $('chat-panel');
  const toggle = $('chat-toggle');
  if (!panel || !toggle) return;
  const viewerId = Number($('social-main').dataset.userId);
  const csrf = document.querySelector('meta[name="csrf-token"]').content;
  const csrfHeader = document.querySelector('meta[name="csrf-header"]').content;
  let view = 'inbox', mode = 'conversations', filter = 'all', listPage = 0, listVersion = 0;
  let groupPage = 0, groupVersion = 0, searchTimer, groupTimer, stream, refreshTimer;
  let active = null, threadVersion = 0, initialLoading = false, syncing = false, syncAgain = false;
  let threadReady = false;
  let oldest = 0, newest = 0, historyMore = false, earlierLoading = false, unreadVersion = 0;
  let sessionEnded = false, liveRefreshing = false;
  const rendered = new Map(), drafts = new Map(), readAcks = new Map(), reading = new Set(), sending = new Set(), selected = new Map();

  function node(tag, className, text) {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (text !== undefined) element.textContent = text;
    return element;
  }
  function icon(name) { const element = node('i', `bi bi-${name}`); element.setAttribute('aria-hidden', 'true'); return element; }
  function initials(name) { return name.trim().split(/\s+/).slice(0, 2).map(part => Array.from(part)[0] || '').join('').toUpperCase(); }
  function avatar(name, group, online) {
    const element = node('span', 'chat-avatar', group ? '' : initials(name));
    element.classList.toggle('is-group', Boolean(group)); element.classList.toggle('is-online', Boolean(online));
    if (group) element.append(icon('people-fill'));
    element.setAttribute('aria-hidden', 'true');
    return element;
  }
  function error(message = '') { $('chat-error').textContent = message; $('chat-error').hidden = !message; }
  function json(method, body) { return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }; }
  async function api(path, options = {}) {
    const headers = new Headers(options.headers);
    headers.set('Accept', 'application/json');
    if (options.method && options.method !== 'GET') headers.set(csrfHeader, csrf);
    const response = await fetch('/chat/api' + path, { ...options, headers, cache: 'no-store', credentials: 'same-origin' });
    if (response.redirected || response.status === 401 || !response.headers.get('content-type')?.includes('json')) {
      sessionEnded = true;
      stream?.close();
      $('chat-connection').textContent = 'Session expired. Refresh and sign in again.';
      throw new Error('Your session expired. Refresh and sign in again.');
    }
    const result = await response.json();
    if (!response.ok) throw new Error(result.message || 'Could not complete this action. Please try again.');
    return result;
  }
  function state(container, text) { container.replaceChildren(node('p', 'chat-state', text)); }
  function displayDate(value, short = false) {
    return new Date(value).toLocaleString([], short ? { month: 'short', day: 'numeric' } : { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' });
  }
  function showView(next) {
    view = next;
    $('chat-inbox').hidden = next !== 'inbox'; $('chat-thread').hidden = next !== 'thread'; $('chat-group').hidden = next !== 'group';
    $('chat-back').hidden = next === 'inbox'; $('chat-new').hidden = next !== 'inbox'; $('chat-new-group').hidden = next !== 'inbox';
    error();
    if (next === 'inbox') {
      $('chat-panel-title').textContent = mode === 'friends' ? 'New message' : 'Chats';
      $('chat-subtitle').textContent = mode === 'friends' ? 'Choose an accepted friend.' : 'Your circle, one message away.';
    } else if (next === 'group') {
      $('chat-panel-title').textContent = 'New group'; $('chat-subtitle').textContent = 'For your challenges and friend circles.';
    }
  }
  function open() {
    panel.hidden = false; toggle.setAttribute('aria-expanded', 'true'); toggle.setAttribute('aria-label', 'Close chats');
    if (view === 'thread' && active) { syncActive(); $('chat-message').focus(); }
    else if (view === 'group') $('chat-group-name').focus();
    else { loadInbox(false, true); $('chat-search').focus(); }
    refreshUnread();
  }
  function close() { panel.hidden = true; toggle.setAttribute('aria-expanded', 'false'); toggle.setAttribute('aria-label', 'Open chats'); toggle.focus(); }
  toggle.addEventListener('click', () => panel.hidden ? open() : close());
  $('chat-close').addEventListener('click', close);
  panel.addEventListener('keydown', event => { if (event.key === 'Escape') close(); });

  async function refreshUnread() {
    if (sessionEnded) return;
    const version = ++unreadVersion;
    try {
      const result = await api('/unread');
      if (version !== unreadVersion) return;
      const count = result.unread;
      $('chat-unread-badge').textContent = count > 99 ? '99+' : String(count);
      $('chat-unread-badge').hidden = count === 0;
      toggle.setAttribute('aria-label', `${panel.hidden ? 'Open' : 'Close'} chats${count ? ', ' + count + ' unread messages' : ''}`);
    } catch (failure) { if (!panel.hidden && sessionEnded) error(failure.message); }
  }
  function conversationRow(conversation) {
    const row = node('button', 'chat-row'); row.type = 'button';
    row.classList.toggle('is-unread', conversation.unread > 0);
    const content = node('span', 'chat-row-content');
    const prefix = conversation.lastSenderId === viewerId ? 'You: ' : conversation.groupChat && conversation.lastSender ? conversation.lastSender + ': ' : '';
    content.append(node('span', 'chat-row-name', conversation.name), node('span', 'chat-row-preview', conversation.lastMessage ? prefix + conversation.lastMessage : 'Say hello'));
    const tail = node('span', 'chat-row-tail');
    tail.append(node('span', 'chat-row-time', displayDate(conversation.updatedAt, true)));
    if (conversation.unread) tail.append(node('span', 'chat-count', conversation.unread > 99 ? '99+' : String(conversation.unread)));
    row.append(avatar(conversation.name, conversation.groupChat, conversation.online), content, tail);
    row.addEventListener('click', () => openConversation(conversation.id));
    return row;
  }
  function friendRow(friend) {
    const row = node('button', 'chat-row'); row.type = 'button';
    const content = node('span', 'chat-row-content');
    content.append(node('span', 'chat-row-name', friend.name), node('span', 'chat-row-preview', friend.online ? 'Online' : 'Send a message'));
    row.append(avatar(friend.name, false, friend.online), content);
    row.addEventListener('click', async () => {
      row.disabled = true; error();
      try {
        const details = await api('/direct', json('POST', { friendId: friend.id }));
        if (!panel.hidden) await openConversation(details.conversation.id, details);
      } catch (failure) { error(failure.message); } finally { row.disabled = false; }
    });
    return row;
  }
  async function loadInbox(append = false, showLoading = false) {
    if (view !== 'inbox' || panel.hidden || sessionEnded) return;
    const version = ++listVersion;
    const currentMode = mode, currentFilter = filter, query = $('chat-search').value.trim();
    const lastPage = append ? listPage + 1 : listPage;
    const path = currentMode === 'friends' ? `/friends?q=${encodeURIComponent(query)}` : `/conversations?filter=${currentFilter}`;
    if (showLoading) state($('chat-list'), currentMode === 'friends' ? 'Finding friends...' : 'Loading chats...');
    $('chat-list-more').disabled = true;
    try {
      let result, items = [];
      for (let page = append ? lastPage : 0; page <= lastPage; page++) {
        result = await api(path + '&page=' + page);
        items.push(...result.items);
        if (!result.hasMore) break;
      }
      if (version !== listVersion || view !== 'inbox' || currentMode !== mode || panel.hidden) return;
      if (!append) $('chat-list').replaceChildren();
      const seen = new Set(Array.from($('chat-list').querySelectorAll('[data-chat-key]')).map(row => row.dataset.chatKey));
      items.forEach(item => {
        if (seen.has(String(item.id))) return;
        const row = currentMode === 'friends' ? friendRow(item) : conversationRow(item);
        row.dataset.chatKey = String(item.id); $('chat-list').append(row); seen.add(String(item.id));
      });
      if (!$('chat-list').querySelector('[data-chat-key]')) state($('chat-list'), currentMode === 'friends'
        ? 'No friends found. Add and accept friends on Social to start chatting.'
        : currentFilter === 'unread' ? 'You are all caught up.' : currentFilter === 'groups' ? 'No groups yet. Create a circle with your friends.' : 'No conversations yet. Find a friend and say hello.');
      listPage = lastPage;
      $('chat-list-more').hidden = !result.hasMore;
    } catch (failure) { if (version === listVersion) error(failure.message); }
    finally { if (version === listVersion) $('chat-list-more').disabled = false; }
  }
  function inbox(friends = false) {
    listVersion++; threadVersion++; active = null; initialLoading = false; threadReady = false;
    mode = friends ? 'friends' : 'conversations'; listPage = 0;
    $('chat-search').value = ''; showView('inbox'); loadInbox(false, true); $('chat-search').focus();
  }
  $('chat-back').addEventListener('click', () => inbox());
  $('chat-new').addEventListener('click', () => inbox(true));
  $('chat-find-friends').addEventListener('click', () => inbox(true));
  $('chat-list-more').addEventListener('click', () => loadInbox(true));
  $('chat-search').addEventListener('input', () => {
    listVersion++; clearTimeout(searchTimer);
    searchTimer = setTimeout(() => {
      if ($('chat-search').value.trim()) mode = 'friends';
      listPage = 0; showView('inbox'); loadInbox(false, true);
    }, 300);
  });
  document.querySelectorAll('[data-chat-filter]').forEach(button => button.addEventListener('click', () => {
    filter = button.dataset.chatFilter;
    document.querySelectorAll('[data-chat-filter]').forEach(item => {
      item.classList.toggle('is-active', item === button); item.setAttribute('aria-pressed', String(item === button));
    });
    inbox();
  }));

  function renderHeader(details) {
    active = details.conversation;
    $('chat-panel-title').textContent = active.name;
    $('chat-subtitle').textContent = active.groupChat ? `${active.memberCount} members · Private group`
      : active.online ? 'Online · Friends only' : 'Friends only';
    $('chat-members').hidden = !active.groupChat;
    $('chat-member-list').replaceChildren(...details.members.map(person => node('div', '', person.name + (person.id === viewerId ? ' (you)' : ''))));
  }
  function restoreDraft() {
    if (!active) return;
    const draft = drafts.get(active.id);
    $('chat-message').value = draft?.text || '';
    $('chat-message').disabled = sending.has(active.id) || !threadReady;
    $('chat-send').disabled = sending.has(active.id) || !threadReady;
    if (draft?.error) error(draft.error);
  }
  async function openConversation(id, knownDetails) {
    const version = ++threadVersion; listVersion++;
    active = { id, name: knownDetails?.conversation.name || 'Messages', groupChat: false };
    oldest = 0; newest = 0; rendered.clear(); initialLoading = true; threadReady = false;
    showView('thread'); $('chat-panel-title').textContent = active.name; $('chat-subtitle').textContent = 'Loading conversation...';
    $('chat-members').hidden = true; $('chat-members').open = false;
    $('chat-history-more').hidden = true;
    state($('chat-messages'), 'Loading messages...'); restoreDraft();
    try {
      const [details, history] = await Promise.all([knownDetails || api(`/conversations/${id}`), api(`/conversations/${id}/messages`)]);
      if (version !== threadVersion || active?.id !== id) return;
      threadReady = true;
      renderHeader(details); $('chat-messages').replaceChildren();
      renderMessages(history.items);
      historyMore = history.hasMore; $('chat-history-more').hidden = !historyMore;
      if (!rendered.size) state($('chat-messages'), 'This is the start of your conversation. Say hello!');
      $('chat-messages').scrollTop = $('chat-messages').scrollHeight;
      restoreDraft();
      if (!panel.hidden) $('chat-message').focus();
      await markRead();
    } catch (failure) {
      if (version === threadVersion) {
        error(failure.message);
        state($('chat-messages'), 'Messages could not be loaded. Reopen this chat to try again.');
      }
    }
    finally {
      if (version === threadVersion) {
        initialLoading = false;
        if (threadReady) syncActive();
      }
    }
  }
  function renderMessages(items, prepend = false) {
    const fresh = items.filter(message => !rendered.has(message.id));
    if (!fresh.length) return;
    $('chat-messages').querySelectorAll('.chat-state').forEach(element => element.remove());
    const fragment = document.createDocumentFragment();
    fresh.forEach(message => {
      const row = node('article', 'chat-message'); row.classList.toggle('is-own', message.senderId === viewerId);
      if (active.groupChat && message.senderId !== viewerId) row.append(node('span', 'chat-message-sender', message.senderName));
      const stamp = node('time', '', displayDate(message.createdAt)); stamp.dateTime = message.createdAt;
      row.append(node('p', 'chat-message-text', message.text), stamp);
      row.dataset.messageId = String(message.id); rendered.set(message.id, row);
      oldest = oldest ? Math.min(oldest, message.id) : message.id; newest = Math.max(newest, message.id);
      fragment.append(row);
    });
    if (prepend) $('chat-messages').prepend(fragment); else $('chat-messages').append(fragment);
  }
  async function markRead() {
    if (!active || view !== 'thread' || panel.hidden || document.hidden || !newest) return;
    const box = $('chat-messages');
    if (box.scrollHeight - box.scrollTop - box.clientHeight > 40) return;
    const id = active.id, throughId = newest;
    if (reading.has(id) || throughId <= (readAcks.get(id) || 0)) return;
    reading.add(id); let success = false;
    try {
      await api(`/conversations/${id}/read`, json('PUT', { throughId }));
      readAcks.set(id, Math.max(throughId, readAcks.get(id) || 0)); success = true; refreshUnread();
    } catch (failure) { if (sessionEnded && !panel.hidden) error(failure.message); }
    finally {
      reading.delete(id);
      if (success && active?.id === id && newest > throughId) markRead();
    }
  }
  $('chat-messages').addEventListener('scroll', () => markRead(), { passive: true });
  async function syncActive() {
    if (view !== 'thread' || !active || panel.hidden || document.hidden || sessionEnded) return;
    if (initialLoading || syncing) { syncAgain = true; return; }
    if (!threadReady) { await openConversation(active.id); return; }
    syncing = true; syncAgain = false;
    const id = active.id, version = threadVersion;
    const box = $('chat-messages');
    const follow = box.scrollHeight - box.scrollTop - box.clientHeight < 80;
    try {
      let more = true, batches = 0;
      while (more && batches++ < 10) {
        const result = await api(`/conversations/${id}/messages?after=${newest}`);
        if (version !== threadVersion || active?.id !== id) return;
        renderMessages(result.items); more = result.hasMore && result.items.length > 0;
      }
      if (more) syncAgain = true;
      if (follow) box.scrollTop = box.scrollHeight;
      await markRead();
    } catch (failure) { if (version === threadVersion) error(failure.message); }
    finally {
      syncing = false;
      if (syncAgain && !initialLoading) { syncAgain = false; setTimeout(syncActive, 100); }
    }
  }
  $('chat-history-more').addEventListener('click', async () => {
    if (!active || earlierLoading || !oldest) return;
    earlierLoading = true; $('chat-history-more').disabled = true;
    const id = active.id, version = threadVersion, box = $('chat-messages'), height = box.scrollHeight, top = box.scrollTop;
    try {
      const result = await api(`/conversations/${id}/messages?before=${oldest}`);
      if (version !== threadVersion) return;
      renderMessages(result.items, true); historyMore = result.hasMore;
      $('chat-history-more').hidden = !historyMore; box.scrollTop = top + box.scrollHeight - height;
    } catch (failure) { if (version === threadVersion) error(failure.message); }
    finally { earlierLoading = false; $('chat-history-more').disabled = false; }
  });
  $('chat-message').addEventListener('input', () => {
    if (active) drafts.set(active.id, { text: $('chat-message').value, clientId: crypto.randomUUID() });
  });
  $('chat-message').addEventListener('keydown', event => {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault(); if (!$('chat-send').disabled) $('chat-message-form').requestSubmit();
    }
  });
  $('chat-message-form').addEventListener('submit', async event => {
    event.preventDefault();
    if (!active || sending.has(active.id) || initialLoading || !threadReady) return;
    const id = active.id, text = $('chat-message').value;
    if (!text.trim()) { error('Write a message first.'); return; }
    let draft = drafts.get(id);
    if (!draft || draft.text !== text) { draft = { text, clientId: crypto.randomUUID() }; drafts.set(id, draft); }
    sending.add(id); restoreDraft(); error();
    try {
      await api(`/conversations/${id}/messages`, json('POST', { text, clientId: draft.clientId }));
      if (drafts.get(id)?.clientId === draft.clientId) drafts.delete(id);
      if (active?.id === id) {
        $('chat-message').value = '';
        $('chat-messages').scrollTop = $('chat-messages').scrollHeight;
        await syncActive();
      }
    } catch (failure) {
      draft.error = failure.message;
      if (active?.id === id) error(failure.message);
    } finally {
      sending.delete(id);
      if (active?.id === id) { restoreDraft(); if (!panel.hidden) $('chat-message').focus(); }
      refreshUnread();
    }
  });

  function renderSelected() {
    $('chat-selected-members').replaceChildren(...Array.from(selected.values()).map(person => {
      const chip = node('button', 'chat-chip', person.name + ' ×'); chip.type = 'button'; chip.setAttribute('aria-label', `Remove ${person.name}`);
      chip.addEventListener('click', () => {
        selected.delete(person.id); renderSelected();
        const checkbox = $('chat-group-friends').querySelector(`input[data-person-id="${person.id}"]`); if (checkbox) checkbox.checked = false;
      });
      return chip;
    }));
  }
  async function loadGroupFriends(append = false) {
    const version = ++groupVersion, page = append ? groupPage + 1 : 0;
    const query = $('chat-group-search').value.trim();
    $('chat-group-more').disabled = true;
    try {
      const result = await api(`/friends?q=${encodeURIComponent(query)}&page=${page}`);
      if (version !== groupVersion || view !== 'group') return;
      if (!append) $('chat-group-friends').replaceChildren();
      result.items.forEach(person => {
        const row = node('label', 'chat-row');
        const info = node('span', 'chat-row-content'); info.append(node('span', 'chat-row-name', person.name));
        const checkbox = node('input'); checkbox.type = 'checkbox'; checkbox.checked = selected.has(person.id); checkbox.dataset.personId = String(person.id);
        checkbox.setAttribute('aria-label', `Add ${person.name} to group`);
        checkbox.addEventListener('change', () => {
          if (checkbox.checked && selected.size >= 49) { checkbox.checked = false; error('A group can have up to 50 people, including you.'); return; }
          if (checkbox.checked) selected.set(person.id, person); else selected.delete(person.id);
          renderSelected();
        });
        row.append(avatar(person.name, false, person.online), info, checkbox); $('chat-group-friends').append(row);
      });
      if (!$('chat-group-friends').children.length) state($('chat-group-friends'), 'No friends found. Accept friend requests on Social first.');
      groupPage = page; $('chat-group-more').hidden = !result.hasMore;
    } catch (failure) { if (version === groupVersion) error(failure.message); }
    finally { if (version === groupVersion) $('chat-group-more').disabled = false; }
  }
  $('chat-new-group').addEventListener('click', () => {
    listVersion++; threadVersion++; active = null; initialLoading = false;
    showView('group'); loadGroupFriends(); $('chat-group-name').focus();
  });
  $('chat-group-more').addEventListener('click', () => loadGroupFriends(true));
  $('chat-group-search').addEventListener('input', () => {
    groupVersion++; clearTimeout(groupTimer); groupTimer = setTimeout(() => loadGroupFriends(), 300);
  });
  $('chat-group-form').addEventListener('submit', async event => {
    event.preventDefault();
    if ($('chat-create-group').disabled) return;
    if (selected.size < 2) { error('Choose at least two friends for a group.'); return; }
    $('chat-create-group').disabled = true; error();
    try {
      const details = await api('/groups', json('POST', { name: $('chat-group-name').value, friendIds: Array.from(selected.keys()) }));
      selected.clear(); renderSelected(); $('chat-group-form').reset();
      if (view === 'group' && !panel.hidden) await openConversation(details.conversation.id, details);
    } catch (failure) { error(failure.message); }
    finally { $('chat-create-group').disabled = false; }
  });

  async function refreshLive() {
    if (sessionEnded || liveRefreshing || document.hidden) return;
    liveRefreshing = true;
    try {
      await refreshUnread();
      if (!panel.hidden) {
        if (view === 'inbox') await loadInbox();
        else if (view === 'thread' && active) {
          const id = active.id, version = threadVersion;
          await syncActive();
          try {
            const details = await api(`/conversations/${id}`);
            if (version === threadVersion && active?.id === id) renderHeader(details);
          } catch (failure) { if (version === threadVersion) error(failure.message); }
        }
      }
    } finally { liveRefreshing = false; }
  }
  function scheduleRefresh() { clearTimeout(refreshTimer); refreshTimer = setTimeout(refreshLive, 100); }
  function connect() {
    if (sessionEnded || stream) return;
    stream = new EventSource('/chat/api/events');
    stream.onopen = () => { $('chat-connection').textContent = 'Live'; scheduleRefresh(); };
    stream.addEventListener('chat-changed', scheduleRefresh);
    stream.onerror = () => { $('chat-connection').textContent = 'Reconnecting... Your messages are saved.'; };
  }
  document.addEventListener('visibilitychange', () => { if (!document.hidden) { connect(); refreshLive(); markRead(); } });
  window.addEventListener('online', () => { if (stream) stream.close(); stream = null; connect(); refreshLive(); });
  window.addEventListener('pagehide', () => { stream?.close(); stream = null; });
  window.addEventListener('pageshow', () => { connect(); refreshLive(); });
  window.setInterval(refreshLive, 15000);
  connect(); refreshUnread();
})();
