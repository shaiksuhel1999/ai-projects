// ===== TekmaChain UI — core (nav, dashboard, wallet, explorer, network) =====

// One-time migration of pre-rename browser storage (nova.* -> tekma.*), so a
// returning user keeps their self-custody wallets and preferences after the rename.
(function migrateLegacyStorage() {
    try {
        ["identity", "displayCur", "selfWallets", "pair"].forEach(k => {
            const oldKey = "nova." + k, newKey = "tekma." + k;
            if (localStorage.getItem(newKey) === null && localStorage.getItem(oldKey) !== null) {
                localStorage.setItem(newKey, localStorage.getItem(oldKey));
            }
        });
    } catch (e) { /* storage unavailable — ignore */ }
})();

const $ = (id) => document.getElementById(id);

const state = {
    wallets: [],
    hotAddress: null,
    identity: localStorage.getItem("tekma.identity") || null,
    view: "dashboard",
    info: null,
    pairs: ["TEK/USD", "TEK/EUR", "TEK/GBP", "TEK/JPY", "TEK/INR"],
    currencies: [],
    curMap: {},
    tekUsd: 1,
    displayCur: localStorage.getItem("tekma.displayCur") || "USD",
};

const QSYM = {};            // pair -> quote code (filled from /api/exchange/pairs)
const SYMBOL = {TEK: ""};  // asset code -> symbol (filled from /api/currencies)
function quoteOf(pair) { return pair.slice(pair.indexOf("/") + 1); }
function curSym(code) { return SYMBOL[code] != null ? SYMBOL[code] : (code + " "); }
function rateOf(code) { const c = state.curMap[code]; return c ? Number(c.usdRate) : 1; }
function tekToDisplay(tek) { return Number(tek || 0) * state.tekUsd * rateOf(state.displayCur); }
function fmtDisplay(tek) {
    const code = state.displayCur;
    return curSym(code) + Number(tekToDisplay(tek)).toLocaleString(undefined, {maximumFractionDigits: 2}) + " " + code;
}

// ---- helpers ----
async function api(path, opts) {
    const res = await fetch(path, {headers: {"Content-Type": "application/json"}, ...opts});
    const body = await res.json().catch(() => ({}));
    if (res.status === 401 && typeof onUnauthorized === "function") onUnauthorized();
    if (!res.ok) throw new Error(body.error || body.message || ("HTTP " + res.status));
    return body;
}

function fmt(n, max = 8) {
    if (n === null || n === undefined || n === "") return "–";
    return Number(n).toLocaleString(undefined, {maximumFractionDigits: max});
}
function money(n, asset) {
    const dp = asset === "TEK" ? 4 : 2;
    return (SYMBOL[asset] || "") + Number(n || 0).toLocaleString(undefined, {minimumFractionDigits: dp, maximumFractionDigits: dp});
}
function price(n) { return n === null || n === undefined ? "–" : Number(n).toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2}); }
function short(s, n = 6) { return s ? s.slice(0, n) + "…" + s.slice(-4) : ""; }
function escapeHtml(s) { return (s || "").replace(/[&<>"']/g, c => ({"&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"}[c])); }
function copy(t) { navigator.clipboard.writeText(t).then(() => toast("Copied " + short(t, 8))); }
function toast(msg, kind = "ok") {
    const t = $("toast");
    t.textContent = msg;
    t.className = "toast show " + kind;
    clearTimeout(toast._t);
    toast._t = setTimeout(() => (t.className = "toast"), 3400);
}

// ---- live-market animation ----
function animateCount(el, to, decimals = 2, prefix = "") {
    if (!el) return;
    const cur = parseFloat(String(el.dataset.v ?? el.textContent).replace(/[^0-9.\-]/g, ""));
    const from = isFinite(cur) ? cur : Number(to);
    to = Number(to); el.dataset.v = to;
    const fmtv = (v) => prefix + v.toLocaleString(undefined, {minimumFractionDigits: decimals, maximumFractionDigits: decimals});
    if (from === to) { el.textContent = fmtv(to); return; }
    const dur = 520, t0 = performance.now();
    (function step(t) {
        const p = Math.min(1, (t - t0) / dur), e = 0.5 - Math.cos(p * Math.PI) / 2;
        el.textContent = fmtv(from + (to - from) * e);
        if (p < 1) requestAnimationFrame(step);
    })(t0);
}
function flashEl(el, up) {
    if (!el) return;
    el.classList.remove("flash-up", "flash-down"); void el.offsetWidth;
    el.classList.add(up ? "flash-up" : "flash-down");
}

// ---- navigation ----
const VIEW_TITLES = {dashboard: "Dashboard", wallet: "Wallet", profile: "My profile", exchange: "Exchange", explorer: "Block explorer", network: "Peer network", security: "Security lab"};
function showView(name) {
    state.view = name;
    document.querySelectorAll(".tab").forEach(t => t.classList.toggle("active", t.dataset.view === name));
    document.querySelectorAll(".view").forEach(v => v.classList.toggle("active", v.id === "view-" + name));
    const pt = $("pageTitle");
    if (pt) pt.textContent = VIEW_TITLES[name] || name;
    window.scrollTo({top: 0});
    refreshView();
}
document.querySelectorAll(".tab").forEach(t => t.addEventListener("click", () => showView(t.dataset.view)));

function refreshView() {
    if (state.view === "dashboard") loadDashboard();
    else if (state.view === "wallet") { loadWallets().then(loadWalletHistory); }
    else if (state.view === "profile") loadProfile();
    else if (state.view === "exchange") loadExchange();
    else if (state.view === "explorer") loadBlocks();
    else if (state.view === "network") loadPeers();
    else if (state.view === "security") { /* on-demand via button */ }
}

// ---- identity ----
function fillIdentity() {
    const sel = $("identity");
    const opts = state.wallets.map(w => `<option value="${w.address}">${escapeHtml(w.label)} · ${short(w.address, 6)}</option>`).join("");
    sel.innerHTML = opts || `<option value="">— no wallets —</option>`;
    if (!state.identity || !state.wallets.some(w => w.address === state.identity)) {
        const def = state.wallets.find(w => w.address !== state.hotAddress) || state.wallets[0];
        state.identity = def ? def.address : null;
    }
    if (state.identity) sel.value = state.identity;
}
function onIdentityChange() {
    state.identity = $("identity").value;
    localStorage.setItem("tekma.identity", state.identity);
    if (state.view === "exchange") loadExchange();
    if (state.view === "wallet") loadWalletHistory();
}
function identityLabel() {
    const w = state.wallets.find(w => w.address === state.identity);
    return w ? w.label : "—";
}

// ---- chain info / KPIs ----
async function loadInfo() {
    const i = await api("/api/chain/info");
    state.info = i;
    $("heightChip").textContent = "#" + i.height;
    return i;
}

// ---- currencies / display currency ----
async function loadCurrencies() {
    try {
        const [pairs, curs, nu] = await Promise.all([
            api("/api/exchange/pairs").catch(() => state.pairs),
            api("/api/currencies").catch(() => []),
            api("/api/currencies/tek-usd").catch(() => ({tekUsd: 1})),
        ]);
        if (pairs && pairs.length) state.pairs = pairs;
        state.pairs.forEach(p => QSYM[p] = quoteOf(p));
        state.currencies = curs || [];
        state.currencies.forEach(c => { state.curMap[c.code] = c; SYMBOL[c.code] = c.symbol || c.code; });
        state.tekUsd = Number(nu.tekUsd) || 1;
        fillDisplayCur();
    } catch (e) { /* ignore */ }
}
function fillDisplayCur() {
    const sel = $("displayCur");
    if (!sel || !state.currencies.length) return;
    sel.innerHTML = state.currencies.map(c => `<option value="${c.code}">${c.code} ${c.symbol || ""}</option>`).join("");
    if (!state.curMap[state.displayCur]) state.displayCur = "USD";
    sel.value = state.displayCur;
}
function onDisplayCurChange() {
    state.displayCur = $("displayCur").value;
    localStorage.setItem("tekma.displayCur", state.displayCur);
    loadWallets();
    refreshView();
}

async function loadDashboard() {
    const [i, net, blocks] = await Promise.all([
        loadInfo(), api("/api/peers").catch(() => ({peers: []})), api("/api/blocks").catch(() => []),
    ]);
    updateSideNet(net);
    $("kpis").innerHTML = [
        ["Height", "#" + i.height, false],
        ["Circulating", fmt(i.circulatingSupply, 2) + " TEK", true],
        ["Supply value", fmtDisplay(i.circulatingSupply), true],
        ["1 TEK", curSym(state.displayCur) + fmt(tekToDisplay(1), 2) + " " + state.displayCur, false],
        ["Difficulty", i.difficulty, false],
        ["Block reward", fmt(i.blockReward, 2), false],
        ["Mempool", i.mempoolSize, false],
        ["Peers", (net.peers || []).length, false],
    ].map(([k, v, a]) => `<div class="kpi"><div class="k">${k}</div><div class="v ${a ? "accent" : ""}">${v}</div></div>`).join("");

    // monetary-policy strip (Bitcoin-style halving + supply cap + genesis message)
    const pct = i.maxSupply ? (Number(i.circulatingSupply) / Number(i.maxSupply) * 100) : 0;
    $("econStrip").innerHTML = `
        <div class="econ-item"><span class="k">Block reward</span><b>${fmt(i.blockReward, 2)} TEK</b>
            <span class="sub">next halving in ${Number(i.blocksUntilHalving || 0).toLocaleString()} blocks</span></div>
        <div class="econ-item grow"><span class="k">Supply</span><b>${fmt(i.circulatingSupply, 2)} / ${fmt(i.maxSupply, 0)} TEK</b>
            <div class="supply-bar"><div class="supply-fill" style="width:${Math.max(0.5, pct).toFixed(4)}%"></div></div>
            <span class="sub">${pct.toFixed(6)}% of max supply minted</span></div>
        <div class="econ-item genesis"><span class="k">Genesis message</span>
            <span class="gmsg">“${escapeHtml(i.genesisMessage || "")}”</span></div>`;

    // fetch tickers + trades per pair once
    const tickers = await Promise.all(state.pairs.map(p => api("/api/exchange/ticker?pair=" + encodeURIComponent(p)).catch(() => null)));
    const tradesByPair = {};
    await Promise.all(state.pairs.map(async p => { tradesByPair[p] = await api("/api/exchange/trades?pair=" + encodeURIComponent(p)).catch(() => []); }));

    // TEK/USD price chart
    const usdSeries = (tradesByPair["TEK/USD"] || []).slice().reverse().map(t => Number(t.price));
    $("dashPriceChart").innerHTML = Charts.line(usdSeries, {emptyMsg: "No TEK/USD trades yet — buy some on the Exchange"});
    $("dashPriceLast").textContent = usdSeries.length ? "$" + price(usdSeries[usdSeries.length - 1]) : "—";

    // volume by market + mint per block
    $("dashVolChart").innerHTML = Charts.bars(state.pairs.map((p, idx) => ({label: quoteOf(p), value: tickers[idx] ? Number(tickers[idx].baseVolume) : 0})), {emptyMsg: "No volume yet"});
    $("dashMintChart").innerHTML = Charts.bars(blocks.slice(-12).map(b => ({
        label: "#" + b.height, color: "var(--gold)",
        value: b.transactions.filter(t => t.coinbase).reduce((s, t) => s + t.outputs.reduce((a, o) => a + Number(o.amount), 0), 0),
    })), {emptyMsg: "Mine a block"});

    // market cards with sparklines
    $("marketCards").innerHTML = tickers.map((t, idx) => {
        const p = state.pairs[idx], q = quoteOf(p);
        const ch = t && t.changePct != null ? Number(t.changePct) : 0;
        const last = t && t.lastPrice != null ? curSym(q) + price(t.lastPrice) : "no trades";
        const spark = Charts.sparkline((tradesByPair[p] || []).slice().reverse().map(x => Number(x.price)));
        return `<div class="mcard" onclick="gotoPair('${p}')">
            <div class="pair">${p}</div>
            <div class="last">${last}</div>
            <div class="sub"><span class="${ch >= 0 ? "up" : "down"}">${ch >= 0 ? "▲" : "▼"} ${Math.abs(ch).toFixed(2)}%</span>
                <span>Vol ${t ? fmt(t.baseVolume, 2) : "0"}</span></div>
            ${spark}
        </div>`;
    }).join("");

    $("dashBlocks").innerHTML = blocks.slice(-6).reverse().map(b =>
        `<div class="mini-row"><span>#${b.height} · ${b.transactions.length} tx</span>
         <span class="mono">${short(b.hash, 10)}</span></div>`).join("") || '<p class="empty">No blocks.</p>';

    const allTrades = state.pairs.flatMap(p => (tradesByPair[p] || []).map(t => ({...t, pair: p})))
        .sort((a, b) => b.createdAt - a.createdAt).slice(0, 6);
    $("dashTrades").innerHTML = allTrades.map(t =>
        `<div class="mini-row"><span class="${t.takerSide === "BUY" ? "up" : "down"}">${t.takerSide} ${fmt(t.amount, 4)} TEK</span>
         <span class="mono">${curSym(quoteOf(t.pair))}${price(t.price)}</span></div>`).join("") || '<p class="empty">No trades yet.</p>';

    renderConverter();
    loadLiveMarkets();
}

// ---- live crypto markets (real Coinbase prices, display-only) ----
const COIN_COLOR = {BTC: "#f7931a", ETH: "#627eea", BNB: "#f0b90b", SOL: "#14f195", XRP: "#4a5560",
    ADA: "#0033ad", DOGE: "#c2a633", AVAX: "#e84142", DOT: "#e6007a", LINK: "#2a5ada", MATIC: "#8247e5", LTC: "#345d9d"};

async function loadLiveMarkets() {
    if (!$("liveMarkets")) return;
    const [coins, st] = await Promise.all([
        api("/api/market").catch(() => []),
        api("/api/market/status").catch(() => null),
    ]);
    const prev = state._mktPrev = state._mktPrev || {};
    const sym = curSym(state.displayCur), rate = rateOf(state.displayCur);
    const rows = coins.map(c => {
        const disp = Number(c.usdPrice) * rate;
        const ch = c.changePct != null ? Number(c.changePct) : 0;
        const col = COIN_COLOR[c.code] || "var(--primary)";
        const dec = disp < 1 ? 6 : 2;
        return `<div class="mkt-row" data-code="${c.code}">
            <div class="mkt-name"><span class="coin-badge" style="background:${col}">${c.code.slice(0, 3)}</span>
                <div><div class="cn">${escapeHtml(c.name)}</div><div class="cc">${c.code}</div></div></div>
            <div class="mkt-price" id="mkp-${c.code}">${sym}${disp.toLocaleString(undefined, {maximumFractionDigits: dec})}</div>
            <div class="mkt-ch ${ch >= 0 ? "up" : "down"}">${ch >= 0 ? "▲" : "▼"} ${Math.abs(ch).toFixed(2)}%</div>
        </div>`;
    }).join("");
    $("liveMarkets").innerHTML =
        `<div class="mkt-row mkt-head"><div>Asset</div><div>Price (${state.displayCur})</div><div>24h*</div></div>` +
        (rows || '<p class="empty">No market data.</p>');
    // flash the price cells that moved since the last render
    coins.forEach(c => {
        const now = Number(c.usdPrice);
        if (prev[c.code] != null && now !== prev[c.code]) flashEl($("mkp-" + c.code), now >= prev[c.code]);
        prev[c.code] = now;
    });
    if (st) {
        $("mktLive").style.display = st.live ? "" : "none";
        $("mktStatus").textContent = st.live ? "synced " + timeAgo(st.lastSyncAt)
            : (st.enabled ? "connecting…" : "reference prices");
    }
}

async function refreshMarkets() {
    try {
        await api("/api/market/refresh", {method: "POST"});
        toast("Market data refreshed");
    } catch (e) {
        toast("Market feed offline — showing last prices", "warn");
    }
    await loadLiveMarkets();
}

// ---- profile ----
async function loadProfile() {
    if (!state.user) { openAuth(); return; }
    let p;
    try { p = await api("/api/profile"); }
    catch (e) { toast("Could not load profile: " + e.message, "err"); return; }

    const sym = curSym(state.displayCur), rate = rateOf(state.displayCur);
    const acc = p.account;
    const name = acc.displayName || acc.username;
    const joined = new Date(acc.createdAt).toLocaleDateString(undefined, {year: "numeric", month: "long", day: "numeric"});
    $("profileHead").innerHTML = `
        <div class="phead">
            <span class="pavatar">${(name.trim()[0] || "?").toUpperCase()}</span>
            <div class="pmeta">
                <div class="pname">${escapeHtml(name)}</div>
                <div class="psub">@${escapeHtml(acc.username)} · member since ${joined}</div>
            </div>
        </div>`;

    const t = p.totals;
    const totalTek = Number(t.onChainTek) + Number(t.exchangeTek);
    const totalValue = totalTek * state.tekUsd * rate;
    $("profileKpis").innerHTML = [
        ["Portfolio value", sym + totalValue.toLocaleString(undefined, {maximumFractionDigits: 2}), true],
        ["TEK on-chain", fmt(t.onChainTek, 4) + " TEK", false],
        ["Spendable", fmt(t.spendableTek, 4) + " TEK", false],
        ["Maturing", fmt(t.immatureTek, 4) + " TEK", false],
        ["On exchange", fmt(t.exchangeTek, 4) + " TEK", false],
    ].map(([k, v, a]) => `<div class="kpi"><div class="k">${k}</div><div class="v ${a ? "accent" : ""}">${v}</div></div>`).join("");

    const rows = [];
    if (totalTek > 0) rows.push(holdingRow("TEK", "Tekma Coin", totalTek, totalTek * state.tekUsd * rate, sym));
    for (const h of p.exchange) {
        if (h.asset === "TEK") continue;                    // TEK already merged above
        const val = Number(h.total) * rate / rateOf(h.asset);
        rows.push(holdingRow(h.asset, h.asset + " · exchange", Number(h.total), val, sym));
    }
    $("profileHoldings").innerHTML = `<div class="mkt-row mkt-head"><div>Asset</div><div>Amount</div><div>Value</div></div>` +
        (rows.join("") || '<p class="empty">No holdings yet — mine some TEK on the Wallet tab.</p>');

    $("profileWallets").innerHTML = p.wallets.map(w =>
        `<div class="mini-row"><span>${escapeHtml(w.label)} ${w.hybrid ? "🛡" : ""}
            <span class="mono small" style="color:var(--muted-2)">${short(w.address, 10)}</span></span>
         <span class="mono">${fmt(w.tek, 4)} TEK${Number(w.immature) > 0 ? ` <span class="small" style="color:var(--gold)">+${fmt(w.immature, 2)}🔒</span>` : ""}</span></div>`
    ).join("") || '<p class="empty">No wallets yet — create one on the Wallet tab.</p>';

    renderAccounts(p.linkedAccounts);
}
function holdingRow(code, name, amount, value, sym) {
    const col = COIN_COLOR[code] || "var(--primary)";
    return `<div class="mkt-row">
        <div class="mkt-name"><span class="coin-badge" style="background:${col}">${code.slice(0, 3)}</span>
            <div><div class="cn">${escapeHtml(name)}</div><div class="cc">${code}</div></div></div>
        <div class="mkt-price">${fmt(amount, 4)}</div>
        <div class="mkt-price">${sym}${value.toLocaleString(undefined, {maximumFractionDigits: 2})}</div>
    </div>`;
}
function renderAccounts(list) {
    const card = a => `<div class="acct-card">
        <div><div class="ac-inst">${escapeHtml(a.institution)}</div>
            <div class="ac-num mono">${a.maskedNumber}${a.code ? " · " + escapeHtml(a.code) : ""}</div>
            <div class="ac-holder small">${escapeHtml(a.holderName)}</div></div>
        <button class="btn tiny" onclick="removeAccount(${a.id})" title="Remove account">✕</button></div>`;
    $("bankList").innerHTML = list.filter(a => a.kind === "BANK").map(card).join("")
        || '<p class="empty">No bank linked. Add one to deposit or withdraw fiat.</p>';
    $("dematList").innerHTML = list.filter(a => a.kind === "DEMAT").map(card).join("")
        || '<p class="empty">No demat account linked yet.</p>';
}
async function linkAccount(kind) {
    const f = kind === "BANK"
        ? {holder: "bankHolder", inst: "bankName", num: "bankNumber", code: "bankCode"}
        : {holder: "dematHolder", inst: "dematDepo", num: "dematNumber", code: "dematCode"};
    const body = {kind, holderName: $(f.holder).value.trim(), institution: $(f.inst).value.trim(),
        number: $(f.num).value.trim(), code: $(f.code).value.trim()};
    if (!body.holderName || !body.institution || !body.number) { toast("Fill in holder, institution and number", "warn"); return; }
    try {
        await api("/api/profile/accounts", {method: "POST", body: JSON.stringify(body)});
        [f.holder, f.inst, f.num, f.code].forEach(id => $(id).value = "");
        toast((kind === "BANK" ? "Bank" : "Demat") + " account linked");
        loadProfile();
    } catch (e) { toast(e.message, "err"); }
}
async function removeAccount(id) {
    try {
        await api("/api/profile/accounts/" + id, {method: "DELETE"});
        toast("Account removed");
        loadProfile();
    } catch (e) { toast(e.message, "err"); }
}

// ---- currency converter ----
function renderConverter() {
    const box = $("converter");
    if (!box) return;
    const nu = $("convTekUsd");
    if (nu) nu.textContent = "1 TEK ≈ $" + price(state.tekUsd);
    if (!box._built && state.currencies.length) {
        box.innerHTML = `
            <div class="conv-in"><input id="convAmt" type="number" value="1" step="any" oninput="renderConverter()"/>
                <span class="eq">TEK =</span>
                <select id="convTo" class="cur-select" onchange="renderConverter()"></select></div>
            <div class="conv-result" id="convResult">–</div>
            <div class="conv-grid" id="convGrid"></div>
            <div class="conv-fx" id="fxStatus"></div>`;
        $("convTo").innerHTML = state.currencies.map(c => `<option value="${c.code}">${c.code} — ${escapeHtml(c.name)}</option>`).join("");
        $("convTo").value = state.displayCur;
        box._built = true;
    }
    updateFxStatus();
    if (!box._built) { box.innerHTML = '<p class="empty">Loading currencies…</p>'; return; }
    const amt = parseFloat($("convAmt").value) || 0, to = $("convTo").value;
    $("convResult").innerHTML = curSym(to) + Number(amt * state.tekUsd * rateOf(to)).toLocaleString(undefined, {maximumFractionDigits: 4}) + ` <span class="cur">${to}</span>`;
    const majors = ["USD", "EUR", "GBP", "JPY", "CNY", "INR", "AED", "BRL", "ZAR"];
    $("convGrid").innerHTML = majors.filter(c => state.curMap[c]).map(c =>
        `<div class="conv-cell"><div class="cc">${c}</div><div class="cv">${curSym(c)}${Number(amt * state.tekUsd * rateOf(c)).toLocaleString(undefined, {maximumFractionDigits: 2})}</div></div>`).join("");
}

function timeAgo(ts) {
    if (!ts) return "never";
    const s = Math.floor((Date.now() - ts) / 1000);
    if (s < 60) return s + "s ago";
    if (s < 3600) return Math.floor(s / 60) + "m ago";
    if (s < 86400) return Math.floor(s / 3600) + "h ago";
    return Math.floor(s / 86400) + "d ago";
}
async function updateFxStatus() {
    const el = $("fxStatus");
    if (!el) return;
    try {
        const s = await api("/api/currencies/status");
        const dot = s.live ? '<span class="dot on"></span>' : '<span class="dot off"></span>';
        const label = s.live ? "live rates · updated " + timeAgo(s.lastSyncAt) : "seeded rates";
        el.innerHTML = `${dot} FX: ${label} <button class="btn tiny" style="margin-left:auto" onclick="refreshFx()">↻ refresh</button>`;
    } catch (e) { el.textContent = ""; }
}
async function refreshFx() {
    try {
        toast("Fetching live FX rates…");
        const r = await api("/api/currencies/refresh", {method: "POST"});
        await loadCurrencies();
        renderConverter();
        loadWallets();
        toast(r.updated > 0 ? ("Updated " + r.updated + " live rates") : "FX feed unavailable — using current rates", r.updated > 0 ? "ok" : "err");
    } catch (e) { toast(e.message, "err"); }
}

// ---- self-custody wallet storage (Phase 7: keys live only in the browser) ----
function getSelfWallets() { try { return JSON.parse(localStorage.getItem("tekma.selfWallets") || "[]"); } catch { return []; } }
function saveSelfWallets(list) { localStorage.setItem("tekma.selfWallets", JSON.stringify(list)); }

// ---- wallets ----
async function loadWallets() {
    const [server, hot] = await Promise.all([
        api("/api/wallets"),
        api("/api/exchange/hotwallet").catch(() => ({address: null})),
    ]);
    state.hotAddress = hot.address;

    const locals = getSelfWallets();
    const localsWithBal = await Promise.all(locals.map(async w => {
        let balance = 0;
        try { balance = (await api("/api/address/" + w.address + "/balance")).balance; } catch (e) { /* ignore */ }
        return {...w, balance, self: true};
    }));
    state.wallets = [...server.map(w => ({...w, self: false})), ...localsWithBal];
    fillIdentity();

    $("wallets").innerHTML = state.wallets.map(w => {
        const isHot = w.address === state.hotAddress;
        const tag = isHot ? '<span class="tag-hot">HOT</span>'
            : (w.hd ? '<span class="tag-self">🌱 HD</span>' : (w.self ? '<span class="tag-self">🔐 self-custody</span>'
                : (w.hybrid ? '<span class="tag-q">🛡 quantum-safe</span>' : "")));
        const deposit = w.self ? "" : `<button class="btn" onclick="gotoDeposit('${w.address}')">Deposit</button>`;
        return `<div class="wallet ${isHot ? "hot" : ""} ${w.self ? "selfw" : ""} ${w.hd ? "hdw" : ""}">
            <div class="label">${escapeHtml(w.label)}${tag}</div>
            <div class="addr" title="copy" onclick="copy('${w.address}')">${w.address}</div>
            <div class="bal">${fmt(w.balance, 4)} <small>TEK</small></div>
            <div class="small" style="color:var(--muted-2);margin-top:2px">≈ ${fmtDisplay(w.balance)}</div>
            ${Number(w.immature) > 0 ? `<div class="small" style="color:var(--gold);margin-top:2px">🔒 ${fmt(w.immature, 4)} TEK maturing</div>` : ""}
            <div class="acts">
                <button class="btn mine" onclick="quickMine('${w.address}')">⛏ Mine</button>
                <button class="btn" onclick="prefillSend('${w.address}')">Send</button>
                ${deposit}
            </div>
        </div>`;
    }).join("") || '<p class="empty">No wallets yet. Create one, then mine to fund it.</p>';

    fillSelect("sendFrom", state.wallets);
    fillSelect("sendTo", state.wallets);
    fillSelect("mineMiner", state.wallets);
}

async function createHdWallet() {
    try {
        const mnemonic = await TekmaHD.generateMnemonic();
        const accounts = await TekmaHD.deriveAccounts(mnemonic, 5);
        const base = ($("walletLabel").value || "HD").trim();
        const list = getSelfWallets();
        accounts.forEach(a => list.push({label: base + " #" + a.index, address: a.address, publicKey: a.publicKey, privateKey: a.privateKey, self: true, hd: true, path: a.path}));
        saveSelfWallets(list);
        $("walletLabel").value = "";
        showMnemonic(mnemonic, accounts);
        await loadWallets();
    } catch (e) { toast("HD keygen failed: " + e.message, "err"); }
}

function showMnemonic(mnemonic, accounts) {
    const words = mnemonic.split(" ").map((w, i) => `<span class="mword"><b>${i + 1}</b> ${escapeHtml(w)}</span>`).join("");
    const addrs = accounts.map(a => `<div class="hd-addr"><span class="mono small">${a.path}</span><span class="mono small" onclick="copy('${a.address}')" title="copy">${short(a.address, 10)}</span></div>`).join("");
    const el = document.createElement("div");
    el.className = "modal-overlay";
    el.innerHTML = `<div class="modal">
        <h3>🌱 Your recovery phrase (BIP-39)</h3>
        <p class="hint">These 12 words derive all your HD addresses. Anyone with them controls the funds — write them down, never share them. (Stored only in this browser.)</p>
        <div class="mnemonic">${words}</div>
        <div class="hd-addrs"><div class="panel-h" style="padding:8px 0">Derived addresses (m/44'/0'/0'/0/i)</div>${addrs}</div>
        <button class="btn primary" onclick="this.closest('.modal-overlay').remove()">I've saved it</button>
    </div>`;
    document.body.appendChild(el);
}

async function createHybridWallet() {
    try {
        const label = $("walletLabel").value;
        await api("/api/wallets/hybrid", {method: "POST", body: JSON.stringify({label})});
        $("walletLabel").value = "";
        toast("🛡 Quantum-safe wallet created — every spend is signed with ECDSA + ML-DSA");
        await loadWallets();
    } catch (e) { toast(e.message, "err"); }
}

async function createSelfWallet() {
    try {
        const label = ($("walletLabel").value || "").trim() || "self-wallet";
        const k = await TekmaCrypto.generateKey();
        const list = getSelfWallets();
        list.push({label, address: k.address, publicKey: k.publicKey, privateKey: k.privateKey});
        saveSelfWallets(list);
        $("walletLabel").value = "";
        toast("🔐 Self-custody wallet created in your browser — key never sent to the node");
        await loadWallets();
    } catch (e) { toast("Self-custody keygen failed: " + e.message, "err"); }
}

function fillSelect(id, list) {
    const sel = $(id);
    if (!sel) return;
    const prev = sel.value;
    sel.innerHTML = list.map(w => `<option value="${w.address}">${escapeHtml(w.label)} · ${short(w.address, 6)} (${fmt(w.balance, 2)})</option>`).join("");
    if ([...sel.options].some(o => o.value === prev)) sel.value = prev;
}

async function createWallet() {
    try {
        const label = $("walletLabel").value;
        const w = await api("/api/wallets", {method: "POST", body: JSON.stringify({label})});
        $("walletLabel").value = "";
        toast("Created wallet " + w.label);
        await loadWallets();
    } catch (e) { toast(e.message, "err"); }
}

async function sendTx() {
    try {
        const from = $("sendFrom").value, to = $("sendTo").value;
        const amount = $("sendAmount").value, fee = $("sendFee").value || "0";
        if (!from || !to) return toast("Pick both wallets", "err");
        if (from === to) return toast("Choose two different wallets", "err");
        const w = state.wallets.find(x => x.address === from);
        let tx;
        if (w && w.self) {
            tx = await selfCustodySend(w, to, amount, fee);   // signed in the browser
            toast("🔐 Signed in-browser → broadcast (" + short(tx.txId, 8) + ")");
        } else {
            tx = await api("/api/transactions", {method: "POST", body: JSON.stringify({from, to, amount, fee})});
            toast("Broadcast → mempool (" + short(tx.txId, 8) + ")");
        }
        $("sendAmount").value = "";
        await loadWallets();
    } catch (e) { toast(e.message, "err"); }
}

// ---- client-side transaction building & signing (Phase 7) ----
function toSats(str) {
    let [w, f = ""] = String(str).trim().split(".");
    f = (f + "00000000").slice(0, 8);
    return BigInt(w || "0") * 100000000n + BigInt(f || "0");
}
function satsToPlain(sats) {
    const w = sats / 100000000n, f = (sats % 100000000n).toString().padStart(8, "0");
    return w.toString() + "." + f;
}
// ---- canonical binary serialization (must match Java SigningData byte-for-byte) ----
// Layout, all integers big-endian:
//   u8  kind (0=coinbase, 1=tx)
//   i64 timestamp
//   u32 inputCount, then per input: u32 prevTxIdLen, prevTxId bytes, u32 outIndex
//   u32 outputCount, then per output: u32 addrLen, addr bytes, i64 amount-in-satoshis
class ByteWriter {
    constructor() { this.b = []; }
    u8(v) { this.b.push(v & 0xff); return this; }
    u32(v) { v = v >>> 0; this.b.push((v >>> 24) & 0xff, (v >>> 16) & 0xff, (v >>> 8) & 0xff, v & 0xff); return this; }
    i64(v) {  // v is a BigInt
        let x = BigInt.asUintN(64, BigInt(v));
        const out = new Array(8);
        for (let i = 7; i >= 0; i--) { out[i] = Number(x & 0xffn); x >>= 8n; }
        for (const y of out) this.b.push(y);
        return this;
    }
    bytes(u8arr) { for (const x of u8arr) this.b.push(x); return this; }
    lenPrefixed(str) { const e = new TextEncoder().encode(str); return this.u32(e.length).bytes(e); }
    build() { return Uint8Array.from(this.b); }
}
function buildSigningBytes(coinbase, timestamp, inputs, outputs) {
    const w = new ByteWriter();
    w.u8(coinbase ? 0 : 1).i64(BigInt(timestamp));
    w.u32(inputs.length);
    for (const i of inputs) w.lenPrefixed(i.prevTxId).u32(i.outIndex);
    w.u32(outputs.length);
    for (const o of outputs) w.lenPrefixed(o.address).i64(toSats(o.amount));
    return w.build();
}

async function selfCustodySend(wallet, to, amountStr, feeStr) {
    const amount = toSats(amountStr), fee = toSats(feeStr);
    if (amount <= 0n) throw new Error("Amount must be positive");
    const utxos = await api("/api/wallets/" + wallet.address + "/utxos");
    const need = amount + fee;
    const chosen = [];
    let gathered = 0n;
    for (const u of utxos) {
        chosen.push(u);
        gathered += toSats(u.amount);
        if (gathered >= need) break;
    }
    if (gathered < need) throw new Error("Insufficient balance: have " + satsToPlain(gathered) + ", need " + satsToPlain(need));

    const timestamp = Date.now();
    const inputs = chosen.map(u => ({prevTxId: u.txId, outIndex: u.outIndex}));
    const outputs = [{index: 0, address: to, amount: satsToPlain(amount)}];
    const change = gathered - need;
    if (change > 0n) outputs.push({index: 1, address: wallet.address, amount: satsToPlain(change)});

    const signingBytes = buildSigningBytes(false, timestamp, inputs, outputs);
    const signature = await TekmaCrypto.signBytes(signingBytes, wallet.privateKey);
    const txId = await TekmaCrypto.sha256HexBytes(signingBytes);

    const dto = {
        txId, coinbase: false, timestamp,
        inputs: inputs.map(i => ({prevTxId: i.prevTxId, outIndex: i.outIndex, publicKey: wallet.publicKey, signature})),
        outputs,
    };
    return api("/api/transactions/raw", {method: "POST", body: JSON.stringify(dto)});
}

async function mine() { await doMine($("mineMiner").value); }
async function quickMine(a) { await doMine(a); }
async function doMine(minerAddress) {
    if (!minerAddress) return toast("Create a wallet first", "err");
    try {
        if ($("mineResult")) $("mineResult").textContent = "⛏ mining… grinding the nonce…";
        const b = await api("/api/mine", {method: "POST", body: JSON.stringify({minerAddress})});
        if ($("mineResult")) $("mineResult").textContent = `✓ Block #${b.height} mined!\nhash ${b.hash}\nnonce ${b.nonce}`;
        toast("Mined block #" + b.height + " 🎉");
        await loadWallets();
        if (state.view === "explorer") loadBlocks();
        if (state.view === "dashboard") loadDashboard();
    } catch (e) { if ($("mineResult")) $("mineResult").textContent = ""; toast(e.message, "err"); }
}

function prefillSend(a) { showView("wallet"); setTimeout(() => { $("sendFrom").value = a; $("sendAmount").focus(); }, 50); }

async function loadWalletHistory() {
    if (!state.identity) { $("walletHistory").innerHTML = '<p class="empty">Select a wallet.</p>'; return; }
    $("histAddr").textContent = state.identity;
    const blocks = await api("/api/blocks");
    const rows = [];
    blocks.slice().reverse().forEach(b => {
        b.transactions.forEach(tx => {
            tx.outputs.forEach(o => {
                if (o.address === state.identity) {
                    rows.push(`<div class="mini-row"><span class="up">+ ${fmt(o.amount, 4)} TEK ${tx.coinbase ? "· ⛏ mined" : "· received"}</span>
                        <span class="mono">blk #${b.height} · ${short(tx.txId, 8)}</span></div>`);
                }
            });
        });
    });
    $("walletHistory").innerHTML = rows.slice(0, 40).join("") || '<p class="empty">No activity yet — mine a block to this wallet.</p>';
}

// ---- explorer ----
async function loadBlocks() {
    const blocks = await api("/api/blocks");
    blocks.reverse();
    $("blocks").innerHTML = blocks.map(renderBlock).join("");
}
function renderTx(tx) {
    const outs = tx.outputs.map(o => `<div class="flow">→ <b>${fmt(o.amount, 4)}</b> TEK to ${short(o.address, 10)}</div>`).join("");
    const ins = tx.coinbase ? '<div class="flow" style="color:var(--gold)">⛏ coinbase (newly minted)</div>' : `<div class="flow">spends ${tx.inputs.length} input(s)</div>`;
    return `<div class="tx ${tx.coinbase ? "coinbase" : ""}"><div class="txid">${tx.txId}</div>${ins}${outs}</div>`;
}
function renderBlock(b) {
    const genesis = b.height === 0;
    const txs = b.transactions.map(renderTx).join("") || '<p class="empty">No transactions.</p>';
    return `<div class="block" id="blk${b.height}">
        <div class="block-head" onclick="document.getElementById('blk${b.height}').classList.toggle('open')">
            <div class="block-h"><span class="block-num ${genesis ? "genesis" : ""}">#${b.height}</span><span class="block-hash">${b.hash}</span></div>
            <span class="block-meta">${b.transactions.length} tx · nonce ${b.nonce} · ${new Date(b.timestamp).toLocaleTimeString()}</span>
        </div>
        <div class="block-body">
            <div class="kv"><b>prev</b> ${b.previousHash}</div>
            <div class="kv"><b>merkleRoot</b> ${b.merkleRoot}</div>
            <div class="kv"><b>difficulty</b> ${b.difficulty} · <b>nonce</b> ${b.nonce} · <b>miner</b> ${short(b.minerAddress, 10)}</div>
            <div>${txs}</div>
        </div></div>`;
}
async function validateChain() {
    try {
        const r = await api("/api/chain/validate");
        $("validation").innerHTML = r.valid
            ? `<p class="ok">✓ Chain valid — ${r.length} block(s). Every hash, link, Merkle root, Proof-of-Work, signature and balance checks out.</p>`
            : `<p class="bad">✗ Chain INVALID</p><ul>${r.problems.map(p => `<li>${escapeHtml(p)}</li>`).join("")}</ul>`;
    } catch (e) { toast(e.message, "err"); }
}

// ---- network ----
function updateSideNet(net) {
    const el = $("sideNet"), dot = $("sideDot");
    if (!el) return;
    const online = (net.peers || []).filter(p => p.online).length;
    if (!net.peers || net.peers.length === 0) { el.textContent = "standalone"; if (dot) dot.className = "dot on"; }
    else { el.textContent = online + "/" + net.peers.length + " peers"; if (dot) dot.className = "dot " + (online ? "on" : "off"); }
}
async function loadPeers() {
    const net = await api("/api/peers");
    $("nodeIdBadge").textContent = "node " + net.nodeId;
    $("selfUrl").textContent = net.selfUrl;
    updateSideNet(net);
    $("peers").innerHTML = net.peers.length ? net.peers.map(p =>
        `<div class="peer"><span class="purl">${escapeHtml(p.url)}</span>
         <span class="pstate"><span class="dot ${p.online ? "on" : "off"}"></span>${p.online ? "h" + p.height : "offline"}</span></div>`).join("")
        : '<p class="empty">No peers. This node is running solo.</p>';
}
async function addPeer() {
    try {
        const url = $("peerUrl").value.trim();
        if (!url) return toast("Enter a peer URL", "err");
        await api("/api/peers", {method: "POST", body: JSON.stringify({url})});
        $("peerUrl").value = "";
        toast("Peer added — discovering & syncing…");
        await loadPeers();
    } catch (e) { toast(e.message, "err"); }
}
async function syncNow() {
    try {
        const r = await api("/api/sync", {method: "POST"});
        toast(r.switched ? ("Adopted longer chain → height " + r.height) : "Already up to date");
        loadPeers(); loadInfo();
    } catch (e) { toast(e.message, "err"); }
}

// ---- security lab (Phase 9) ----
function loadSecurity() { /* rendered on demand */ }

async function runPqBenchmark() {
    const box = $("pqResult");
    box.innerHTML = '<p class="empty">Benchmarking ECDSA vs post-quantum schemes on this node…</p>';
    try {
        const r = await api("/api/pq/benchmark");
        const maxSig = Math.max(...r.rows.map(x => x.signatureBytes));
        const rows = r.rows.map(x => {
            const w = Math.max(2, (x.signatureBytes / maxSig) * 100);
            return `<tr>
                <td><b>${x.algorithm}</b></td>
                <td>${x.quantumResistant ? '<span class="qbadge safe">✓ quantum-safe</span>' : '<span class="qbadge risk">✗ vulnerable</span>'}</td>
                <td class="mono a-right">${x.publicKeyBytes.toLocaleString()}</td>
                <td class="mono a-right">${x.signatureBytes.toLocaleString()}</td>
                <td class="mono a-right">${x.signMs.toFixed(3)}</td>
                <td class="mono a-right">${x.verifyMs.toFixed(3)}</td>
                <td><div class="sigbar"><div class="sigbar-fill ${x.quantumResistant ? "pq" : "cl"}" style="width:${w}%"></div></div></td>
            </tr>`;
        }).join("");
        box.innerHTML = `
            <div style="overflow-x:auto">
            <table class="pq-table">
                <thead><tr><th>Algorithm</th><th>Quantum</th><th class="a-right">Pub key (B)</th>
                    <th class="a-right">Signature (B)</th><th class="a-right">Sign (ms)</th><th class="a-right">Verify (ms)</th>
                    <th>Signature size</th></tr></thead>
                <tbody>${rows}</tbody>
            </table></div>
            <p class="hint">${escapeHtml(r.note)}</p>`;
    } catch (e) { box.innerHTML = `<p class="empty">Benchmark failed: ${escapeHtml(e.message)}</p>`; }
}

// ---- auth ----
state.user = null;
state.authMode = "login";

async function loadAuth() {
    try {
        const me = await api("/api/auth/me");
        setUser(me.authenticated ? me : null);
    } catch (e) { setUser(null); }
}
function setUser(me) {
    state.user = me;
    const authed = !!me;
    $("authOverlay").hidden = authed;
    $("tbUser").hidden = !authed;
    $("tbGuest").hidden = authed;
    $("navProfile").hidden = !authed;
    if (authed) {
        const name = me.displayName || me.username;
        $("uname").textContent = name;
        $("uavatar").textContent = (name.trim()[0] || "?").toUpperCase();
    } else if (state.view === "profile") {
        showView("dashboard");   // don't strand a signed-out user on the profile page
    }
}
function openAuth() {
    $("authErr").textContent = "";
    $("authOverlay").hidden = false;
}
function setAuthMode(mode) {
    state.authMode = mode;
    const reg = mode === "register";
    $("tabLogin").classList.toggle("active", !reg);
    $("tabRegister").classList.toggle("active", reg);
    $("authNameRow").hidden = !reg;
    $("authSubmit").textContent = reg ? "Create account" : "Sign in";
    $("authPass").setAttribute("autocomplete", reg ? "new-password" : "current-password");
    $("authErr").textContent = "";
}
async function submitAuth() {
    const username = $("authUser").value.trim();
    const password = $("authPass").value;
    const displayName = $("authName").value.trim();
    if (!username || !password) { $("authErr").textContent = "Enter a username and password."; return; }
    const path = state.authMode === "register" ? "/api/auth/register" : "/api/auth/login";
    try {
        const me = await api(path, {method: "POST", body: JSON.stringify({username, password, displayName})});
        setUser(me);
        $("authPass").value = "";
        toast("Welcome, " + (me.displayName || me.username));
        await loadWallets();
        refreshView();
    } catch (e) {
        $("authErr").textContent = e.message || "Sign-in failed.";
    }
}
async function logout() {
    try { await api("/api/auth/logout", {method: "POST"}); } catch (e) { /* ignore */ }
    setUser(null);
    state.wallets = []; state.identity = null;
    fillIdentity();
    setAuthMode("login");
    refreshView();
}
function browseAsGuest() { $("authOverlay").hidden = true; }
function onUnauthorized() {
    if (state.user) setUser(null);
    const ov = $("authOverlay");
    if (ov) ov.hidden = false;
}

// ---- boot ----
async function boot() {
    try {
        await loadAuth();
        await loadCurrencies();
        if (state.user) await loadWallets();
        await loadDashboard();
    } catch (e) { toast("Could not reach node: " + e.message, "err"); }
}
boot();
// slower full refresh for non-exchange views
setInterval(() => {
    loadInfo().catch(() => {});
    if (state.view !== "exchange") refreshView();
}, 6000);
// fast live-market tick: keep the exchange ticker / book / trades moving with animation
setInterval(() => {
    if (state.view === "exchange") { loadTicker(); loadTrades(); loadOrderBook(); }
}, 4000);
