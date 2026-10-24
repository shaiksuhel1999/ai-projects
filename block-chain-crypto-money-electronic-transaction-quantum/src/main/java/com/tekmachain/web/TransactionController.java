package com.tekmachain.web;

import com.tekmachain.auth.WalletAccessGuard;
import com.tekmachain.domain.Transaction;
import com.tekmachain.p2p.NetworkService;
import com.tekmachain.service.MempoolService;
import com.tekmachain.service.TransactionService;
import com.tekmachain.web.dto.Dtos;
import com.tekmachain.web.dto.Mapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TransactionController {

    private final TransactionService transactions;
    private final MempoolService mempool;
    private final NetworkService network;
    private final WalletAccessGuard guard;

    public TransactionController(TransactionService transactions, MempoolService mempool,
                                 NetworkService network, WalletAccessGuard guard) {
        this.transactions = transactions;
        this.mempool = mempool;
        this.network = network;
        this.guard = guard;
    }

    /** Create, sign and broadcast a payment (it lands in the mempool and gossips to peers). */
    @PostMapping("/transactions")
    public Dtos.TransactionDto send(@RequestBody Dtos.SendRequest req) {
        guard.assertCanActAs(req.from());   // only spend from a custodial wallet you own
        Transaction tx = transactions.createPayment(req.from(), req.to(), req.amount(), req.fee());
        Dtos.TransactionDto dto = Mapper.tx(tx);
        network.broadcastTransaction(dto);
        return dto;
    }

    /**
     * Accept a transaction that was already built and signed by the client
     * (Phase 7 self-custody). The node never sees the private key — it only
     * verifies the signatures against the UTXO set, pools it, and gossips it.
     */
    @PostMapping("/transactions/raw")
    public Dtos.TransactionDto sendRaw(@RequestBody Dtos.TransactionDto dto) {
        Transaction tx = Mapper.txEntity(dto);
        transactions.validate(tx);   // verifies each signature + ownership; throws on failure
        mempool.add(tx);
        Dtos.TransactionDto out = Mapper.tx(tx);
        network.broadcastTransaction(out);
        return out;
    }

    @GetMapping("/mempool")
    public List<Dtos.TransactionDto> mempool() {
        return mempool.all().stream().map(Mapper::tx).toList();
    }
}
