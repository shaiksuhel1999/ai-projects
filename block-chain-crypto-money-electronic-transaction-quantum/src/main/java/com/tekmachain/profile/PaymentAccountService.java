package com.tekmachain.profile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages a user's linked bank and demat accounts, and enforces the "you must link
 * a bank account before moving fiat" rule that a real exchange applies.
 */
@Service
public class PaymentAccountService {

    private final PaymentAccountRepository accounts;

    public PaymentAccountService(PaymentAccountRepository accounts) {
        this.accounts = accounts;
    }

    /** One account as safely exposed to the client (number masked). */
    public record View(Long id, String kind, String holderName, String institution,
                       String maskedNumber, String code, long createdAt) {
    }

    public static View view(PaymentAccount a) {
        return new View(a.getId(), a.getKind(), a.getHolderName(), a.getInstitution(),
                a.maskedNumber(), a.getCode(), a.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<View> list(String owner) {
        return accounts.findByOwnerOrderByCreatedAtDesc(owner).stream().map(PaymentAccountService::view).toList();
    }

    @Transactional
    public View link(String owner, String kind, String holderName, String institution, String number, String code) {
        String k = kind == null ? "" : kind.trim().toUpperCase();
        if (!PaymentAccount.BANK.equals(k) && !PaymentAccount.DEMAT.equals(k)) {
            throw new IllegalArgumentException("Account kind must be BANK or DEMAT");
        }
        if (isBlank(holderName)) {
            throw new IllegalArgumentException("Account holder name is required");
        }
        if (isBlank(institution)) {
            throw new IllegalArgumentException(PaymentAccount.BANK.equals(k)
                    ? "Bank name is required" : "Depository (NSDL/CDSL) is required");
        }
        String num = number == null ? "" : number.replaceAll("\\s+", "");
        if (num.length() < 4 || !num.matches("[A-Za-z0-9]+")) {
            throw new IllegalArgumentException("Enter a valid account number");
        }
        if (accounts.countByOwnerAndKind(owner, k) >= 5) {
            throw new IllegalStateException("You can link at most 5 " + k.toLowerCase() + " accounts");
        }
        String c = code == null ? null : code.trim().toUpperCase();
        return view(accounts.save(new PaymentAccount(owner, k, holderName.trim(), institution.trim(), num, c)));
    }

    @Transactional
    public void remove(String owner, Long id) {
        PaymentAccount a = accounts.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new IllegalArgumentException("No such linked account"));
        accounts.delete(a);
    }

    /**
     * The bank account to settle a fiat operation against: the one identified by
     * {@code bankId} (if given and owned by the user), otherwise the user's only/first
     * bank account. Throws if the user has not linked a bank account yet.
     */
    @Transactional(readOnly = true)
    public PaymentAccount requireBank(String owner, Long bankId) {
        if (bankId != null) {
            return accounts.findByIdAndOwner(bankId, owner)
                    .filter(a -> PaymentAccount.BANK.equals(a.getKind()))
                    .orElseThrow(() -> new IllegalArgumentException("That bank account isn't linked to your profile"));
        }
        List<PaymentAccount> banks = accounts.findByOwnerAndKind(owner, PaymentAccount.BANK);
        if (banks.isEmpty()) {
            throw new IllegalStateException("Link a bank account in your profile before moving fiat");
        }
        return banks.get(0);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
