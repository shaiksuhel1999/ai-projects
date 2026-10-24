package com.tekmachain.service;

import com.tekmachain.domain.Transaction;
import com.tekmachain.domain.TxInput;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The mempool: validated-but-not-yet-mined transactions waiting to be included
 * in a block. Kept in memory (it is transient by nature). It also tracks which
 * outpoints are already reserved by pending transactions, so two mempool
 * transactions cannot try to spend the same UTXO (double-spend protection).
 */
@Service
public class MempoolService {

    private final Map<String, Transaction> pending = new LinkedHashMap<>();
    private final Set<String> reservedOutpoints = ConcurrentHashMap.newKeySet();

    public synchronized void add(Transaction tx) {
        for (TxInput in : tx.getInputs()) {
            if (reservedOutpoints.contains(in.outpoint())) {
                throw new IllegalStateException(
                        "Output " + in.outpoint() + " is already spent by a pending transaction");
            }
        }
        pending.put(tx.getTxId(), tx);
        for (TxInput in : tx.getInputs()) {
            reservedOutpoints.add(in.outpoint());
        }
    }

    public synchronized boolean isReserved(String outpoint) {
        return reservedOutpoints.contains(outpoint);
    }

    public synchronized boolean contains(String txId) {
        return pending.containsKey(txId);
    }

    /** Drop everything — used after adopting a peer's chain. */
    public synchronized void clearAll() {
        pending.clear();
        reservedOutpoints.clear();
    }

    public synchronized List<Transaction> snapshot() {
        return new ArrayList<>(pending.values());
    }

    public synchronized int size() {
        return pending.size();
    }

    /** Called after a block is mined: forget the transactions it confirmed. */
    public synchronized void remove(List<Transaction> included) {
        for (Transaction tx : included) {
            if (pending.remove(tx.getTxId()) != null) {
                for (TxInput in : tx.getInputs()) {
                    reservedOutpoints.remove(in.outpoint());
                }
            }
        }
    }

    public synchronized List<Transaction> all() {
        return Collections.unmodifiableList(new ArrayList<>(pending.values()));
    }
}
