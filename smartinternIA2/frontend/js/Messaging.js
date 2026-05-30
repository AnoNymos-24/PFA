/* ═══════════════════════════════════════════════════════════
   SmartIntern — Messagerie temps réel
   Fichier : js/messaging.js
   Dépendances CDN (à mettre avant ce fichier) :
     <script src="https://cdn.jsdelivr.net/npm/sockjs-client@1.6.1/dist/sockjs.min.js"></script>
     <script src="https://cdn.jsdelivr.net/npm/@stomp/stompjs@7.0.0/bundles/stomp.umd.min.js"></script>
   ═══════════════════════════════════════════════════════════ */

(function () {
  'use strict';

  const BASE_URL = 'http://localhost:8081/api/messaging';
  const WS_URL   = 'http://localhost:8081/ws';
  const COLORS   = ['#4f46e5','#0891b2','#059669','#d97706','#dc2626','#7c3aed','#db2777'];

  let stompClient     = null;
  let conversations   = [];
  let messages        = {};
  let activeConvId    = null;
  let connected       = false;
  let typingTimer     = null;
  let allUsers        = [];
  let panelOpen       = false;
  let subscriptions   = {};
  let selectedParts   = [];

  // ── Session ───────────────────────────────────────────────
  const getToken  = () => localStorage.getItem('smartintern_token') || localStorage.getItem('token') || '';
  const getUserId = () => parseInt(localStorage.getItem('smartintern_userId') || '0');

  const authHeader = () => ({
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ' + getToken()
  });

  // ── Utilitaires ───────────────────────────────────────────
  const gc = (id) => COLORS[(id || 0) % COLORS.length];
  const gi = (nom, prenom) =>
    (((prenom || '')[0] || '') + ((nom || '')[0] || '')).toUpperCase() || '?';

  const ava = (user, sz = 32) =>
    `<div style="width:${sz}px;height:${sz}px;border-radius:50%;background:${gc(user?.id)};
      display:flex;align-items:center;justify-content:center;color:#fff;
      font-size:${Math.round(sz*0.36)}px;font-weight:700;flex-shrink:0;user-select:none;">
      ${gi(user?.nom, user?.prenom)}</div>`;

  const fmtTime = (d) => {
    if (!d) return '';
    const dt = new Date(d), now = new Date(), diff = now - dt;
    if (diff < 60000)   return 'maintenant';
    if (diff < 3600000) return Math.floor(diff/60000) + 'min';
    if (dt.toDateString() === now.toDateString())
      return dt.toLocaleTimeString('fr-FR', {hour:'2-digit',minute:'2-digit'});
    return dt.toLocaleDateString('fr-FR', {day:'2-digit',month:'2-digit'});
  };

  const fmtSize = (b) => {
    if (!b) return '';
    if (b < 1024)    return b + ' B';
    if (b < 1048576) return (b/1024).toFixed(1) + ' KB';
    return (b/1048576).toFixed(1) + ' MB';
  };

  const escHtml = (s) => String(s || '')
    .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');

  // ── API REST ──────────────────────────────────────────────
  async function apiFetch(path, opts = {}) {
    const res = await fetch(BASE_URL + path, {
      ...opts,
      headers: { ...authHeader(), ...(opts.headers || {}) }
    });
    if (!res.ok) throw new Error('Erreur ' + res.status);
    const ct = res.headers.get('content-type') || '';
    return ct.includes('json') ? res.json() : res.text();
  }

  const apiGetConvs    = ()      => apiFetch('/conversations');
  const apiGetMsgs     = (id,p)  => apiFetch(`/conversations/${id}/messages?page=${p}&size=50`);
  const apiSendRest    = (id,c)  => apiFetch(`/conversations/${id}/messages`, {
    method:'POST', body: JSON.stringify({ conversationId:id, contenu:c })
  });
  const apiMarkRead    = (id)    => apiFetch(`/conversations/${id}/lus`, {method:'PUT'});
  const apiJoin        = (id)    => apiFetch(`/conversations/${id}/rejoindre`, {method:'POST'});
  const apiExport      = (id)    => apiFetch(`/conversations/${id}/export`);
  // Création de conversation UNIQUEMENT via WebSocket
// Création de conversation via API REST Admin
const apiCreate = async (data) => {
    const res = await fetch('http://localhost:8081/api/messaging/conversations', {
        method: 'POST',
        headers: authHeader(),
        body: JSON.stringify(data)
    });
    if (!res.ok) {
        const errorText = await res.text();
        throw new Error(`HTTP ${res.status}: ${errorText}`);
    }
    return res.json();
};

  async function apiSendFile(convId, file) {
    const fd = new FormData();
    fd.append('fichier', file);
    const res = await fetch(`${BASE_URL}/conversations/${convId}/fichiers`, {
      method:'POST',
      headers: { 'Authorization': 'Bearer ' + getToken() },
      body: fd
    });
    if (!res.ok) throw new Error('Erreur upload ' + res.status);
    return res.json();
  }

 async function apiGetUsers() {
    try {
        const res = await fetch('http://localhost:8081/api/messaging/users', {
            headers: authHeader()
        });
        if (!res.ok) {
            console.warn('[Messaging] apiGetUsers HTTP', res.status);
            return [];
        }
        return res.json();
    } catch(e) {
        console.warn('[Messaging] apiGetUsers:', e);
        return [];
    }
}

  // ── WebSocket (StompJs v7) ────────────────────────────────
  function connectWS() {
    if (!window.SockJS || !window.StompJs) {
      console.warn('[Messaging] SockJS/StompJs non disponibles — mode REST uniquement');
      return;
    }

    const client = new window.StompJs.Client({
      webSocketFactory: () => new window.SockJS(WS_URL),
      connectHeaders: { Authorization: 'Bearer ' + getToken() },
      reconnectDelay: 4000,
      debug: () => {},

      onConnect: () => {
        stompClient = client;
        connected   = true;
        updateConnDot();

        client.subscribe('/user/queue/notifications', (msg) => {
          const m = JSON.parse(msg.body);
          conversations = conversations.map(c =>
            c.id === m.conversationId
              ? { ...c, dernierMessage: m, nonLus: (c.nonLus||0) + 1 }
              : c
          );
          renderConvList();
          updateFabBadge();
        });

        client.subscribe('/user/queue/conversations', (msg) => {
          const conv = JSON.parse(msg.body);
          const idx  = conversations.findIndex(c => c.id === conv.id);
          if (idx >= 0) conversations[idx] = conv;
          else conversations.unshift(conv);
          renderConvList();
        });

        // Re-souscrire aux convs déjà abonnées
        const oldSubs = Object.keys(subscriptions);
        subscriptions = {};
        oldSubs.forEach(id => subscribeToConv(parseInt(id)));
      },

      onDisconnect: () => { connected = false; updateConnDot(); },
      onStompError: () => { connected = false; updateConnDot(); }
    });

    stompClient = client;
    client.activate();
  }

  function subscribeToConv(convId) {
    if (!stompClient?.connected || subscriptions[convId]) return;

    const s1 = stompClient.subscribe(`/topic/conversations/${convId}`, (msg) => {
      const m = JSON.parse(msg.body);
      if (!messages[convId]) messages[convId] = [];
      messages[convId].push(m);
      if (activeConvId === convId) {
        appendMessage(m);
        apiMarkRead(convId).catch(()=>{});
      } else {
        conversations = conversations.map(c =>
          c.id === convId ? { ...c, nonLus: (c.nonLus||0)+1 } : c
        );
        renderConvList();
        updateFabBadge();
      }
    });

    const s2 = stompClient.subscribe(`/topic/conversations/${convId}/typing`, (msg) => {
      const d = JSON.parse(msg.body);
      if (d.userId !== getUserId()) showTyping(convId, d.typing ? d.nom : null);
    });

    const s3 = stompClient.subscribe(`/topic/conversations/${convId}/lus`, () => {
      conversations = conversations.map(c =>
        c.id === convId ? { ...c, nonLus: 0 } : c
      );
      renderConvList();
      updateFabBadge();
    });

    subscriptions[convId] = [s1, s2, s3];
  }

  function wsPublish(destination, body) {
    if (stompClient?.connected) {
      stompClient.publish({ destination, body: JSON.stringify(body) });
      return true;
    }
    return false;
  }

  // ── Envoi message ─────────────────────────────────────────
async function sendMessage() {
    const inp  = document.getElementById('si-msg-input');
    const text = inp?.value.trim();
    
    console.log('[send] text:', text, 'activeConvId:', activeConvId);
    
    if (!text) { console.warn('[send] texte vide'); return; }
    if (!activeConvId) { console.warn('[send] pas de conversation active'); return; }
    
    inp.value = '';
    const btn = document.getElementById('si-send-btn');
    if (btn) btn.disabled = true;

    // Toujours essayer REST en premier (plus fiable)
 try {
    const msg = await apiSendRest(activeConvId, text);
    // Afficher localement seulement si WebSocket non connecté
    if (!connected) {
        if (!messages[activeConvId]) messages[activeConvId] = [];
        messages[activeConvId].push(msg);
        appendMessage(msg);
    }
} catch(e) {
    console.error('[Messaging] send REST échoué:', e);
}
    
    if (btn) btn.disabled = false;
}

  async function sendFile(file) {
    if (!file || !activeConvId) return;
    try {
      const msg = await apiSendFile(activeConvId, file);
      if (!messages[activeConvId]) messages[activeConvId] = [];
      messages[activeConvId].push(msg);
      appendMessage(msg);
      scrollBottom();
    } catch(e) { alert('Erreur envoi fichier : ' + e.message); }
  }

  function sendTypingSignal(isTyping) {
    if (!activeConvId) return;
    clearTimeout(typingTimer);
    wsPublish(`/app/conversations/${activeConvId}/typing`, {
      action: isTyping ? 'TYPING_START' : 'TYPING_STOP'
    });
    if (isTyping)
      typingTimer = setTimeout(() => sendTypingSignal(false), 2000);
  }

  // ── Chargement données ────────────────────────────────────
 async function loadConversations() {
    try {
        conversations = await apiGetConvs();
    } catch(e) {
        console.warn('[Messaging] loadConversations REST:', e);
        conversations = [];
    }
    renderConvList();
    updateFabBadge();
}

  async function selectConv(convId) {
    activeConvId = convId;
    conversations = conversations.map(c => c.id === convId ? { ...c, nonLus: 0 } : c);
    renderConvList();
    updateFabBadge();

    // Afficher zone chat
    document.getElementById('si-chat-placeholder').style.display = 'none';
    const chatActive = document.getElementById('si-chat-active');
    chatActive.style.display    = 'flex';
    chatActive.style.flex       = '1';
    chatActive.style.flexDirection = 'column';
    chatActive.style.minHeight  = '0';

    renderChatHeader();

    if (!messages[convId]) {
      document.getElementById('si-msgs').innerHTML = '<div class="si-loading">Chargement…</div>';
      try {
        const data = await apiGetMsgs(convId, 0);
        messages[convId] = (data.content || []).reverse();
      } catch(e) { messages[convId] = []; }
    }

    subscribeToConv(convId);
    renderMessages();
    scrollBottom();
    apiMarkRead(convId).catch(()=>{});
  }

  async function joinConv(convId) {
    try {
      const conv = await apiJoin(convId);
      const idx  = conversations.findIndex(c => c.id === conv.id);
      if (idx >= 0) conversations[idx] = conv;
      else conversations.unshift(conv);
      renderConvList();
      closeModal('si-modal-join');
      await selectConv(conv.id);
    } catch(e) { alert('Erreur rejoindre : ' + e.message); }
  }

async function createConv(nom, type, participantIds) {
    if (!nom) { alert('Veuillez entrer un nom'); return; }
    try {
        const conv = await apiCreate({ nom, type, participantIds });
if (!connected) {
    conversations.unshift(conv);
    renderConvList();
}
closeModal('si-modal-create');
await selectConv(conv.id);
    } catch(e) {
        console.error('[Messaging] Erreur création:', e);
        alert('Erreur création : ' + e.message);
    }
}

  async function exportHistorique() {
    if (!activeConvId) return;
    try {
      const conv = conversations.find(c => c.id === activeConvId);
      const txt  = await apiExport(activeConvId);
      const blob = new Blob([txt], { type: 'text/plain;charset=utf-8' });
      const a    = document.createElement('a');
      a.href     = URL.createObjectURL(blob);
      a.download = `historique-${conv?.nom || activeConvId}.txt`;
      a.click();
      URL.revokeObjectURL(a.href);
    } catch(e) { alert('Erreur export : ' + e.message); }
  }

  // ── Rendu ─────────────────────────────────────────────────
  function renderConvList() {
    const search = (document.getElementById('si-search-input')?.value || '').toLowerCase();
    const list   = conversations.filter(c => (c.nom||'').toLowerCase().includes(search));
    const el     = document.getElementById('si-conv-list');
    if (!el) return;

    if (!list.length) {
      el.innerHTML = `<div class="si-empty-convs">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
        <p>Aucune conversation</p>
        <button class="si-btn-primary" onclick="SIMessaging.openCreateModal()">+ Créer</button>
      </div>`;
      return;
    }

    el.innerHTML = list.map(c => {
      const isGrp  = c.type !== 'PRIVE';
      const other  = !isGrp && c.participants?.find(p => p.id !== getUserId());
      const user   = other || { id: c.id, nom: c.nom, prenom: '' };
      const avaEl  = isGrp
        ? `<div class="si-ci-ava group">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
            </svg>
           </div>`
        : `<div class="si-ci-ava" style="background:${gc(user.id)}">${gi(user.nom, user.prenom)}</div>`;

      const preview = c.dernierMessage
        ? (c.dernierMessage.type === 'FICHIER' || c.dernierMessage.type === 'IMAGE'
            ? '📎 ' + (c.dernierMessage.fichierNom || 'Fichier')
            : escHtml(c.dernierMessage.contenu || ''))
        : 'Démarrer la conversation…';

      return `<div class="si-conv-item ${c.id===activeConvId?'active':''} ${c.nonLus>0?'unread':''}"
        onclick="SIMessaging.selectConv(${c.id})">
        ${avaEl}
        <div class="si-ci-info">
          <div class="si-ci-top">
            <span class="si-ci-name">${escHtml(c.nom||'')}</span>
            <span class="si-ci-time">${fmtTime(c.dernierMessage?.sentAt)}</span>
          </div>
          <div style="display:flex;justify-content:space-between;align-items:center;">
            <span class="si-ci-preview">${preview}</span>
            ${c.nonLus > 0 ? `<span class="si-unread-badge">${c.nonLus>99?'99+':c.nonLus}</span>` : ''}
          </div>
        </div>
      </div>`;
    }).join('');
  }

  function renderChatHeader() {
    const conv = conversations.find(c => c.id === activeConvId);
    const head = document.getElementById('si-chat-head-content');
    if (!head || !conv) return;

    const isGrp = conv.type !== 'PRIVE';
    const other = !isGrp && conv.participants?.find(p => p.id !== getUserId());
    const user  = other || { id: conv.id, nom: conv.nom, prenom: '' };
    const avaEl = isGrp
      ? `<div style="width:36px;height:36px;border-radius:50%;background:linear-gradient(135deg,#4f46e5,#7c3aed);
           display:flex;align-items:center;justify-content:center;flex-shrink:0;">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/>
            <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
          </svg>
         </div>`
      : ava(user, 36);

    head.innerHTML = `
      <div class="si-chat-head-left">
        ${avaEl}
        <div>
          <div class="si-chat-conv-name">${escHtml(conv.nom)}</div>
          <div class="si-chat-conv-meta">
            ${(conv.participants?.length||0)} participant(s)
            <span id="si-typing-ind" class="si-typing-ind" style="display:none;"></span>
          </div>
        </div>
      </div>
      <div class="si-chat-head-right">
        <button class="si-icon-btn" title="Exporter" onclick="SIMessaging.exportHistorique()">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="17 8 12 3 7 8"/>
            <line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
        </button>
      </div>`;
  }

  function renderMessages() {
    const el   = document.getElementById('si-msgs');
    if (!el) return;
    const list = messages[activeConvId] || [];
    if (!list.length) {
      el.innerHTML = '<div class="si-loading">Aucun message. Commencez la conversation !</div>';
      return;
    }
    let prev = null;
    el.innerHTML = list.map(m => { const h = bubbleHTML(m, prev); prev = m; return h; }).join('');
  }

  function appendMessage(m) {
    const el   = document.getElementById('si-msgs');
    if (!el) return;
    const prev = (messages[activeConvId] || []).slice(-2, -1)[0];
    // Enlever typing dots
    el.querySelector('.si-typing-dots')?.remove();
    const div = document.createElement('div');
    div.innerHTML = bubbleHTML(m, prev);
    el.appendChild(div.firstElementChild || div);
    scrollBottom();
  }

  function bubbleHTML(m, prev) {
    if (m.type === 'SYSTEME')
      return `<div class="si-msg-system">${escHtml(m.contenu||'')}</div>`;

    const isMine  = m.expediteur?.id === getUserId();
    const showAva = !isMine && (!prev || prev.expediteur?.id !== m.expediteur?.id || prev.type === 'SYSTEME');
    const avaEl   = showAva ? ava(m.expediteur, 26) : '<div style="width:26px"></div>';
    const sender  = !isMine && showAva
      ? `<div class="si-bubble-sender">${escHtml((m.expediteur?.prenom||'')+' '+(m.expediteur?.nom||''))}</div>`
      : '';

    let content = '';
    if (m.type === 'IMAGE' && m.fichierPath) {
      content = `<a href="${m.fichierPath}" target="_blank" rel="noopener">
        <img src="${m.fichierPath}" alt="${escHtml(m.fichierNom||'')}"
          style="max-width:210px;max-height:170px;border-radius:7px;display:block;"/></a>`;
    } else if (m.type === 'FICHIER' && m.fichierPath) {
      content = `<a href="${m.fichierPath}" download="${escHtml(m.fichierNom||'')}" class="si-file-msg">
        <span class="si-file-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/>
            <polyline points="13 2 13 9 20 9"/>
          </svg>
        </span>
        <div>
          <div class="si-file-name">${escHtml(m.fichierNom||'')}</div>
          <div class="si-file-size">${fmtSize(m.fichierTaille)}</div>
        </div>
        <span class="si-file-dl">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="17 8 12 3 7 8"/>
            <line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
        </span></a>`;
    } else {
      content = `<div class="si-bubble-text">${escHtml(m.contenu||'')}</div>`;
    }

    return `<div class="si-msg-row ${isMine?'mine':'theirs'}">
      ${!isMine ? avaEl : ''}
      <div class="si-bubble ${isMine?'si-bubble-mine':'si-bubble-theirs'}">
        ${sender}${content}
        <div class="si-bubble-meta">
          <span class="si-bubble-time">${fmtTime(m.sentAt)}</span>
          ${isMine ? `<span class="si-bubble-lu">${m.lu?'✓✓':'✓'}</span>` : ''}
        </div>
      </div>
      ${isMine ? avaEl : ''}
    </div>`;
  }

  function showTyping(convId, nom) {
    if (convId !== activeConvId) return;
    const el   = document.getElementById('si-msgs');
    const ind  = document.getElementById('si-typing-ind');
    const dots = el?.querySelector('.si-typing-dots');
    if (nom) {
      if (ind) { ind.textContent = `· ${nom} écrit…`; ind.style.display = 'inline'; }
      if (!dots && el) {
        const d = document.createElement('div');
        d.className = 'si-typing-dots';
        d.innerHTML = '<div class="si-td"></div><div class="si-td"></div><div class="si-td"></div>';
        el.appendChild(d);
        scrollBottom();
      }
    } else {
      if (ind) ind.style.display = 'none';
      dots?.remove();
    }
  }

  function scrollBottom() {
    const el = document.getElementById('si-msgs');
    if (el) el.scrollTop = el.scrollHeight;
  }

  function updateConnDot() {
    document.querySelectorAll('.si-conn-dot').forEach(d => {
      d.className = 'si-conn-dot' + (connected ? ' online' : '');
    });
    const badge = document.getElementById('si-conn-badge');
    if (badge) {
      badge.className   = 'si-conn-badge ' + (connected ? 'online' : 'offline');
      badge.textContent = connected ? 'En ligne' : 'Hors ligne';
    }
  }

  function updateFabBadge() {
    const total = conversations.reduce((a,c) => a + (c.nonLus||0), 0);
    const badge = document.getElementById('si-fab-badge');
    if (badge) {
      badge.textContent  = total > 99 ? '99+' : total;
      badge.style.display = (total > 0 && !panelOpen) ? 'block' : 'none';
    }
  }

  // ── Modals ────────────────────────────────────────────────
  function openCreateModal() {
    selectedParts = [];
    if (document.getElementById('si-create-nom'))  document.getElementById('si-create-nom').value  = '';
    if (document.getElementById('si-create-type')) document.getElementById('si-create-type').value = 'GROUPE';
    if (document.getElementById('si-part-search')) document.getElementById('si-part-search').value = '';
    renderPartList('');
    document.getElementById('si-modal-create')?.classList.add('open');
  }

  function openJoinModal() {
    if (document.getElementById('si-join-id')) document.getElementById('si-join-id').value = '';
    document.getElementById('si-modal-join')?.classList.add('open');
  }

  function closeModal(id) {
    document.getElementById(id)?.classList.remove('open');
  }

  function renderPartList(search) {
    const el  = document.getElementById('si-part-list');
    if (!el) return;
    const me  = getUserId();
    const filtered = allUsers.filter(u =>
      u.id !== me &&
      `${u.prenom||''} ${u.nom||''}`.toLowerCase().includes((search||'').toLowerCase())
    );
    if (!filtered.length) {
      el.innerHTML = '<div style="padding:10px;text-align:center;font-size:12px;color:#475569;">Aucun utilisateur</div>';
      return;
    }
    el.innerHTML = filtered.map(u => `
      <div class="si-part-item ${selectedParts.includes(u.id)?'selected':''}"
        onclick="SIMessaging.togglePart(${u.id})">
        ${ava(u, 24)}
        <span style="font-size:12px;">${escHtml((u.prenom||'')+' '+(u.nom||''))}</span>
        <span class="si-part-role">${(u.role||'').replace('ROLE_','')}</span>
        ${selectedParts.includes(u.id) ? '<span class="si-part-check">✓</span>' : ''}
      </div>`).join('');
  }

  function togglePart(id) {
    selectedParts = selectedParts.includes(id)
      ? selectedParts.filter(x => x !== id)
      : [...selectedParts, id];
    renderPartList(document.getElementById('si-part-search')?.value || '');
  }

  function confirmCreate() {
    const nom  = document.getElementById('si-create-nom')?.value.trim();
    const type = document.getElementById('si-create-type')?.value || 'GROUPE';
    if (!nom) { alert('Veuillez entrer un nom.'); return; }
    createConv(nom, type, selectedParts);
  }

  function confirmJoin() {
    const id = parseInt(document.getElementById('si-join-id')?.value || '0');
    if (!id) return;
    joinConv(id);
  }

  // ── Panel toggle ──────────────────────────────────────────
  function togglePanel() {
    panelOpen = !panelOpen;
    document.getElementById('si-panel')?.classList.toggle('open', panelOpen);
    document.getElementById('si-chat-fab')?.classList.toggle('open', panelOpen);
    updateFabBadge();
  }

  // ── Injection HTML ────────────────────────────────────────
  function injectHTML() {
    if (document.getElementById('si-messaging-root')) return;
    const root = document.createElement('div');
    root.id    = 'si-messaging-root';
    root.innerHTML = `
<button class="si-chat-fab" id="si-chat-fab" onclick="SIMessaging.togglePanel()" title="Messagerie">
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
    <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
  </svg>
  <span class="si-fab-badge" id="si-fab-badge" style="display:none;">0</span>
  <span class="si-conn-dot" id="si-conn-dot"></span>
</button>

<div class="si-panel" id="si-panel">
  <div class="si-sidebar">
    <div class="si-sidebar-head">
      <div class="si-sidebar-title">
        <span>
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
          Messagerie
          <span class="si-conn-badge offline" id="si-conn-badge">Hors ligne</span>
        </span>
        <div class="si-head-actions">
          <button class="si-icon-btn" title="Rejoindre" onclick="SIMessaging.openJoinModal()">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="8.5" cy="7" r="4"/>
              <line x1="20" y1="8" x2="20" y2="14"/>
              <line x1="23" y1="11" x2="17" y2="11"/>
            </svg>
          </button>
          <button class="si-icon-btn primary" title="Créer" onclick="SIMessaging.openCreateModal()">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"/>
              <line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
          </button>
        </div>
      </div>
      <div class="si-search">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
        </svg>
        <input id="si-search-input" placeholder="Rechercher…"
          oninput="SIMessaging.filterConvs(this.value)"/>
      </div>
    </div>
    <div class="si-conv-list" id="si-conv-list">
      <div class="si-loading">Chargement…</div>
    </div>
  </div>

  <div class="si-chat" id="si-chat">
    <div class="si-chat-placeholder" id="si-chat-placeholder">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
        <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
      </svg>
      <h3>Sélectionnez une conversation</h3>
      <p>ou créez-en une nouvelle</p>
      <button class="si-btn-primary" onclick="SIMessaging.openCreateModal()">+ Nouvelle conversation</button>
    </div>
    <div id="si-chat-active" style="display:none;flex-direction:column;flex:1;min-height:0;">
      <div class="si-chat-head" id="si-chat-head-content"></div>
      <div class="si-msgs" id="si-msgs"></div>
      <div class="si-input-area">
        <input type="file" id="si-file-input" style="display:none"
          accept="image/*,.pdf,.doc,.docx,.xls,.xlsx,.txt,.zip"
          onchange="SIMessaging.onFileChange(this)"/>
        <button class="si-icon-btn" title="Joindre" onclick="document.getElementById('si-file-input').click()">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"/>
          </svg>
        </button>
        <textarea id="si-msg-input" class="si-msg-input" rows="1"
          placeholder="Écrire un message… (Entrée pour envoyer)"
          onkeydown="SIMessaging.onKeyDown(event)"
          oninput="SIMessaging.onInput(event)"></textarea>
        <button class="si-send-btn" id="si-send-btn" disabled onclick="SIMessaging.sendMessage()">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="22" y1="2" x2="11" y2="13"/>
            <polygon points="22 2 15 22 11 13 2 9 22 2"/>
          </svg>
        </button>
      </div>
    </div>
  </div>
</div>

<div class="si-modal-overlay" id="si-modal-create" onclick="if(event.target===this)SIMessaging.closeModal('si-modal-create')">
  <div class="si-modal">
    <div class="si-modal-head">
      <h3>Nouvelle conversation</h3>
      <button class="si-icon-btn" onclick="SIMessaging.closeModal('si-modal-create')">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
        </svg>
      </button>
    </div>
    <div class="si-modal-body">
      <input class="si-input-field" id="si-create-nom" placeholder="Nom de la conversation *"/>
      <select class="si-select-field" id="si-create-type">
        <option value="GROUPE">Groupe</option>
        <option value="PRIVE">Privé (1:1)</option>
        <option value="STAGE">Stage</option>
      </select>
      <div class="si-search" style="margin:0;">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
        </svg>
        <input id="si-part-search" placeholder="Rechercher des participants…"
          oninput="SIMessaging.renderPartList(this.value)"/>
      </div>
      <div class="si-part-list" id="si-part-list">
        <div class="si-loading">Chargement des utilisateurs…</div>
      </div>
    </div>
    <div class="si-modal-foot">
      <button class="si-btn-ghost" onclick="SIMessaging.closeModal('si-modal-create')">Annuler</button>
      <button class="si-btn-primary" onclick="SIMessaging.confirmCreate()">Créer</button>
    </div>
  </div>
</div>

<div class="si-modal-overlay" id="si-modal-join" onclick="if(event.target===this)SIMessaging.closeModal('si-modal-join')">
  <div class="si-modal si-modal-sm">
    <div class="si-modal-head">
      <h3>Rejoindre une conversation</h3>
      <button class="si-icon-btn" onclick="SIMessaging.closeModal('si-modal-join')">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
        </svg>
      </button>
    </div>
    <div class="si-modal-body">
      <p style="font-size:12px;color:#475569;margin:0;">Entrez l'ID de la conversation à rejoindre.</p>
      <input class="si-input-field" id="si-join-id" type="number" placeholder="ID de la conversation"/>
    </div>
    <div class="si-modal-foot">
      <button class="si-btn-ghost" onclick="SIMessaging.closeModal('si-modal-join')">Annuler</button>
      <button class="si-btn-primary" onclick="SIMessaging.confirmJoin()">Rejoindre</button>
    </div>
  </div>
</div>`;
    document.body.appendChild(root);
  }

  // ── Démarrage ─────────────────────────────────────────────
 async function init() {
    if (!getToken()) return;
    injectHTML();

    // ── Bind manuel des événements après injection du HTML ──
    const textarea = document.getElementById('si-msg-input');
    const sendBtn  = document.getElementById('si-send-btn');

    if (textarea) {
        textarea.addEventListener('input', (e) => {
            if (sendBtn) sendBtn.disabled = !e.target.value.trim();
            sendTypingSignal(e.target.value.length > 0);
        });
        textarea.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessage();
            }
        });
    }

    connectWS();
    await loadConversations();
    allUsers = await apiGetUsers();
}

  // ── Export global ─────────────────────────────────────────
  window.SIMessaging = {
    togglePanel, selectConv,
    filterConvs: () => renderConvList(),
    openCreateModal, openJoinModal, confirmCreate, confirmJoin, closeModal,
    togglePart, renderPartList,
    sendMessage, exportHistorique,
    onKeyDown: (e) => { if (e.key==='Enter'&&!e.shiftKey){e.preventDefault();sendMessage();} },
    onInput:   (e) => {
      const btn = document.getElementById('si-send-btn');
      if (btn) btn.disabled = !e.target.value.trim();
      sendTypingSignal(e.target.value.length > 0);
    },
    onFileChange: (inp) => { const f=inp.files[0]; if(f){sendFile(f);inp.value='';} }
    
  };

  if (document.readyState === 'loading')
    document.addEventListener('DOMContentLoaded', init);
  else
    init();

})();