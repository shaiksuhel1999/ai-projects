// ===== TekmaChain UI — exchange trading terminal =====

const ex = {pair: localStorage.getItem("tekma.pair") || "TEK/USD", side: "BUY", type: "LIMIT", fundMode: null};

function qsym() { return QSYM[ex.pair] || quoteOf(ex.pair); }

function gotoPair(pair) { ex.pair = pair; localStorage.setItem("tekma.pair", pair); showView("exchange"); }
function gotoDeposit(address) {
    state.identity = address; localStorage.setItem("tekma.identity", address);
    $("identity").value = address;
    showView("exchange");
    setTimeout(() => toggleFunding("deposit"), 60);
}

// ---- segmented controls (wired once) ----
document.querySelectorAll("#segSide .seg-btn").forEach(b => b.addEventListener("click", () => {
    ex.side = b.dataset.side;
    document.querySelectorAll("#segSide .seg-btn").forEach(x => x.classList.toggle("active", x === b));
    syncOrderForm();
}));
document.querySelectorAll("#segType .seg-btn").forEach(b => b.addEventListener("click", () => {
    ex.type = b.dataset.type;
    document.querySelectorAll("#segType .seg-btn").forEach(x => x.classList.toggle("active", x === b));
    syncOrderForm();
}));

function syncOrderForm() {
    const buy = ex.side === "BUY";
    const btn = $("placeBtn");
    btn.className = "btn place " + (buy ? "buy" : "sell");
    btn.textContent = (buy ? "Buy" : "Sell") + " TEK";
    document.querySelectorAll(".qsym").forEach(e => e.textContent = qsym());
    const market = ex.type === "MARKET";
    $("priceRow").style.opacity = market ? ".4" : "1";
    $("ordPrice").disabled = market;
    recalcTotal();
    updateAvailHint();
}

function recalcTotal() {
    const p = parseFloat($("ordPrice").value), a = parseFloat($("ordAmount").value);
    if (ex.type === "MARKET") { $("ordTotal").textContent = "market"; return; }
    $("ordTotal").textContent = (p > 0 && a > 0) ? curSym(qsym()) + price(p * a) : "–";
}

function updateAvailHint() {
    if (!ex._account) { $("ordAvail").textContent = ""; return; }
    const need = ex.side === "BUY" ? qsym() : "TEK";
    const bal = ex._account.balances.find(b => b.asset === need);
    $("ordAvail").textContent = "Available: " + fmt(bal ? bal.available : 0, need === "TEK" ? 4 : 2) + " " + need;
}

// ---- orchestration ----
async function loadExchange() {
    renderPairList();
    $("bookPair").textContent = ex.pair;
    $("exIdentity").textContent = state.identity ? (identityLabel() + " · " + short(state.identity, 6)) : "no wallet";
    syncOrderForm();
    await Promise.all([loadTicker(), loadOrderBook(), loadTrades(), loadAccount(), loadOrders()]);
}

function renderPairList() {
    $("pairList").innerHTML = state.pairs.map(p =>
        `<div class="pair-item ${p === ex.pair ? "active" : ""}" onclick="selectPair('${p}')" id="pi-${p.replace('/', '-')}">
            <span class="pn">${p}</span><span class="pp" id="pp-${p.replace('/', '-')}">–</span></div>`).join("");
    // fill last prices
    state.pairs.forEach(async p => {
        try {
            const t = await api("/api/exchange/ticker?pair=" + encodeURIComponent(p));
            const el = $("pp-" + p.replace('/', '-'));
            if (el) el.textContent = t.lastPrice != null ? curSym(QSYM[p] || quoteOf(p)) + price(t.lastPrice) : "–";
        } catch (e) { /* ignore */ }
    });
}
function selectPair(p) { ex.pair = p; localStorage.setItem("tekma.pair", p); loadExchange(); }

async function loadTicker() {
    const t = await api("/api/exchange/ticker?pair=" + encodeURIComponent(ex.pair));
    const ch = t.changePct != null ? Number(t.changePct) : 0;
    const s = curSym(qsym());
    $("ticker").innerHTML = `
        <div class="tk-headcol"><div class="tk-pair">${ex.pair}</div>
            <span class="live-badge"><span class="live-dot"></span>LIVE</span></div>
        <div class="tk-last ${ch >= 0 ? "up" : "down"}" id="tkLast">${t.lastPrice != null ? s + price(t.lastPrice) : "—"}</div>
        <div class="tk-stat"><span class="l">24h change</span><span class="v ${ch >= 0 ? "up" : "down"}">${ch >= 0 ? "+" : ""}${ch.toFixed(2)}%</span></div>
        <div class="tk-stat"><span class="l">24h high</span><span class="v">${t.high != null ? s + price(t.high) : "–"}</span></div>
        <div class="tk-stat"><span class="l">24h low</span><span class="v">${t.low != null ? s + price(t.low) : "–"}</span></div>
        <div class="tk-stat"><span class="l">24h volume</span><span class="v">${fmt(t.baseVolume, 2)} TEK</span></div>
        <div class="tk-stat"><span class="l">best bid / ask</span><span class="v">${t.bestBid != null ? s + price(t.bestBid) : "–"} / ${t.bestAsk != null ? s + price(t.bestAsk) : "–"}</span></div>`;

    // animate the last price toward the new value and flash green/red on change
    ex._prevPrice = ex._prevPrice || {};
    const prev = ex._prevPrice[ex.pair];
    if (t.lastPrice != null) {
        const el = $("tkLast");
        if (prev != null && el) { el.dataset.v = prev; animateCount(el, t.lastPrice, 2, s); flashEl(el, t.lastPrice >= prev); }
        ex._prevPrice[ex.pair] = Number(t.lastPrice);
    }
}

async function loadOrderBook() {
    const b = await api("/api/exchange/orderbook?pair=" + encodeURIComponent(ex.pair));
    const s = curSym(qsym());
    const asks = b.asks.slice(0, 10);
    const bids = b.bids.slice(0, 10);
    const maxA = asks.length ? Number(asks[asks.length - 1].total) : 1;
    const maxB = bids.length ? Number(bids[bids.length - 1].total) : 1;

    const askRows = asks.slice().reverse().map(l => bookRow("ask", l, s, maxA)).join("");
    const bidRows = bids.map(l => bookRow("bid", l, s, maxB)).join("");
    const spread = (b.bestAsk != null && b.bestBid != null) ? (Number(b.bestAsk) - Number(b.bestBid)) : null;
    const last = ex._lastPrice;

    const depthEl = $("exDepthChart");
    if (depthEl) depthEl.innerHTML = Charts.depth(
        b.bids.map(l => ({price: Number(l.price), total: Number(l.total)})),
        b.asks.map(l => ({price: Number(l.price), total: Number(l.total)})));

    $("orderbook").innerHTML = `
        <div class="book-head"><span>Price (${qsym()})</span><span class="a-right">Amount</span><span class="a-right">Total</span></div>
        <div class="book-side">${askRows || '<p class="empty">No asks</p>'}</div>
        <div class="book-mid"><span class="lp ${ex._lastUp ? "up" : "down"}">${last != null ? s + price(last) : "—"}</span>
            <span class="sp">spread ${spread != null ? s + price(spread) : "–"}</span></div>
        <div class="book-side">${bidRows || '<p class="empty">No bids</p>'}</div>`;
}
function bookRow(side, l, s, max) {
    const w = Math.min(100, (Number(l.total) / (max || 1)) * 100);
    return `<div class="book-row ${side}" onclick="fillPrice('${l.price}')">
        <span class="px">${s}${price(l.price)}</span>
        <span class="a-right">${fmt(l.amount, 4)}</span>
        <span class="a-right">${fmt(l.total, 4)}</span>
        <span class="depth" style="width:${w}%"></span></div>`;
}
function fillPrice(p) { $("ordPrice").value = p; recalcTotal(); }

async function loadTrades() {
    const trades = await api("/api/exchange/trades?pair=" + encodeURIComponent(ex.pair));
    const s = curSym(qsym());
    if (trades.length) { ex._lastPrice = Number(trades[0].price); ex._lastUp = trades.length < 2 || Number(trades[0].price) >= Number(trades[1].price); }

    const priceEl = $("exPriceChart");
    if (priceEl) priceEl.innerHTML = Charts.line(trades.slice().reverse().map(t => Number(t.price)), {emptyMsg: "No trades yet for " + ex.pair});
    const cl = $("chartLast");
    if (cl) cl.textContent = trades.length ? s + price(trades[0].price) : "—";

    $("exTrades").innerHTML =
        `<div class="tfeed-head"><span>Price</span><span class="a-right">Amount</span><span class="a-right">Time</span></div>` +
        (trades.map(t => `<div class="tfeed-row"><span class="${t.takerSide === "BUY" ? "up" : "down"}">${s}${price(t.price)}</span>
            <span class="a-right">${fmt(t.amount, 4)}</span>
            <span class="a-right" style="color:var(--muted-2)">${new Date(t.createdAt).toLocaleTimeString([], {hour: '2-digit', minute: '2-digit', second: '2-digit'})}</span></div>`).join("")
        || '<p class="empty">No trades yet.</p>');
}

async function loadAccount() {
    if (!state.identity) { $("exBalances").innerHTML = '<p class="empty">Create/select a wallet to trade.</p>'; ex._account = null; return; }
    const acc = await api("/api/exchange/account/" + state.identity);
    ex._account = acc;
    $("exBalances").innerHTML = acc.balances.map(b =>
        `<div class="bal-row"><span class="asset">${b.asset}</span>
         <span><span class="amt">${fmt(b.available, b.asset === "TEK" ? 4 : 2)}</span>${Number(b.locked) > 0 ? ` <span class="lk">(+${fmt(b.locked, 2)} locked)</span>` : ""}</span></div>`).join("");
    updateAvailHint();
}

async function loadOrders() {
    if (!state.identity) { $("openOrders").innerHTML = ""; $("orderHistory").innerHTML = ""; return; }
    const orders = await api("/api/exchange/orders/" + state.identity);
    const open = orders.filter(o => o.status === "OPEN" || o.status === "PARTIALLY_FILLED");
    const done = orders.filter(o => o.status !== "OPEN" && o.status !== "PARTIALLY_FILLED").slice(0, 15);
    $("openOrders").innerHTML = open.map(o => orderRow(o, true)).join("") || '<p class="empty">No open orders.</p>';
    $("orderHistory").innerHTML = done.map(o => orderRow(o, false)).join("") || '<p class="empty">No past orders.</p>';
}
function orderRow(o, cancelable) {
    const s = curSym(quoteOf(o.pair));
    const px = o.type === "MARKET" ? "market" : s + price(o.price);
    return `<div class="ord ${o.side}">
        <div class="r1"><span class="side-b ${o.side}">${o.side} ${o.type}</span>
            ${cancelable ? `<button class="cancel" onclick="cancelOrder(${o.id})">✕ cancel</button>` : `<span class="st ${o.status}">${o.status}</span>`}</div>
        <div class="meta">${fmt(o.filled, 4)}/${fmt(o.amount, 4)} TEK @ ${px} · ${o.pair}</div></div>`;
}

// ---- actions ----
async function placeOrder() {
    if (!state.identity) return toast("Select a wallet (top-right) first", "err");
    const req = {address: state.identity, pair: ex.pair, side: ex.side, type: ex.type,
        price: $("ordPrice").value, amount: $("ordAmount").value};
    if (!(parseFloat(req.amount) > 0)) return toast("Enter an amount", "err");
    if (ex.type === "LIMIT" && !(parseFloat(req.price) > 0)) return toast("Enter a limit price", "err");
    try {
        const o = await api("/api/exchange/orders", {method: "POST", body: JSON.stringify(req)});
        $("ordAmount").value = "";
        toast(`${o.side} order ${o.status.toLowerCase()} · filled ${fmt(o.filled, 4)} TEK`);
        loadExchange();
    } catch (e) { toast(e.message, "err"); }
}
async function cancelOrder(id) {
    try {
        await api(`/api/exchange/orders/${id}/cancel?address=${encodeURIComponent(state.identity)}`, {method: "POST"});
        toast("Order cancelled");
        loadExchange();
    } catch (e) { toast(e.message, "err"); }
}

// ---- funding ----
async function toggleFunding(mode) {
    const panel = $("fundingPanel");
    if (ex.fundMode === mode && !panel.classList.contains("hidden")) { panel.classList.add("hidden"); ex.fundMode = null; return; }
    ex.fundMode = mode;
    panel.classList.remove("hidden");
    ex._accounts = [];
    try { if (state.user) ex._accounts = await api("/api/profile/accounts"); } catch (e) { /* guest / none */ }
    const isDep = mode === "deposit";
    panel.innerHTML = `
        <div class="seg-mini">
            <button class="btn tiny" onclick="setFundAsset('TEK')" id="fa-TEK">TEK (on-chain)</button>
            <button class="btn tiny" onclick="setFundAsset('USD')" id="fa-USD">USD</button>
            <button class="btn tiny" onclick="setFundAsset('INR')" id="fa-INR">INR</button>
        </div>
        <div id="fundAccount"></div>
        <label>Amount<input id="fundAmount" type="number" step="0.00000001" placeholder="0.00"/></label>
        <button class="btn ${isDep ? "primary" : ""}" onclick="submitFunding()">${isDep ? "Deposit" : "Withdraw"}</button>
        <p class="hint" id="fundHint" style="margin:2px 0"></p>`;
    ex.fundAsset = "TEK";
    setFundAsset("TEK");
}
function setFundAsset(a) {
    ex.fundAsset = a;
    ["TEK", "USD", "INR"].forEach(x => { const el = $("fa-" + x); if (el) el.classList.toggle("primary", x === a); });
    $("fundHint").textContent = a === "TEK"
        ? (ex.fundMode === "deposit" ? "Signs an on-chain transfer to the exchange hot wallet — mine a block to confirm." : "Sends TEK on-chain from the hot wallet back to your wallet.")
        : "Fiat settles to your linked bank account (simulated — no real bank is contacted).";
    renderFundAccount();
}
function renderFundAccount() {
    const slot = $("fundAccount");
    if (!slot) return;
    const accts = ex._accounts || [];
    if (ex.fundAsset === "TEK") {
        const demat = accts.filter(a => a.kind === "DEMAT");
        slot.innerHTML = demat.length
            ? `<label>Demat account<select id="fundDemat">${demat.map(a => `<option value="${a.id}">${escapeHtml(a.institution)} ${a.maskedNumber}</option>`).join("")}</select></label>`
            : "";
        return;
    }
    const banks = accts.filter(a => a.kind === "BANK");
    slot.innerHTML = banks.length
        ? `<label>Bank account<select id="fundBank">${banks.map(a => `<option value="${a.id}">${escapeHtml(a.institution)} ${a.maskedNumber}</option>`).join("")}</select></label>`
        : `<p class="hint" style="color:var(--gold);margin:2px 0">Link a bank account in your <a href="#" onclick="showView('profile');return false" style="color:var(--primary-2)">profile</a> to move fiat.</p>`;
}
async function submitFunding() {
    if (!state.identity) return toast("Select a wallet first", "err");
    const amount = $("fundAmount").value;
    if (!(parseFloat(amount) > 0)) return toast("Enter an amount", "err");
    const dep = ex.fundMode === "deposit";
    try {
        let r;
        if (ex.fundAsset === "TEK") {
            r = await api(`/api/exchange/${dep ? "deposit" : "withdraw"}/tek`, {method: "POST", body: JSON.stringify({address: state.identity, amount})});
        } else {
            const bankSel = $("fundBank");
            if (!bankSel) return toast("Link a bank account in your profile first", "warn");
            r = await api(`/api/exchange/${dep ? "deposit" : "withdraw"}/fiat`, {method: "POST",
                body: JSON.stringify({address: state.identity, asset: ex.fundAsset, amount, bankAccountId: Number(bankSel.value)})});
        }
        toast(r.message || "Done");
        $("fundAmount").value = "";
        loadAccount();
        if (ex.fundAsset === "TEK") loadWallets();
    } catch (e) { toast(e.message, "err"); }
}
