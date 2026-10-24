// ===== TekmaChain client-side crypto (Phase 7 self-custody) =====
// Pure-JS secp256k1 key generation + ECDSA signing + Base58Check addresses.
// Produces exactly the encodings the Java node expects (X.509 SubjectPublicKeyInfo
// public keys, DER ECDSA signatures over SHA-256), so keys never leave the browser.
const TekmaCrypto = (function () {
    const p = 0xfffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2fn;
    const n = 0xfffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141n;
    const Gx = 0x79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798n;
    const Gy = 0x483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8n;
    const G = {x: Gx, y: Gy};
    // constant DER prefix for an secp256k1 uncompressed SubjectPublicKeyInfo
    const SPKI_PREFIX = "3056301006072a8648ce3d020106052b8104000a034200";
    const B58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    // ---- hex / bytes ----
    const toHex = (u8) => Array.from(u8, b => b.toString(16).padStart(2, "0")).join("");
    const fromHex = (h) => { const a = new Uint8Array(h.length / 2); for (let i = 0; i < a.length; i++) a[i] = parseInt(h.substr(i * 2, 2), 16); return a; };
    const bytesToBig = (u8) => { let x = 0n; for (const b of u8) x = (x << 8n) | BigInt(b); return x; };
    const bigToBytes = (x, len) => { const o = new Uint8Array(len); for (let i = len - 1; i >= 0; i--) { o[i] = Number(x & 0xffn); x >>= 8n; } return o; };
    const enc = (s) => new TextEncoder().encode(s);

    // ---- field / curve ----
    const mod = (x, m = p) => ((x % m) + m) % m;
    function inv(a, m) { a = mod(a, m); let [or, r] = [a, m], [os, s] = [1n, 0n]; while (r !== 0n) { const q = or / r; [or, r] = [r, or - q * r]; [os, s] = [s, os - q * s]; } return mod(os, m); }
    function dbl(P) { if (!P) return null; if (P.y === 0n) return null; const l = mod(3n * P.x * P.x % p * inv(2n * P.y, p)); const x = mod(l * l - 2n * P.x); return {x, y: mod(l * (P.x - x) - P.y)}; }
    function add(P, Q) { if (!P) return Q; if (!Q) return P; if (P.x === Q.x) { if (mod(P.y + Q.y) === 0n) return null; return dbl(P); } const l = mod((Q.y - P.y) * inv(Q.x - P.x, p)); const x = mod(l * l - P.x - Q.x); return {x, y: mod(l * (P.x - x) - P.y)}; }
    function mul(k, P) { let R = null, A = P; while (k > 0n) { if (k & 1n) R = add(R, A); A = dbl(A); k >>= 1n; } return R; }

    // ---- SHA-256 (WebCrypto) ----
    async function sha256(u8) { return new Uint8Array(await crypto.subtle.digest("SHA-256", u8)); }
    async function sha256Hex(str) { return toHex(await sha256(enc(str))); }

    // ---- RIPEMD-160 ----
    const rl = (x, c) => ((x << c) | (x >>> (32 - c))) >>> 0;
    const ZL = [0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,7,4,13,1,10,6,15,3,12,0,9,5,2,14,11,8,3,10,14,4,9,15,8,1,2,7,0,6,13,11,5,12,1,9,11,10,0,8,12,4,13,3,7,15,14,5,6,2,4,0,5,9,7,12,2,10,14,1,3,8,11,6,15,13];
    const ZR = [5,14,7,0,9,2,11,4,13,6,15,8,1,10,3,12,6,11,3,7,0,13,5,10,14,15,8,12,4,9,1,2,15,5,1,3,7,14,6,9,11,8,12,2,10,0,4,13,8,6,4,1,3,11,15,0,5,12,2,13,9,7,10,14,12,15,10,4,1,5,8,7,6,2,13,14,0,3,9,11];
    const SL = [11,14,15,12,5,8,7,9,11,13,14,15,6,7,9,8,7,6,8,13,11,9,7,15,7,12,15,9,11,7,13,12,11,13,6,7,14,9,13,15,14,8,13,6,5,12,7,5,11,12,14,15,14,15,9,8,9,14,5,6,8,6,5,12,9,15,5,11,6,8,13,12,5,12,13,14,11,8,5,6];
    const SR = [8,9,9,11,13,15,15,5,7,7,8,11,14,14,12,6,9,13,15,7,12,8,9,11,7,7,12,7,6,15,13,11,9,7,15,11,8,6,6,14,12,13,5,14,13,13,7,5,15,5,8,11,14,14,6,14,6,9,12,9,12,5,15,8,8,5,12,9,12,5,14,6,8,13,6,5,15,13,11,11];
    function f(j, x, y, z) { if (j < 16) return x ^ y ^ z; if (j < 32) return (x & y) | (~x & z); if (j < 48) return (x | ~y) ^ z; if (j < 64) return (x & z) | (y & ~z); return x ^ (y | ~z); }
    const KL = [0x00000000, 0x5a827999, 0x6ed9eba1, 0x8f1bbcdc, 0xa953fd4e];
    const KR = [0x50a28be6, 0x5c4dd124, 0x6d703ef3, 0x7a6d76e9, 0x00000000];
    function ripemd160(msg) {
        const len = msg.length;
        const withOne = new Uint8Array((((len + 8) >> 6) + 1) << 6);
        withOne.set(msg);
        withOne[len] = 0x80;
        const bits = len * 8;
        const dv = new DataView(withOne.buffer);
        dv.setUint32(withOne.length - 8, bits >>> 0, true);
        dv.setUint32(withOne.length - 4, Math.floor(bits / 0x100000000), true);

        let h0 = 0x67452301, h1 = 0xefcdab89, h2 = 0x98badcfe, h3 = 0x10325476, h4 = 0xc3d2e1f0;
        const X = new Int32Array(16);
        for (let off = 0; off < withOne.length; off += 64) {
            for (let i = 0; i < 16; i++) X[i] = dv.getInt32(off + i * 4, true);
            let al = h0, bl = h1, cl = h2, dl = h3, el = h4;
            let ar = h0, br = h1, cr = h2, dr = h3, er = h4;
            for (let j = 0; j < 80; j++) {
                let t = (rl((al + f(j, bl, cl, dl) + X[ZL[j]] + KL[j / 16 | 0]) >>> 0, SL[j]) + el) >>> 0;
                al = el; el = dl; dl = rl(cl, 10); cl = bl; bl = t;
                t = (rl((ar + f(79 - j, br, cr, dr) + X[ZR[j]] + KR[j / 16 | 0]) >>> 0, SR[j]) + er) >>> 0;
                ar = er; er = dr; dr = rl(cr, 10); cr = br; br = t;
            }
            const t = (h1 + cl + dr) >>> 0;
            h1 = (h2 + dl + er) >>> 0; h2 = (h3 + el + ar) >>> 0; h3 = (h4 + al + br) >>> 0;
            h4 = (h0 + bl + cr) >>> 0; h0 = t;
        }
        const out = new Uint8Array(20);
        new DataView(out.buffer).setUint32(0, h0, true);
        new DataView(out.buffer).setUint32(4, h1, true);
        new DataView(out.buffer).setUint32(8, h2, true);
        new DataView(out.buffer).setUint32(12, h3, true);
        new DataView(out.buffer).setUint32(16, h4, true);
        return out;
    }

    // ---- Base58 ----
    function base58(u8) {
        let x = bytesToBig(u8), out = "";
        while (x > 0n) { const r = x % 58n; x = x / 58n; out = B58[Number(r)] + out; }
        for (const b of u8) { if (b === 0) out = "1" + out; else break; }
        return out;
    }

    // ---- DER ----
    function derInt(x) {
        let b = Array.from(bigToBytes(x, 32));
        while (b.length > 1 && b[0] === 0) b.shift();
        if (b[0] & 0x80) b.unshift(0);
        return [0x02, b.length, ...b];
    }
    function derSig(r, s) { const body = [...derInt(r), ...derInt(s)]; return toHex(new Uint8Array([0x30, body.length, ...body])); }

    // ---- public keys / addresses ----
    function publicKeyHex(d) { const Q = mul(d, G); return SPKI_PREFIX + "04" + toHex(bigToBytes(Q.x, 32)) + toHex(bigToBytes(Q.y, 32)); }
    async function addressFromPublicKeyHex(pubHex) {
        const h160 = ripemd160(await sha256(fromHex(pubHex)));
        const payload = new Uint8Array(21); payload[0] = 0x35; payload.set(h160, 1);
        const chk = (await sha256(await sha256(payload))).slice(0, 4);
        const full = new Uint8Array(25); full.set(payload); full.set(chk, 21);
        return "TEK" + base58(full);
    }

    function randScalar() { while (true) { const d = bytesToBig(crypto.getRandomValues(new Uint8Array(32))); if (d >= 1n && d < n) return d; } }

    // ---- API ----
    async function generateKey() {
        const d = randScalar();
        const publicKey = publicKeyHex(d);
        return {privateKey: toHex(bigToBytes(d, 32)), publicKey, address: await addressFromPublicKeyHex(publicKey)};
    }

    /** ECDSA sign raw bytes, matching Java's SHA256withECDSA over the same bytes. */
    async function signBytes(u8, privateKeyHex) {
        const d = bytesToBig(fromHex(privateKeyHex));
        const e = mod(bytesToBig(await sha256(u8)), n);
        while (true) {
            const k = randScalar();
            const R = mul(k, G);
            const r = mod(R.x, n);
            if (r === 0n) continue;
            const s = mod(inv(k, n) * (e + r * d), n);
            if (s === 0n) continue;
            return derSig(r, s);
        }
    }

    /** ECDSA sign an arbitrary message string, matching Java's SHA256withECDSA. */
    async function sign(messageStr, privateKeyHex) {
        return signBytes(enc(messageStr), privateKeyHex);
    }

    /** SHA-256 (hex) of raw bytes — used for transaction ids over the canonical form. */
    async function sha256HexBytes(u8) { return toHex(await sha256(u8)); }

    return {generateKey, sign, signBytes, addressFromPublicKeyHex, sha256Hex, sha256HexBytes, publicKeyHex,
        _mul: mul, _G: G, _n: n, _fromHex: fromHex, _toHex: toHex, _bigToBytes: bigToBytes};
})();
window.TekmaCrypto = TekmaCrypto;
