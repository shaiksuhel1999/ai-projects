// ===== TekmaChain lightweight SVG charts (no external libraries) =====
const Charts = (function () {
    const esc = (s) => String(s).replace(/[&<>"']/g, c => ({"&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"}[c]));

    function empty(msg) { return `<div class="chart-empty">${esc(msg || "No data yet")}</div>`; }

    // Line / area chart from an array of numbers (chronological).
    function line(values, opts = {}) {
        values = (values || []).filter(v => v != null && isFinite(v));
        if (values.length < 2) return empty(opts.emptyMsg);
        const w = opts.w || 640, h = opts.h || 180, pad = 10, padB = 18, padL = 44;
        const color = opts.color || "var(--accent)";
        let min = Math.min(...values), max = Math.max(...values);
        if (min === max) { min -= 1; max += 1; }
        const span = max - min;
        const X = (i) => padL + (i / (values.length - 1)) * (w - padL - pad);
        const Y = (v) => pad + (1 - (v - min) / span) * (h - pad - padB);
        const pts = values.map((v, i) => `${X(i).toFixed(1)},${Y(v).toFixed(1)}`);
        const linePath = "M" + pts.join(" L");
        const areaPath = `M${X(0)},${Y(min)} L` + pts.join(" L") + ` L${X(values.length - 1)},${Y(min)} Z`;
        const gid = "g" + Math.random().toString(36).slice(2, 8);
        const up = values[values.length - 1] >= values[0];
        const stroke = opts.color || (up ? "var(--up)" : "var(--down)");
        // horizontal gridlines + y labels
        let grid = "";
        for (let g = 0; g <= 3; g++) {
            const val = min + (span * g / 3), y = Y(val);
            grid += `<line x1="${padL}" y1="${y}" x2="${w - pad}" y2="${y}" class="c-grid"/>`;
            grid += `<text x="${padL - 6}" y="${y + 3}" class="c-lbl" text-anchor="end">${fmtNum(val)}</text>`;
        }
        return `<svg viewBox="0 0 ${w} ${h}" class="chart" preserveAspectRatio="none">
            <defs><linearGradient id="${gid}" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0" stop-color="${stroke}" stop-opacity="0.28"/>
                <stop offset="1" stop-color="${stroke}" stop-opacity="0"/></linearGradient></defs>
            ${grid}
            <path d="${areaPath}" fill="url(#${gid})"/>
            <path d="${linePath}" fill="none" stroke="${stroke}" stroke-width="2" stroke-linejoin="round"/>
            <circle cx="${X(values.length - 1)}" cy="${Y(values[values.length - 1])}" r="3.2" fill="${stroke}"/>
        </svg>`;
    }

    // Vertical bar chart. items: [{label, value, color?}]
    function bars(items, opts = {}) {
        items = items || [];
        if (!items.length) return empty(opts.emptyMsg);
        const w = opts.w || 640, h = opts.h || 190, pad = 10, padB = 26, padL = 44;
        const max = Math.max(...items.map(i => i.value), 1);
        const bw = (w - padL - pad) / items.length;
        const Y = (v) => pad + (1 - v / max) * (h - pad - padB);
        let grid = "";
        for (let g = 0; g <= 3; g++) { const val = max * g / 3, y = Y(val); grid += `<line x1="${padL}" y1="${y}" x2="${w - pad}" y2="${y}" class="c-grid"/><text x="${padL - 6}" y="${y + 3}" class="c-lbl" text-anchor="end">${fmtNum(val)}</text>`; }
        const rects = items.map((it, i) => {
            const x = padL + i * bw + bw * 0.16, bwid = bw * 0.68, y = Y(it.value), bh = (h - pad - padB) - (y - pad);
            const col = it.color || "var(--accent)";
            return `<rect x="${x.toFixed(1)}" y="${y.toFixed(1)}" width="${bwid.toFixed(1)}" height="${Math.max(1, bh).toFixed(1)}" rx="3" fill="${col}"><title>${esc(it.label)}: ${fmtNum(it.value)}</title></rect>
                <text x="${(x + bwid / 2).toFixed(1)}" y="${h - 9}" class="c-lbl" text-anchor="middle">${esc(it.label)}</text>`;
        }).join("");
        return `<svg viewBox="0 0 ${w} ${h}" class="chart" preserveAspectRatio="none">${grid}${rects}</svg>`;
    }

    // Market-depth chart. bids/asks: [{price, total}] cumulative, bids desc, asks asc.
    function depth(bids, asks, opts = {}) {
        bids = (bids || []).slice(); asks = (asks || []).slice();
        if (!bids.length && !asks.length) return empty("No open orders — the book is empty");
        const w = opts.w || 640, h = opts.h || 200, pad = 10, padB = 18;
        const bidsAsc = bids.slice().sort((a, b) => a.price - b.price);
        const allP = [...bidsAsc.map(b => +b.price), ...asks.map(a => +a.price)];
        let minP = Math.min(...allP), maxP = Math.max(...allP);
        if (minP === maxP) { minP *= 0.99; maxP *= 1.01; }
        const maxT = Math.max(...bids.map(b => +b.total), ...asks.map(a => +a.total), 1);
        const X = (p) => pad + ((p - minP) / (maxP - minP)) * (w - 2 * pad);
        const Y = (t) => pad + (1 - t / maxT) * (h - pad - padB);
        const area = (pointsArr, color, gid) => {
            if (pointsArr.length < 1) return "";
            const pts = pointsArr.map(p => `${X(p.price).toFixed(1)},${Y(p.total).toFixed(1)}`);
            const x0 = X(pointsArr[0].price), x1 = X(pointsArr[pointsArr.length - 1].price);
            const path = `M${x0},${h - padB} L` + pts.join(" L") + ` L${x1},${h - padB} Z`;
            const stepLine = "M" + pts.join(" L");
            return `<defs><linearGradient id="${gid}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${color}" stop-opacity="0.32"/><stop offset="1" stop-color="${color}" stop-opacity="0"/></linearGradient></defs>
                <path d="${path}" fill="url(#${gid})"/><path d="${stepLine}" fill="none" stroke="${color}" stroke-width="2"/>`;
        };
        return `<svg viewBox="0 0 ${w} ${h}" class="chart" preserveAspectRatio="none">
            ${area(bidsAsc, "var(--up)", "dg" + Math.random().toString(36).slice(2, 7))}
            ${area(asks, "var(--down)", "dr" + Math.random().toString(36).slice(2, 7))}
        </svg>`;
    }

    function sparkline(values, opts = {}) {
        values = (values || []).filter(v => v != null && isFinite(v));
        if (values.length < 2) return "";
        const w = opts.w || 120, h = opts.h || 32;
        let min = Math.min(...values), max = Math.max(...values); if (min === max) { min -= 1; max += 1; }
        const X = (i) => (i / (values.length - 1)) * w, Y = (v) => (1 - (v - min) / (max - min)) * h;
        const d = "M" + values.map((v, i) => `${X(i).toFixed(1)},${Y(v).toFixed(1)}`).join(" L");
        const up = values[values.length - 1] >= values[0];
        return `<svg viewBox="0 0 ${w} ${h}" class="spark" preserveAspectRatio="none"><path d="${d}" fill="none" stroke="${up ? "var(--up)" : "var(--down)"}" stroke-width="1.6"/></svg>`;
    }

    function fmtNum(v) {
        const a = Math.abs(v);
        if (a >= 1e9) return (v / 1e9).toFixed(1) + "B";
        if (a >= 1e6) return (v / 1e6).toFixed(1) + "M";
        if (a >= 1e3) return (v / 1e3).toFixed(1) + "k";
        if (a >= 1) return v.toFixed(a < 100 ? 1 : 0);
        return v.toFixed(3);
    }

    return {line, bars, depth, sparkline};
})();
window.Charts = Charts;
