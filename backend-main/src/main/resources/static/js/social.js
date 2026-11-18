(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const main = $('social-main');
  const csrf = document.querySelector('meta[name="csrf-token"]').content;
  const csrfHeader = document.querySelector('meta[name="csrf-header"]').content;
  const me = { id: Number(main.dataset.userId), name: main.dataset.userName };
  document.addEventListener('click', event => {
    document.querySelectorAll('.social-comment-menu[open]').forEach(menu => {
      if (!menu.contains(event.target)) menu.open = false;
    });
  });
  let photoUrl, noticeTimer, feedPage = 0, requestPage = 0, friendPage = 0, searchPage = 0;
  let searchQuery = '', searchVersion = 0, searchTimer;

  function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }
  function icon(name) { const node = el('i', `bi bi-${name}`); node.setAttribute('aria-hidden', 'true'); return node; }
  function button(text, className, action) {
    const node = el('button', className, text);
    node.type = 'button';
    node.addEventListener('click', action);
    return node;
  }
  function avatar(person) {
    const node = el('span', 'social-avatar', initials(person.name));
    node.dataset.tone = String(person.id % 4);
    node.setAttribute('aria-hidden', 'true');
    return node;
  }
  function initials(name) { return name.trim().split(/\s+/).slice(0, 2).map(part => Array.from(part)[0] || '').join('').toUpperCase(); }
  function time(value) {
    const date = new Date(value);
    const node = el('time', '', date.toLocaleString([], { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' }));
    node.dateTime = value;
    node.title = date.toLocaleString();
    return node;
  }
  async function api(path, options = {}) {
    const headers = new Headers(options.headers);
    headers.set('Accept', 'application/json');
    if (options.method && options.method !== 'GET') headers.set(csrfHeader, csrf);
    const response = await fetch('/social/api' + path, { ...options, headers, credentials: 'same-origin', cache: 'no-store' });
    if (response.redirected || response.status === 401) throw new Error('Your session expired. Refresh the page and log in again.');
    const json = response.headers.get('content-type')?.includes('application/json') ? await response.json() : null;
    if (!response.ok) throw new Error(json?.message || (response.status === 413 ? 'Choose a photo smaller than 2 MB.' : response.status === 403 ? 'This action is unavailable. Refresh the page and try again.' : 'Could not complete this action. Please try again.'));
    if (json === null) throw new Error('Could not load Social. Please refresh the page.');
    return json;
  }
  function jsonOptions(method, data) { return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) }; }
  function notice(message) {
    clearTimeout(noticeTimer);
    $('social-notice').textContent = message;
    $('social-notice').hidden = false;
    noticeTimer = setTimeout(() => { $('social-notice').hidden = true; }, 3000);
  }
  function setError(node, message) { node.textContent = message || ''; node.hidden = !message; }
  function retry(container, error, action, append = false) {
    const box = el('div', 'social-load-error');
    box.append(el('p', 'social-error', error.message), button('Try again', 'social-secondary', () => { box.remove(); action(); }));
    if (!append) container.replaceChildren();
    container.append(box);
  }
  async function busy(node, action, onError) {
    node.disabled = true;
    try { await action(); } catch (error) { onError(error); } finally { node.disabled = false; }
  }

  $('composer-avatar').replaceWith(Object.assign(avatar(me), { id: 'composer-avatar' }));
  function clearPhoto() {
    if (photoUrl) URL.revokeObjectURL(photoUrl);
    photoUrl = null;
    $('post-photo').value = '';
    $('photo-preview').removeAttribute('src');
    $('photo-preview-wrap').hidden = true;
  }
  $('add-photo').addEventListener('click', () => $('post-photo').click());
  $('remove-photo').addEventListener('click', clearPhoto);
  $('post-photo').addEventListener('change', () => {
    const file = $('post-photo').files[0];
    setError($('post-error'), '');
    if (!file) { clearPhoto(); return; }
    if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 2 * 1024 * 1024) {
      clearPhoto();
      setError($('post-error'), 'Choose one JPEG or PNG photo smaller than 2 MB.');
      return;
    }
    if (photoUrl) URL.revokeObjectURL(photoUrl);
    photoUrl = URL.createObjectURL(file);
    $('photo-preview').src = photoUrl;
    $('photo-preview-wrap').hidden = false;
  });
  $('post-form').addEventListener('submit', event => {
    event.preventDefault();
    setError($('post-error'), '');
    busy($('publish-post'), async () => {
      const body = new FormData($('post-form'));
      if (!$('post-photo').files.length) body.delete('photo');
      await api('/posts', { method: 'POST', body });
      $('post-form').reset();
      clearPhoto();
      notice('Progress shared with your friends.');
      await loadFeed(0);
    }, error => setError($('post-error'), error.message));
  });

  const categoryIcons = { Study: 'book', Fitness: 'activity', Prayer: 'moon-stars', Habit: 'repeat', Skill: 'lightbulb', Other: 'flag' };
  function postCard(post) {
    const card = el('article', 'social-card social-post');
    const head = el('header', 'social-post-head');
    const author = el('div', 'social-post-author');
    author.append(el('strong', 'social-post-name', post.author.name));
    const meta = el('div', 'social-post-meta');
    if (post.category) { const tag = el('span', 'social-category'); tag.append(icon(categoryIcons[post.category] || 'flag'), document.createTextNode(post.category)); meta.append(tag); }
    meta.append(time(post.createdAt), el('span', '', 'Friends only'));
    author.append(meta);
    head.append(avatar(post.author), author);
    const tools = el('div', 'social-post-tools');
    async function removeFromFeed(control, deleting) {
      if (deleting && !window.confirm('Permanently delete this post, its photo, likes, and comments?')) return;
      tools.querySelectorAll('button').forEach(node => { node.disabled = true; });
      await busy(control, async () => {
        setError(error, '');
        const result = await api(`/posts/${post.id}${deleting ? '' : '/hidden'}`, { method: deleting ? 'DELETE' : 'PUT' });
        notice(result.message);
        await loadFeed(0);
      }, failure => {
        tools.querySelectorAll('button').forEach(node => { node.disabled = false; });
        setError(error, failure.message);
      });
    }
    if (post.author.id === me.id) {
      const menu = el('details', 'social-post-menu');
      menu.name = 'social-post-options';
      const toggle = el('summary', 'social-post-icon');
      toggle.append(icon('three-dots'));
      toggle.setAttribute('aria-label', 'Post options');
      toggle.title = 'Post options';
      const dropdown = el('div', 'social-post-dropdown');
      const deletePost = button('Delete post', 'social-delete-post', () => removeFromFeed(deletePost, true));
      dropdown.append(deletePost);
      menu.append(toggle, dropdown);
      menu.addEventListener('keydown', event => {
        if (event.key === 'Escape') { menu.open = false; toggle.focus(); }
      });
      tools.append(menu);
    }
    const hidePost = button('', 'social-post-icon', () => removeFromFeed(hidePost, false));
    hidePost.append(icon('x-lg'));
    hidePost.setAttribute('aria-label', 'Remove post from your feed');
    hidePost.title = 'Remove from your feed';
    tools.append(hidePost);
    head.append(tools);
    card.append(head, el('p', 'social-post-text', post.text));
    if (post.photoUrl) {
      const image = el('img', 'social-post-photo');
      image.src = post.photoUrl;
      image.alt = `Photo shared by ${post.author.name}`;
      image.loading = 'lazy';
      image.addEventListener('error', () => image.replaceWith(el('p', 'social-error', 'This photo could not be loaded. Refresh to try again.')), { once: true });
      card.append(image);
    }
    const error = el('p', 'social-error'); error.hidden = true; error.setAttribute('role', 'alert');
    const actions = el('div', 'social-post-actions');
    const like = button('', 'social-text-button social-like', () => busy(like, async () => {
      setError(error, '');
      const result = await api(`/posts/${post.id}/like`, jsonOptions('PUT', { liked: !post.liked }));
      post.liked = result.liked; post.likes = result.count; updateLike();
    }, failure => setError(error, failure.message)));
    function updateLike() {
      like.replaceChildren(icon(post.liked ? 'hand-thumbs-up-fill' : 'hand-thumbs-up'), document.createTextNode(`Like${post.likes ? ' · ' + post.likes : ''}`));
      like.classList.toggle('is-liked', post.liked);
      like.setAttribute('aria-pressed', String(post.liked));
    }
    updateLike();
    const thread = el('section', 'social-comments'); thread.hidden = true; thread.id = `comments-${post.id}`;
    const list = el('div'); list.setAttribute('aria-live', 'polite');
    const more = button('Older thread entries', 'social-more', () => loadComments(commentPage + 1)); more.hidden = true;
    let commentPage = 0, loaded = false;
    const seenComments = new Set();
    const commentButton = button('', 'social-text-button', () => {
      thread.hidden = !thread.hidden;
      commentButton.setAttribute('aria-expanded', String(!thread.hidden));
      if (!thread.hidden && !loaded) loadComments(0);
    });
    function updateCommentCount() { commentButton.replaceChildren(icon('chat-square'), document.createTextNode(`Comment${post.comments ? ' · ' + post.comments : ''}`)); }
    updateCommentCount(); commentButton.setAttribute('aria-expanded', 'false'); commentButton.setAttribute('aria-controls', thread.id);
    const form = el('form', 'social-comment-form');
    const input = el('textarea'); input.rows = 2; input.maxLength = 1000; input.required = true; input.placeholder = 'Write a comment...'; input.setAttribute('aria-label', `Comment on ${post.author.name}'s post`);
    const submit = el('button', 'social-primary', 'Send'); submit.type = 'submit';
    const commentError = el('p', 'social-error'); commentError.hidden = true; commentError.setAttribute('role', 'alert');
    form.append(input, submit);
    function commentLikeControl(comment) {
      const control = el('span', 'social-comment-like-control');
      const likeError = el('span', 'social-error'); likeError.hidden = true; likeError.setAttribute('role', 'alert');
      const likeButton = button('', 'social-text-button social-comment-like', () => busy(likeButton, async () => {
        setError(likeError, '');
        const result = await api(`/posts/${post.id}/comments/${comment.id}/like`, jsonOptions('PUT', { liked: !comment.liked }));
        comment.liked = result.liked;
        comment.likes = result.count;
        update();
      }, failure => setError(likeError, failure.message)));
      function update() {
        likeButton.replaceChildren(icon(comment.liked ? 'hand-thumbs-up-fill' : 'hand-thumbs-up'),
          document.createTextNode(`Like${comment.likes ? ' · ' + comment.likes : ''}`));
        likeButton.classList.toggle('is-liked', Boolean(comment.liked));
        likeButton.setAttribute('aria-pressed', String(Boolean(comment.liked)));
        likeButton.setAttribute('aria-label', `Like comment by ${comment.author.name}`);
      }
      update();
      control.append(likeButton, likeError);
      return control;
    }
    function addCommentDeleteControl(content, comment, isReply = false) {
      if (comment.author.id !== me.id) return;
      const menu = el('details', 'social-comment-menu');
      menu.name = 'social-comment-options';
      const toggle = el('summary', 'social-post-icon');
      toggle.append(icon('three-dots'));
      toggle.setAttribute('aria-label', isReply ? 'Reply options' : 'Comment options');
      toggle.title = isReply ? 'Reply options' : 'Comment options';
      const dropdown = el('div', 'social-post-dropdown');
      const deleteError = el('span', 'social-error'); deleteError.hidden = true; deleteError.setAttribute('role', 'alert');
      const deleteButton = button('Delete', 'social-delete-post', () => {
        const message = isReply ? 'Permanently delete this reply?' : 'Permanently delete this comment and all its replies?';
        if (!window.confirm(message)) return;
        busy(deleteButton, async () => {
          setError(deleteError, '');
          const result = await api(`/posts/${post.id}/comments/${comment.id}`, { method: 'DELETE' });
          post.comments = Math.max(0, post.comments - result.removed);
          updateCommentCount();
          notice(isReply ? 'Reply deleted.' : 'Comment deleted.');
          await loadComments(0);
        }, failure => setError(deleteError, failure.message));
      });
      dropdown.append(deleteButton, deleteError);
      menu.append(toggle, dropdown);
      menu.addEventListener('keydown', event => {
        if (event.key === 'Escape') { menu.open = false; toggle.focus(); }
      });
      content.append(menu);
    }
    function addComment(comment) {
      if (seenComments.has(comment.id)) return;
      seenComments.add(comment.id);
      const row = el('article', 'social-comment');
      const content = el('div', 'social-comment-content');
      content.append(el('strong', 'social-post-name', comment.author.name), time(comment.createdAt), el('p', '', comment.text));
      const replyActions = el('div', 'social-reply-actions');
      const replies = el('section', 'social-replies'); replies.hidden = true;
      replies.setAttribute('aria-label', `Replies to ${comment.author.name}`);
      const replyList = el('div'); replyList.setAttribute('aria-live', 'polite');
      const replyError = el('p', 'social-error'); replyError.hidden = true; replyError.setAttribute('role', 'alert');
      const replyForm = el('form', 'social-comment-form social-reply-form');
      const replyingTo = el('p', 'social-reply-target'); replyingTo.hidden = true;
      let replyTarget = comment;
      const replyInput = el('textarea'); replyInput.rows = 2; replyInput.maxLength = 1000; replyInput.required = true;
      replyInput.placeholder = `Reply to ${comment.author.name}...`;
      replyInput.setAttribute('aria-label', `Reply to ${comment.author.name}`);
      const sendReply = el('button', 'social-primary', 'Reply'); sendReply.type = 'submit';
      const cancelReply = button('Cancel', 'social-text-button', () => {
        replies.hidden = true;
        viewReplies.setAttribute('aria-expanded', 'false');
        replyButton.setAttribute('aria-expanded', 'false');
        replyButton.focus();
      });
      replyForm.append(replyInput, sendReply);
      let repliesLoaded = false, replyPage = 0;
      const seenReplies = new Set();
      const moreReplies = button('Load more replies', 'social-more', () => loadReplies(replyPage + 1)); moreReplies.hidden = true;
      function beginReply(target) {
        if (sendReply.disabled) return;
        replyTarget = target;
        replyInput.placeholder = `Reply to ${target.author.name}...`;
        replyInput.setAttribute('aria-label', `Reply to ${target.author.name}`);
        replyingTo.textContent = `Replying to ${target.author.name}`;
        replyingTo.hidden = target.id === comment.id;
        replies.hidden = false;
        replyButton.setAttribute('aria-expanded', 'true');
        viewReplies.setAttribute('aria-expanded', 'true');
        if (!repliesLoaded) loadReplies(0);
        replyInput.focus();
      }
      const replyButton = button('Reply', 'social-text-button social-reply-button', () => beginReply(comment));
      replyButton.setAttribute('aria-expanded', 'false');
      const viewReplies = button('', 'social-text-button social-reply-button', () => {
        replies.hidden = !replies.hidden;
        viewReplies.setAttribute('aria-expanded', String(!replies.hidden));
        replyButton.setAttribute('aria-expanded', String(!replies.hidden));
        if (!replies.hidden && !repliesLoaded) loadReplies(0);
      });
      viewReplies.setAttribute('aria-expanded', 'false');
      function updateReplyCount() {
        const count = comment.replies || 0;
        viewReplies.textContent = `${count} ${count === 1 ? 'reply' : 'replies'}`;
        viewReplies.hidden = count === 0;
      }
      function addReply(reply) {
        if (seenReplies.has(reply.id)) return;
        seenReplies.add(reply.id);
        const replyRow = el('article', 'social-comment social-reply');
        const replyContent = el('div', 'social-comment-content');
        replyContent.append(el('strong', 'social-post-name', reply.author.name), time(reply.createdAt));
        if (reply.replyingTo) replyContent.append(el('div', 'social-reply-target', `Replying to ${reply.replyingTo.name}`));
        replyContent.append(el('p', '', reply.text));
        const nestedActions = el('div', 'social-reply-actions');
        nestedActions.append(commentLikeControl(reply), button('Reply', 'social-text-button social-reply-button', () => beginReply(reply)));
        addCommentDeleteControl(replyContent, reply, true);
        replyContent.append(nestedActions);
        replyRow.append(avatar(reply.author), replyContent);
        replyList.append(replyRow);
      }
      async function loadReplies(page) {
        moreReplies.disabled = true;
        if (page === 0) replyList.replaceChildren(el('p', 'social-state', 'Loading replies...'));
        try {
          const result = await api(`/posts/${post.id}/comments/${comment.id}/replies?page=${page}`);
          if (page === 0) { replyList.replaceChildren(); seenReplies.clear(); }
          result.items.forEach(addReply);
          repliesLoaded = true; replyPage = result.page; moreReplies.hidden = !result.hasNext;
        } catch (failure) { retry(replyList, failure, () => loadReplies(page), page > 0); }
        finally { moreReplies.disabled = false; }
      }
      replyForm.addEventListener('submit', event => {
        event.preventDefault();
        busy(sendReply, async () => {
          setError(replyError, '');
          const reply = await api(`/posts/${post.id}/comments/${comment.id}/replies`, jsonOptions('POST', { text: replyInput.value, replyToId: replyTarget.id }));
          replyInput.value = '';
          replyTarget = comment;
          replyInput.placeholder = `Reply to ${comment.author.name}...`;
          replyInput.setAttribute('aria-label', `Reply to ${comment.author.name}`);
          replyingTo.hidden = true;
          comment.replies = (comment.replies || 0) + 1;
          post.comments++; updateCommentCount(); updateReplyCount();
          if (!repliesLoaded) await loadReplies(0);
          addReply(reply);
        }, failure => setError(replyError, failure.message));
      });
      updateReplyCount();
      replyActions.append(commentLikeControl(comment), replyButton, viewReplies);
      addCommentDeleteControl(content, comment);
      replies.append(replyList, moreReplies, replyingTo, replyForm, cancelReply, replyError);
      content.append(replyActions, replies);
      row.append(avatar(comment.author), content);
      list.append(row);
    }
    async function loadComments(page) {
      more.disabled = true;
      if (page === 0) list.replaceChildren(el('p', 'social-state', 'Loading comments...'));
      try {
        const result = await api(`/posts/${post.id}/comments?page=${page}`);
        if (page === 0) { list.replaceChildren(); seenComments.clear(); }
        result.items.forEach(addComment);
        if (page === 0 && !result.items.length) list.append(el('p', 'social-state', 'Start the conversation.'));
        loaded = true; commentPage = result.page; more.hidden = !result.hasNext;
        more.textContent = 'Load more comments';
      } catch (failure) { retry(list, failure, () => loadComments(page), page > 0); }
      finally { more.disabled = false; }
    }
    form.addEventListener('submit', event => {
      event.preventDefault();
      busy(submit, async () => {
        setError(commentError, '');
        const comment = await api(`/posts/${post.id}/comments`, jsonOptions('POST', { text: input.value }));
        input.value = ''; post.comments++; updateCommentCount();
        if (!loaded) await loadComments(0);
        list.querySelectorAll('.social-state').forEach(node => node.remove());
        addComment(comment);
      }, failure => setError(commentError, failure.message));
    });
    thread.append(list, more, form, commentError);
    actions.append(like, commentButton);
    card.append(actions, error, thread);
    return card;
  }

  async function loadFeed(page) {
    $('feed-more').disabled = true;
    if (page === 0) $('feed-list').replaceChildren(el('p', 'social-state', 'Loading your feed...'));
    try {
      const result = await api(`/feed?page=${page}`);
      if (page === 0) $('feed-list').replaceChildren();
      result.items.forEach(post => $('feed-list').append(postCard(post)));
      if (page === 0 && !result.items.length) {
        const empty = el('div', 'social-card social-empty');
        empty.append(icon('people'), el('h2', '', 'Small wins are better together'), el('p', 'social-state', 'Share your first update or add a friend to see their progress here.'));
        $('feed-list').append(empty);
      }
      feedPage = result.page; $('feed-more').hidden = !result.hasNext;
    } catch (failure) { retry($('feed-list'), failure, () => loadFeed(page), page > 0); }
    finally { $('feed-more').disabled = false; }
  }
  $('feed-more').addEventListener('click', () => loadFeed(feedPage + 1));

  function personRow(person) {
    const row = el('div', 'social-person');
    const info = el('div', 'social-person-info');
    info.append(el('div', 'social-person-name', person.name));
    row.append(avatar(person), info);
    return { row, info };
  }
  function friendAction(info, text, action, secondary = false) {
    let actions = info.querySelector('.social-person-actions');
    if (!actions) { actions = el('div', 'social-person-actions'); info.append(actions); }
    let error = info.querySelector('.social-error');
    if (!error) { error = el('p', 'social-error'); error.hidden = true; error.setAttribute('role', 'alert'); info.append(error); }
    const control = button(text, secondary ? 'social-secondary' : 'social-primary', () => {
      actions.querySelectorAll('button').forEach(node => { node.disabled = true; });
      busy(control, async () => {
        setError(error, '');
        const result = await action();
        notice(result.message);
        await Promise.all([loadRequests(0), loadFriends(0), loadFeed(0)]);
        if (searchQuery.length >= 2) await loadSearch(0);
      }, failure => {
        actions.querySelectorAll('button').forEach(node => { node.disabled = false; });
        setError(error, failure.message);
      });
    });
    actions.append(control);
  }
  async function loadRequests(page) {
    $('requests-more').disabled = true;
    try {
      const result = await api(`/requests?page=${page}`);
      if (page === 0) $('request-list').replaceChildren();
      result.items.forEach(request => {
        const { row, info } = personRow(request.person);
        friendAction(info, 'Accept', () => api(`/requests/${request.id}/accept`, { method: 'POST' }));
        friendAction(info, 'Decline', () => api(`/requests/${request.id}/decline`, { method: 'POST' }), true);
        $('request-list').append(row);
      });
      if (page === 0 && !result.items.length) $('request-list').append(el('p', 'social-state', 'No pending requests.'));
      $('request-count').textContent = String(result.total);
      $('incoming-total').textContent = result.total ? `(${result.total})` : '';
      requestPage = result.page; $('requests-more').hidden = !result.hasNext;
    } catch (failure) { retry($('request-list'), failure, () => loadRequests(page), page > 0); }
    finally { $('requests-more').disabled = false; }
  }
  async function loadFriends(page) {
    $('friends-more').disabled = true;
    try {
      const result = await api(`/friends?page=${page}`);
      if (page === 0) $('friend-list').replaceChildren();
      result.items.forEach(person => $('friend-list').append(personRow(person).row));
      if (page === 0 && !result.items.length) $('friend-list').append(el('p', 'social-state', 'Your circle starts here. Add a new Friend by searching them up.'));
      $('friends-total').textContent = `(${result.total})`;
      friendPage = result.page; $('friends-more').hidden = !result.hasNext;
    } catch (failure) { retry($('friend-list'), failure, () => loadFriends(page), page > 0); }
    finally { $('friends-more').disabled = false; }
  }
  $('requests-more').addEventListener('click', () => loadRequests(requestPage + 1));
  $('friends-more').addEventListener('click', () => loadFriends(friendPage + 1));

  async function loadSearch(page) {
    const version = ++searchVersion;
    if (searchQuery.length < 2) {
      $('search-results').replaceChildren(el('p', 'social-hint', 'Enter at least 2 characters to search by name.'));
      $('search-more').hidden = true; return;
    }
    $('search-more').disabled = true;
    if (page === 0) $('search-results').replaceChildren(el('p', 'social-state', 'Searching...'));
    try {
      const result = await api(`/people?q=${encodeURIComponent(searchQuery)}&page=${page}`);
      if (version !== searchVersion) return;
      if (page === 0) $('search-results').replaceChildren();
      result.items.forEach(person => {
        const { row, info } = personRow(person);
        if (person.state === 'NONE') friendAction(info, 'Add friend', () => api(`/requests/${person.id}`, { method: 'POST' }));
        else if (person.state === 'INCOMING') {
          friendAction(info, 'Accept request', () => api(`/requests/${person.requestId}/accept`, { method: 'POST' }));
          friendAction(info, 'Decline', () => api(`/requests/${person.requestId}/decline`, { method: 'POST' }), true);
        } else info.append(el('p', 'social-person-state', person.state === 'FRIENDS' ? 'Friends' : 'Request sent'));
        $('search-results').append(row);
      });
      if (page === 0 && !result.items.length) $('search-results').append(el('p', 'social-state', 'No people found. Try another name.'));
      searchPage = result.page; $('search-more').hidden = !result.hasNext;
    } catch (failure) { if (version === searchVersion) retry($('search-results'), failure, () => loadSearch(page), page > 0); }
    finally { if (version === searchVersion) $('search-more').disabled = false; }
  }
  function startSearch() { clearTimeout(searchTimer); searchQuery = $('friend-search').value.trim(); loadSearch(0); }
  $('search-form').addEventListener('submit', event => { event.preventDefault(); startSearch(); });
  $('friend-search').addEventListener('input', () => { ++searchVersion; clearTimeout(searchTimer); searchTimer = setTimeout(startSearch, 300); });
  $('search-more').addEventListener('click', () => loadSearch(searchPage + 1));
  function showFriends(show) {
    main.classList.toggle('show-friends', show);
    $('friends-tab').classList.toggle('is-active', show); $('feed-tab').classList.toggle('is-active', !show);
    $('friends-tab').setAttribute('aria-pressed', String(show)); $('feed-tab').setAttribute('aria-pressed', String(!show));
    if (show) { loadRequests(0); loadFriends(0); }
  }
  $('friends-tab').addEventListener('click', () => showFriends(true));
  $('feed-tab').addEventListener('click', () => { showFriends(false); loadFeed(0); });
  Promise.all([loadFeed(0), loadRequests(0), loadFriends(0)]);
})();
