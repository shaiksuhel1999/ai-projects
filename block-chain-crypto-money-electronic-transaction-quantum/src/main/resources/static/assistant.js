// ===== TekmaChain AI guide — client for the embedded RAG assistant =====
const AA = {open: false, greeted: false};

function toggleAssistant() {
    AA.open = !AA.open;
    $("assistantPanel").classList.toggle("open", AA.open);
    $("assistantFab").classList.toggle("hidden", AA.open);
    if (AA.open && !AA.greeted) { AA.greeted = true; greetAssistant(); }
    if (AA.open) setTimeout(() => $("apInput").focus(), 60);
}

async function greetAssistant() {
    let ex = [];
    try { ex = await api("/api/assistant/examples"); } catch (e) { /* ignore */ }
    botBubble("👋 Hi! I'm your TekmaChain guide. Tell me what you're trying to do — or tap a question:", {suggestions: ex});
}

function apEl() { return $("apMsgs"); }
function apScroll() { const el = apEl(); el.scrollTop = el.scrollHeight; }
function mdLite(s) { return escapeHtml(s).replace(/\*\*(.+?)\*\*/g, "<b>$1</b>").replace(/\n/g, "<br>"); }

function userBubble(text) {
    apEl().insertAdjacentHTML("beforeend", `<div class="msg user">${escapeHtml(text)}</div>`);
    apScroll();
}
function botBubble(text, opts = {}) {
    let extra = "";
    if (opts.sources && opts.sources.length) extra += `<div class="msg-src">📚 ${opts.sources.map(escapeHtml).join(" · ")}</div>`;
    if (opts.action) extra += `<button class="ap-action" onclick="assistantAction('${opts.action}')">${escapeHtml(opts.actionLabel || ("Open " + opts.action))} →</button>`;
    const chips = (opts.suggestions || []).map(s => `<button class="ap-chip" data-q="${escapeHtml(s)}" onclick="askAssistant(this.dataset.q)">${escapeHtml(s)}</button>`).join("");
    apEl().insertAdjacentHTML("beforeend", `<div class="msg bot">${mdLite(text)}${extra}</div>${chips ? `<div class="ap-chips">${chips}</div>` : ""}`);
    apScroll();
}
function typingBubble() {
    apEl().insertAdjacentHTML("beforeend", `<div class="msg bot typing" id="apTyping"><span></span><span></span><span></span></div>`);
    apScroll();
}

function assistantAction(view) { showView(view); toggleAssistant(); }

async function askAssistant(text) {
    if (!text || !text.trim()) return;
    userBubble(text);
    $("apInput").value = "";
    typingBubble();
    try {
        const a = await api("/api/assistant", {method: "POST", body: JSON.stringify({question: text})});
        const t = $("apTyping"); if (t) t.remove();
        botBubble(a.answer, {sources: a.sources, action: a.action, actionLabel: a.actionLabel, suggestions: a.suggestions});
    } catch (e) {
        const t = $("apTyping"); if (t) t.remove();
        botBubble("Sorry — I couldn't reach the assistant right now.");
    }
}
function sendAssistant() { askAssistant($("apInput").value.trim()); }
