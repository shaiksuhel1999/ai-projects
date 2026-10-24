// ===== TekmaChain HD wallets — BIP-39 mnemonic + BIP-32 derivation (browser-side) =====
// Builds on tekma-crypto.js (secp256k1) + WebCrypto (SHA-256, HMAC-SHA512, PBKDF2).
const TekmaHD = (function () {
    const C = TekmaCrypto;
    const n = C._n, toHex = C._toHex, fromHex = C._fromHex, bigToBytes = C._bigToBytes;
    const enc = (s) => new TextEncoder().encode(s);
    const bytesToBig = (u8) => { let x = 0n; for (const b of u8) x = (x << 8n) | BigInt(b); return x; };
    function concat(...arrs) { let len = arrs.reduce((s, a) => s + a.length, 0), o = new Uint8Array(len), off = 0; for (const a of arrs) { o.set(a, off); off += a.length; } return o; }

    async function sha256(u8) { return new Uint8Array(await crypto.subtle.digest("SHA-256", u8)); }
    async function hmacSha512(keyBytes, dataBytes) {
        const k = await crypto.subtle.importKey("raw", keyBytes, {name: "HMAC", hash: "SHA-512"}, false, ["sign"]);
        return new Uint8Array(await crypto.subtle.sign("HMAC", k, dataBytes));
    }
    async function pbkdf2Sha512(passBytes, saltBytes, iters, dkLenBytes) {
        const k = await crypto.subtle.importKey("raw", passBytes, "PBKDF2", false, ["deriveBits"]);
        const bits = await crypto.subtle.deriveBits({name: "PBKDF2", salt: saltBytes, iterations: iters, hash: "SHA-512"}, k, dkLenBytes * 8);
        return new Uint8Array(bits);
    }

    // compressed public key (33 bytes) from a private scalar
    function compressedPub(d) {
        const P = C._mul(d, C._G);
        return concat(new Uint8Array([(P.y & 1n) === 0n ? 0x02 : 0x03]), bigToBytes(P.x, 32));
    }

    // ---- BIP-39 ----
    const WORDS = window.BIP39_WORDS;

    async function generateMnemonic() {                // 128-bit entropy -> 12 words
        const ent = crypto.getRandomValues(new Uint8Array(16));
        const hash = await sha256(ent);
        let bits = "";
        for (const b of ent) bits += b.toString(2).padStart(8, "0");
        bits += hash[0].toString(2).padStart(8, "0").slice(0, 4);   // 4-bit checksum
        const words = [];
        for (let i = 0; i < bits.length; i += 11) words.push(WORDS[parseInt(bits.slice(i, i + 11), 2)]);
        return words.join(" ");
    }

    async function mnemonicToSeed(mnemonic, passphrase = "") {
        const m = mnemonic.normalize("NFKD");
        const salt = ("mnemonic" + passphrase).normalize("NFKD");
        return pbkdf2Sha512(enc(m), enc(salt), 2048, 64);
    }

    // ---- BIP-32 ----
    async function masterFromSeed(seed) {
        const I = await hmacSha512(enc("Bitcoin seed"), seed);
        return {key: I.slice(0, 32), chain: I.slice(32)};
    }
    function ser32(i) { const b = new Uint8Array(4); b[0] = (i >>> 24) & 255; b[1] = (i >>> 16) & 255; b[2] = (i >>> 8) & 255; b[3] = i & 255; return b; }

    async function ckdPriv(parent, index) {
        const hardened = index >= 0x80000000;
        const data = hardened
            ? concat(new Uint8Array([0]), parent.key, ser32(index))
            : concat(compressedPub(bytesToBig(parent.key)), ser32(index));
        const I = await hmacSha512(parent.chain, data);
        const childKey = (bytesToBig(I.slice(0, 32)) + bytesToBig(parent.key)) % n;
        return {key: bigToBytes(childKey, 32), chain: I.slice(32)};
    }

    async function derivePath(seed, path) {            // e.g. "m/44'/0'/0'/0/0"
        let node = await masterFromSeed(seed);
        for (const p of path.split("/").slice(1)) {
            const hard = p.endsWith("'");
            const idx = (parseInt(hard ? p.slice(0, -1) : p, 10) + (hard ? 0x80000000 : 0)) >>> 0;
            node = await ckdPriv(node, idx);
        }
        return node;
    }

    /** Derive N accounts at m/44'/0'/0'/0/i, each a usable secp256k1 wallet. */
    async function deriveAccounts(mnemonic, count) {
        const seed = await mnemonicToSeed(mnemonic);
        const out = [];
        for (let i = 0; i < count; i++) {
            const node = await derivePath(seed, "m/44'/0'/0'/0/" + i);
            const dHex = toHex(node.key);
            const pub = C.publicKeyHex(BigInt("0x" + dHex));
            out.push({index: i, path: "m/44'/0'/0'/0/" + i, privateKey: dHex, publicKey: pub, address: await C.addressFromPublicKeyHex(pub)});
        }
        return out;
    }

    return {generateMnemonic, mnemonicToSeed, masterFromSeed, ckdPriv, derivePath, deriveAccounts, _toHex: toHex};
})();
window.TekmaHD = TekmaHD;
