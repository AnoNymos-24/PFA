/**
 * SmartIntern AI — Module Messagerie temps réel (U-06, U-07, U-08)
 * =================================================================
 * Dépendances CDN (à inclure dans le dashboard avant ce script) :
 *   <script src="https://cdnjs.cloudflare.com/ajax/libs/sockjs-client/1.6.1/sockjs.min.js"></script>
 *   <script src="https://cdnjs.cloudflare.com/ajax/libs/stomp.js/2.3.3/stomp.min.js"></script>
 *
 * Initialisation :
 *   document.addEventListener('DOMContentLoaded', () => initMessagerie());
 */

'use strict';

// ════════════════════════════════════════════════════════════════════════════
// CONFIGURATION
// ════════════════════════════════════════════════════════════════════════════

const MESSAGERIE_API = '/api/messagerie';
const WS_RECONNECT_DELAY_MS = 5000;

// ════════════════════════════════════════════════════════════════════════════
// SECTION 1 — CLIENT WEBSOCKET STOMP
// ════════════════════════════════════════════════════════════════════════════

class MessagerieClient {
    constructor(token) {
        this.token     = token;
        this.client    = null;
        this.abonnements = {};   // { conversationId: Subscription }
        this.connecte  = false;
        this._onConnectedCb = null;
    }

    /**
     * Ouvre la connexion WebSocket SockJS + STOMP.
     * Reconnexion automatique toutes les 5 s en cas de coupure.
     * @param {Function} onConnected — appelée une fois la connexion établie
     */
    connecter(onConnected) {
        this._onConnectedCb = onConnected;
        const socket = new SockJS(`/ws?token=${this.token}`);
        this.client  = Stomp.over(socket);
        this.client.debug = null; // Désactiver les logs STOMP en console

        this.client.connect(
            { Authorization: `Bearer ${this.token}` },
            (frame) => {
                this.connecte = true;
                console.info('[Messagerie] WebSocket connecté');
                if (typeof onConnected === 'function') onConnected(frame);
            },
            (error) => {
                this.connecte = false;
                console.warn('[Messagerie] WebSocket déconnecté — reconnexion dans 5 s...', error);
                setTimeout(() => this.connecter(this._onConnectedCb), WS_RECONNECT_DELAY_MS);
            }
        );
    }

    /**
     * S'abonne au topic d'une conversation.
     * @param {number} conversationId
     * @param {Function} onMessage — reçoit un MessageDto (objet JS)
     */
    abonnerConversation(conversationId, onMessage) {
        if (this.abonnements[conversationId]) return; // déjà abonné
        const dest = `/topic/conversation.${conversationId}`;
        this.abonnements[conversationId] = this.client.subscribe(dest, (msg) => {
            try {
                onMessage(JSON.parse(msg.body));
            } catch (e) {
                console.error('[Messagerie] Erreur parsing message WS :', e);
            }
        });
        // Rejoindre la conversation côté serveur (vérification d'accès)
        this.client.send('/app/conversation.rejoindre', {},
            JSON.stringify({ conversationId }));
    }

    /**
     * Se désabonne d'une conversation.
     */
    desabonnerConversation(conversationId) {
        const sub = this.abonnements[conversationId];
        if (sub) {
            sub.unsubscribe();
            delete this.abonnements[conversationId];
        }
    }

    /**
     * Envoie un message via WebSocket STOMP.
     * @param {number} conversationId
     * @param {string} contenu
     */
    envoyerMessage(conversationId, contenu) {
        if (!this.connecte) {
            console.error('[Messagerie] WebSocket non connecté — envoi ignoré');
            return false;
        }
        this.client.send('/app/message.envoyer', {},
            JSON.stringify({ conversationId, contenu }));
        return true;
    }

    /**
     * Ferme proprement la connexion WebSocket.
     */
    deconnecter() {
        Object.keys(this.abonnements).forEach(id => this.desabonnerConversation(id));
        if (this.client) this.client.disconnect();
        this.connecte = false;
    }
}

// ════════════════════════════════════════════════════════════════════════════
// SECTION 2 — FONCTIONS API REST
// ════════════════════════════════════════════════════════════════════════════

/**
 * @returns {string} JWT depuis localStorage
 */
function _getToken() {
    return localStorage.getItem('token') || '';
}

/**
 * GET authentifié
 */
async function apiGet(url) {
    const res = await fetch(url, {
        headers: { Authorization: `Bearer ${_getToken()}` }
    });
    if (!res.ok) throw new Error(`HTTP ${res.status} — GET ${url}`);
    return res.json();
}

/**
 * POST authentifié avec corps JSON
 */
async function apiPost(url, body) {
    const res = await fetch(url, {
        method:  'POST',
        headers: {
            'Content-Type': 'application/json',
            Authorization:  `Bearer ${_getToken()}`
        },
        body: JSON.stringify(body)
    });
    if (!res.ok) throw new Error(`HTTP ${res.status} — POST ${url}`);
    return res.json();
}

/**
 * PUT authentifié (sans corps)
 */
async function apiPut(url) {
    const res = await fetch(url, {
        method:  'PUT',
        headers: { Authorization: `Bearer ${_getToken()}` }
    });
    if (!res.ok) throw new Error(`HTTP ${res.status} — PUT ${url}`);
    return res.json();
}

// ── Fonctions API métier ───────────────────────────────────────────────────

async function chargerConversations() {
    return apiGet(`${MESSAGERIE_API}/conversations`);
}

async function chargerMessages(conversationId, page = 0) {
    return apiGet(`${MESSAGERIE_API}/conversations/${conversationId}/messages?page=${page}&size=25`);
}

async function marquerLu(conversationId) {
    return apiPut(`${MESSAGERIE_API}/conversations/${conversationId}/lire`);
}

async function creerConversationPrivee(destinataireId, stageId) {
    return apiPost(`${MESSAGERIE_API}/conversations/privee`, {
        type: 'PRIVEE', destinataireId, stageId
    });
}

async function creerReunion(entrepriseId, sujet) {
    return apiPost(`${MESSAGERIE_API}/conversations/reunion`, {
        type: 'REUNION', entrepriseId, sujet
    });
}

// ════════════════════════════════════════════════════════════════════════════
// SECTION 3 — ÉTAT GLOBAL UI
// ════════════════════════════════════════════════════════════════════════════

let conversationActiveId = null;
let messagerieClient     = null;
let currentUserId        = null;

// ════════════════════════════════════════════════════════════════════════════
// SECTION 4 — FONCTIONS UI
// ════════════════════════════════════════════════════════════════════════════

/**
 * Point d'entrée principal — appeler après que le JWT soit chargé.
 */
function initMessagerie() {
    const userData  = JSON.parse(localStorage.getItem('user') || '{}');
    currentUserId   = userData.id || null;
    const token     = _getToken();

    if (!token) {
        console.warn('[Messagerie] Pas de token JWT — messagerie non initialisée');
        return;
    }

    messagerieClient = new MessagerieClient(token);
    messagerieClient.connecter(() => {
        renderConversationsList().catch(err =>
            console.error('[Messagerie] Erreur chargement conversations :', err)
        );
    });
}

/**
 * Affiche la liste des conversations dans #conv-items.
 */
async function renderConversationsList() {
    const conversations = await chargerConversations();
    const container = document.getElementById('conv-items');
    if (!container) return;

    container.innerHTML = '';
    if (conversations.length === 0) {
        container.innerHTML = '<p class="conv-empty">Aucune conversation</p>';
        return;
    }

    conversations.forEach(conv => {
        const el = document.createElement('div');
        el.className = 'conv-item' + (conv.nbNonLus > 0 ? ' unread' : '');
        el.dataset.id = conv.id;

        const titre  = conv.type === 'REUNION' ? (conv.sujet || 'Réunion') : getNomConversation(conv);
        const apercu = conv.dernierMessage
            ? (conv.dernierMessage.contenu || '').substring(0, 45) + '...'
            : 'Aucun message';
        const badge = conv.nbNonLus > 0
            ? `<span class="badge" aria-label="${conv.nbNonLus} messages non lus">${conv.nbNonLus}</span>`
            : '';

        el.innerHTML = `
            <div class="conv-title">${escapeHtml(titre)} ${badge}</div>
            <div class="conv-preview">${escapeHtml(apercu)}</div>
            ${conv.statut !== 'ACTIVE' ? '<span class="conv-readonly-tag">🔒</span>' : ''}
        `;
        el.addEventListener('click', () => ouvrirConversation(conv.id, conv.statut));
        container.appendChild(el);
    });
}

/**
 * Retourne le nom d'une conversation PRIVEE (le nom de l'autre participant).
 */
function getNomConversation(conv) {
    if (!conv.participants) return 'Conversation';
    const autre = conv.participants.find(p => p.utilisateurId !== currentUserId);
    return autre ? `${autre.prenom} ${autre.nom}` : 'Conversation';
}

/**
 * Ouvre une conversation : charge l'historique, marque les messages lus, s'abonne au topic WS.
 */
async function ouvrirConversation(conversationId, statut) {
    // Désabonner de la conversation précédente
    if (conversationActiveId && conversationActiveId !== conversationId) {
        messagerieClient.desabonnerConversation(conversationActiveId);
    }
    conversationActiveId = conversationId;

    // Charger l'historique (page 0, 25 messages)
    const data = await chargerMessages(conversationId);
    const messages = data.content || [];

    // Marquer comme lus
    await marquerLu(conversationId).catch(() => {});

    // Rendre les messages
    const liste = document.getElementById('messages-list');
    if (liste) {
        liste.innerHTML = '';
        messages.slice().reverse().forEach(msg => ajouterMessageDansUI(msg));
        liste.scrollTop = liste.scrollHeight;
    }

    // Afficher/masquer la zone de saisie selon le statut
    const inputArea      = document.getElementById('message-input-area');
    const readonlyBanner = document.getElementById('readonly-banner');
    if (inputArea)      inputArea.style.display = (statut === 'ACTIVE') ? 'flex' : 'none';
    if (readonlyBanner) readonlyBanner.style.display = (statut !== 'ACTIVE') ? 'block' : 'none';

    // S'abonner au topic WebSocket de la conversation
    messagerieClient.abonnerConversation(conversationId, (messageDto) => {
        ajouterMessageDansUI(messageDto);
        if (liste) liste.scrollTop = liste.scrollHeight;
        marquerLu(conversationId).catch(() => {});
        renderConversationsList().catch(() => {}); // Rafraîchir les badges
    });

    // Rafraîchir la liste (retirer le badge non-lus)
    renderConversationsList().catch(() => {});
}

/**
 * Ajoute une bulle de message dans #messages-list.
 */
function ajouterMessageDansUI(messageDto) {
    const liste = document.getElementById('messages-list');
    if (!liste) return;

    const isMine   = messageDto.expediteur && messageDto.expediteur.id === currentUserId;
    const isSysteme = messageDto.type === 'SYSTEME';

    const el = document.createElement('div');

    if (isSysteme) {
        el.className   = 'msg-systeme';
        el.textContent = messageDto.contenu;
    } else {
        el.className = `msg-bubble ${isMine ? 'msg-sent' : 'msg-received'}`;
        el.innerHTML = `
            ${!isMine && messageDto.expediteur
                ? `<span class="msg-author">${escapeHtml(messageDto.expediteur.prenom)}</span>`
                : ''}
            <div class="msg-text">${escapeHtml(messageDto.contenu)}</div>
            <span class="msg-time">${formatHeure(messageDto.envoieLe)}</span>
        `;
    }
    liste.appendChild(el);
}

/**
 * Envoie le message saisi dans #msg-input.
 * Appelé depuis le bouton "Envoyer" ou la touche Entrée.
 */
function envoyerMessageUI() {
    const input = document.getElementById('msg-input');
    if (!input) return;

    const contenu = input.value.trim();
    if (!contenu || !conversationActiveId) return;

    if (contenu.length > 2000) {
        alert('Message trop long (max 2000 caractères)');
        return;
    }

    const envoye = messagerieClient.envoyerMessage(conversationActiveId, contenu);
    if (envoye) {
        input.value = '';
        const counter = document.getElementById('char-counter');
        if (counter) counter.textContent = '0/2000';
    }
}

/**
 * Ouvre la modal de création de conversation (comportement à adapter selon le rôle).
 * À surcharger dans chaque dashboard.
 */
function ouvrirModalNouvelleConv() {
    console.info('[Messagerie] ouvrirModalNouvelleConv() — à implémenter dans le dashboard');
}

// ════════════════════════════════════════════════════════════════════════════
// SECTION 5 — UTILITAIRES
// ════════════════════════════════════════════════════════════════════════════

/**
 * Échappe le HTML pour prévenir les injections XSS.
 */
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.appendChild(document.createTextNode(String(text)));
    return div.innerHTML;
}

/**
 * Formate un timestamp ISO en heure locale HH:MM.
 */
function formatHeure(isoString) {
    if (!isoString) return '';
    try {
        return new Date(isoString).toLocaleTimeString('fr-FR', {
            hour: '2-digit', minute: '2-digit'
        });
    } catch {
        return '';
    }
}

// ════════════════════════════════════════════════════════════════════════════
// EXPORTS (compatibilité CommonJS / ES modules optionnelle)
// ════════════════════════════════════════════════════════════════════════════

if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        MessagerieClient,
        initMessagerie,
        chargerConversations,
        chargerMessages,
        creerConversationPrivee,
        creerReunion,
        marquerLu,
        ouvrirConversation,
        envoyerMessageUI,
        ajouterMessageDansUI,
        renderConversationsList,
        escapeHtml,
        formatHeure
    };
}
