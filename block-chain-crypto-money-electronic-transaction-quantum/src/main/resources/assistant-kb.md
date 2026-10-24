## Getting started on TekmaChain
keywords: start, begin, new, first, getting started, setup, quickstart, how do i start, onboard
action: wallet
New here? Do this in order: (1) Open the **Wallet** tab and create a wallet — pick **Custodial** for the quickest start. (2) Click **⛏ Mine** on that wallet to earn 50 TEK — mining is how new coins come into existence. (3) You can now **Send** TEK to another wallet, or go to the **Exchange** to trade. The **Explorer** tab shows every block, and **Network** connects you to other nodes.

## Signing in and accounts
keywords: login, log in, sign in, sign up, register, account, password, create account, log out, sign out, forgot password, who owns my wallet
action: wallet
TekmaChain has accounts. **Create an account** or **sign in** from the welcome screen — your password is stored only as a salted BCrypt hash, never in the clear. Once signed in, the **custodial wallets you create belong to your account**, and actions like creating a wallet, sending, mining and trading require you to be logged in. You can still **browse the markets and explorer without an account**. Sign out any time from the top-right. Self-custody and HD wallets live in your browser, so keep their recovery phrase safe — losing it means losing those coins regardless of your account.

## My profile and portfolio
keywords: profile, portfolio, holdings, what do i hold, my coins, my balance, net worth, my account, my wallets, total value
action: wallet
Once signed in, open **Profile** (the person icon in the sidebar, or click your name top-right) to see your whole portfolio in one place: your total value in your display currency, how much TEK you hold on-chain across every wallet (split into spendable vs still-maturing), what you're holding on the exchange, and a per-wallet breakdown. It's also where you manage your linked **bank** and **demat** accounts.

## Bank and demat accounts (deposit and withdraw)
keywords: bank account, demat, link bank, add bank, deposit money, withdraw money, fiat, ifsc, nsdl, cdsl, dp id, cannot deposit, fund account
action: exchange
To move **fiat** (USD/INR) in or out of the exchange you must first **link a bank account** on your **Profile** — just like a real exchange. Add it there (holder name, bank, account number, IFSC), then pick it in the Exchange deposit/withdraw form; the receipt names the account and your number is always shown masked. A **demat account** (NSDL/CDSL) represents where your securities are held and can be linked the same way. Depositing/withdrawing **TEK** itself uses your on-chain wallet, not a bank.

## Live crypto market prices
keywords: bitcoin price, btc, eth, ethereum, solana, real prices, live prices, market, markets, coinbase, real crypto, other coins, price feed
The Dashboard's **Live crypto markets** panel shows real-world prices for BTC, ETH, SOL and other major coins, pulled from Coinbase's public feed on a schedule and repriced into your chosen display currency. These are **reference prices only** — real coins are not tradable here and TekmaChain is never wired to real money. Tekma Coin (TEK) remains the one fictional asset you actually mine, hold and trade in this demo.

## Which wallet type should I use
keywords: wallet type, which wallet, custodial, self custody, hybrid, hd, difference, choose, kind of wallet, best wallet
action: wallet
Pick by how much control and protection you want: **Custodial** — the node holds the key; simplest, great for quick testing and receiving. **Self-custody (🔐)** — the key is generated and used only in your browser and the node never sees it; use this for true self-sovereign control. **Quantum-safe (🛡)** — signs with ECDSA *and* ML-DSA, so it stays secure even against a future quantum computer; use for maximum future-proofing. **HD (🌱)** — one 12-word recovery phrase backs up many addresses; use when you want easy backup and multiple addresses.

## How do I get TEK (mining)
keywords: get tek, get coins, earn, mine, mining, free coins, proof of work, reward, faucet, how to get
action: wallet
Mining creates new TEK. On the **Wallet** tab click **⛏ Mine** on any wallet — the node does Proof-of-Work (grinds a nonce until the block hash has enough leading zeros) and pays that wallet the block reward (currently 50 TEK) plus any fees from the block. The reward halves over time and total supply is capped at 21,000,000 TEK, just like Bitcoin.

## How do I send TEK
keywords: send, transfer, pay, send coins, send money, payment, move coins
action: wallet
On the **Wallet** tab use **Send**: choose the sender, the recipient address, an amount and a small fee, then broadcast. It lands in the mempool and becomes final when a block is mined that includes it. Self-custody and HD wallets sign the transaction *in your browser* before it is sent — the node only verifies it.

## Limit order vs Market order
keywords: limit, market, order type, which order, buy, sell, order, trade, trading, price
action: exchange
On the **Exchange**: a **Market** order buys or sells immediately at the best available price — use it when you want speed and don't mind the exact price. A **Limit** order lets you set the price; it only fills at that price or better and otherwise rests on the order book — use it when the price matters more than speed. Watch the order book and recent trades to judge the market before you place an order.

## How to deposit money into the exchange
keywords: deposit, fund, add money, deposit tek, deposit fiat, put money, top up, buying power
action: exchange
To trade you need a balance on the exchange. In the **Account** panel click **Deposit**: choose **TEK** to move coins on-chain from your wallet to the exchange (mine a block to confirm it), or a fiat currency like **USD/EUR** which is simulated and credited instantly. Then place orders. **Withdraw** sends TEK back on-chain to your wallet whenever you want.

## Keeping your keys and recovery phrase safe
keywords: recovery phrase, seed phrase, backup, keys, private key, safe, secure, lose, restore, security, protect
Your recovery phrase and private keys **are** the money — anyone who has them controls your coins. Write the 12-word phrase down offline and never share it or paste it anywhere. Self-custody and HD keys live only in your browser: if you clear your browser data without a backup, they are gone for good. The node never sees these keys.

## What is TekmaChain and Tekma Coin
keywords: what is, about, tekmachain, tekma coin, tek, overview, explain, introduction
TekmaChain is a Bitcoin-inspired cryptocurrency network, and its coin is **Tekma Coin (TEK)**. It has a real blockchain with Proof-of-Work mining, a UTXO ledger, cryptographic wallets, a peer-to-peer network, an exchange with an order book and matching engine, and Bitcoin-style monetary policy (reward halving plus a 21,000,000 supply cap). It is a proof of concept — TEK is fictional and not real money.

## Halving, supply cap and monetary policy
keywords: halving, supply, cap, inflation, how many coins, 21 million, monetary policy, reward schedule, scarcity
Tekma Coin follows Bitcoin's rules: the block reward starts at 50 TEK and **halves** on a fixed schedule, and total supply is **capped at 21,000,000 TEK** — enforced by every node, so no miner can mint extra. The **Dashboard** shows the current reward, how many blocks until the next halving, and how much of the cap has been minted so far.

## Why can't I spend my mining reward yet
keywords: maturing, immature, mature, coinbase maturity, cant spend, reward locked, mining reward locked, spendable, why locked, pending coins, confirmations
action: wallet
Freshly **mined** coins need to mature before you can spend them — exactly like Bitcoin's coinbase maturity rule. A newly mined reward shows on your wallet card as **"🔒 … TEK maturing"** and is not counted as spendable until a few more blocks are mined on top of it. This protects the network: if that block were ever replaced by a longer competing chain, its reward would vanish, so we don't let it be spent immediately. Keep mining (or wait for other blocks) and the reward becomes spendable automatically.

## How Proof-of-Work difficulty adjusts
keywords: difficulty, retarget, adjustment, proof of work, pow, hash, mining hard, target, block time, leading zeros, how mining difficulty changes
The chain aims for a steady block time. Every so often it **retargets**: if recent blocks came too fast the difficulty rises, if too slow it falls, with each step clamped so it can't swing wildly — the same idea as Bitcoin's 2016-block adjustment. Difficulty is measured in **leading zero bits** the block hash must have. Every node recomputes the expected difficulty for each block height and rejects any block that doesn't meet it, so no one can sneak in an easy block. You can watch the current difficulty on the Dashboard and in the Explorer.

## How transactions are signed and made tamper-proof
keywords: canonical, serialization, signing, signature, tamper proof, txid, transaction id, deterministic, bytes, format, consensus, how signing works, interop
Every transaction is turned into one **canonical binary form** — a fixed, length-prefixed byte layout where amounts are exact 64-bit satoshi integers, never decimal text. Its SHA-256 is the transaction id, and that same byte form is what your key signs. Because the browser wallet and the node build these bytes identically, a transaction signed in your browser hashes and verifies the same on every node — and no value can be reshaped to smuggle in extra coins. This is proven byte-for-byte by the project's cross-language test vectors.

## Quantum safety and the Security lab
keywords: quantum, quantum safe, quantum-safe, quantum resistant, is it quantum safe, wallet quantum safe, security, post quantum, ml-dsa, dilithium, falcon, benchmark, secure, security lab, future proof
action: security
The **Security** tab benchmarks ordinary ECDSA against post-quantum signatures (ML-DSA/Dilithium and Falcon), showing why a quantum computer threatens classic keys and what the size and speed cost of the defence is. If you want quantum-resistant coins today, create a **Quantum-safe** wallet on the Wallet tab — every spend is then signed with both ECDSA and ML-DSA.

## Explorer and validating the chain
keywords: explorer, blocks, validate, verify, chain, history, view blocks, tamper, integrity
The **Explorer** tab lists every block — expand one to see its transactions, hash, Merkle root and nonce. **Validate chain** re-checks the entire chain: every hash, link, Proof-of-Work, signature and the reward rules. If anything had been tampered with, it would be flagged immediately.

## Network, peers and running multiple nodes
keywords: network, peers, nodes, p2p, connect, decentralized, gossip, sync, cluster, mesh
TekmaChain is peer-to-peer. Start more nodes on other ports and connect them from the **Network** tab; they gossip transactions and blocks and converge on the longest valid chain. The status dot in the sidebar shows how many peers are online.

## Currency converter and display currency
keywords: convert, currency, converter, price in, exchange rate, fiat, local currency, inr, usd, eur, value
Use the **Currency converter** on the Dashboard to see 1 TEK (or any amount) in 130+ world currencies at live FX rates. The **Currency** selector in the top bar sets your display currency, which reprices your balances across the whole app.

## Transaction fees
keywords: fee, fees, cost, gas, charge, how much, expensive
When you send TEK you attach a small fee; miners collect fees on top of the block reward, which helps prioritise your transaction. Fiat deposits and withdrawals in this demo are simulated and free. Always keep a little TEK spare to cover fees.

## What can the assistant help with
keywords: help, stuck, what can you do, guide, assist, options, menu, confused, lost
I can guide you around TekmaChain. Try asking things like: "which wallet should I use?", "how do I get TEK?", "limit vs market order?", "how do I deposit to the exchange?", or "is it quantum-safe?". Tell me what you're trying to do and I'll point you to the right tab.
