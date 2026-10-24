package com.tekmachain.web.dto;

import com.tekmachain.crypto.SigningData;
import com.tekmachain.domain.*;

import java.util.ArrayList;
import java.util.List;

/** Converts JPA entities into API DTOs and back. */
public final class Mapper {

    private Mapper() {
    }

    // ---- entity -> dto ----

    public static Dtos.TransactionDto tx(Transaction t) {
        return new Dtos.TransactionDto(
                t.getTxId(),
                t.isCoinbase(),
                t.getTimestamp(),
                t.getInputs().stream()
                        .map(i -> new Dtos.TxInputDto(i.getPrevTxId(), i.getOutIndex(),
                                i.getPublicKey(), i.getSignature(),
                                i.getDilithiumPublicKey(), i.getDilithiumSignature()))
                        .toList(),
                t.getOutputs().stream()
                        .map(o -> new Dtos.TxOutputDto(o.getIndex(), o.getAddress(), o.getAmount()))
                        .toList());
    }

    public static Dtos.BlockDto block(Block b) {
        return new Dtos.BlockDto(
                b.getHeight(),
                b.getTimestamp(),
                b.getPreviousHash(),
                b.getMerkleRoot(),
                b.getDifficulty(),
                b.getNonce(),
                b.getMinerAddress(),
                b.getHash(),
                b.getTransactions().stream().map(Mapper::tx).toList());
    }

    // ---- dto -> entity (for blocks received from peers) ----

    public static Transaction txEntity(Dtos.TransactionDto d) {
        Transaction t = new Transaction();
        t.setTxId(d.txId());
        t.setCoinbase(d.coinbase());
        t.setTimestamp(d.timestamp());
        List<TxInput> ins = new ArrayList<>();
        for (Dtos.TxInputDto i : d.inputs()) {
            TxInput in = new TxInput(i.prevTxId(), i.outIndex());
            in.setSignature(i.signature());
            in.setPublicKey(i.publicKey());
            in.setDilithiumPublicKey(i.dilithiumPublicKey());
            in.setDilithiumSignature(i.dilithiumSignature());
            ins.add(in);
        }
        t.setInputs(ins);
        List<TxOutput> outs = new ArrayList<>();
        for (Dtos.TxOutputDto o : d.outputs()) {
            outs.add(new TxOutput(o.index(), o.address(), SigningData.normalize(o.amount())));
        }
        t.setOutputs(outs);
        return t;
    }

    public static Block blockEntity(Dtos.BlockDto d) {
        Block b = new Block();
        b.setHeight(d.height());
        b.setTimestamp(d.timestamp());
        b.setPreviousHash(d.previousHash());
        b.setMerkleRoot(d.merkleRoot());
        b.setDifficulty(d.difficulty());
        b.setNonce(d.nonce());
        b.setMinerAddress(d.minerAddress());
        b.setHash(d.hash());
        b.setTransactions(d.transactions().stream().map(Mapper::txEntity).toList());
        return b;
    }
}
