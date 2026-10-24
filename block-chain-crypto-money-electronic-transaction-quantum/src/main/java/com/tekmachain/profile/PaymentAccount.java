package com.tekmachain.profile;

import jakarta.persistence.*;

/**
 * A funding account a user links to move money in and out of the exchange:
 * a <b>BANK</b> account for fiat (USD/INR) deposits and withdrawals, or a
 * <b>DEMAT</b> account that custodies their crypto/securities. Account numbers are
 * stored but only ever returned masked (last 4 digits).
 *
 * <p>POC note: no real bank or depository is contacted — these records simply model
 * the linked-account step a real exchange requires before it will settle fiat.</p>
 */
@Entity
@Table(name = "payment_account", indexes = @Index(name = "idx_payment_account_owner", columnList = "owner"))
public class PaymentAccount {

    public static final String BANK = "BANK";
    public static final String DEMAT = "DEMAT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The account (username) this belongs to. */
    @Column(nullable = false, length = 40)
    private String owner;

    /** BANK or DEMAT. */
    @Column(nullable = false, length = 8)
    private String kind;

    @Column(name = "holder_name", nullable = false)
    private String holderName;

    /** Bank name, or the depository (NSDL / CDSL) for a demat account. */
    @Column(nullable = false)
    private String institution;

    /**
     * Only the last 4 digits of the account number are ever kept — the app never needs
     * the full number (it only shows a masked form), so we don't store that PII at rest.
     */
    @Column(name = "number_last4", nullable = false, length = 4)
    private String numberLast4;

    /** IFSC/routing code for a bank, or the DP id for a demat account. */
    @Column(length = 24)
    private String code;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    public PaymentAccount() {
    }

    public PaymentAccount(String owner, String kind, String holderName, String institution, String fullNumber, String code) {
        this.owner = owner;
        this.kind = kind;
        this.holderName = holderName;
        this.institution = institution;
        this.numberLast4 = last4(fullNumber);
        this.code = code;
        this.createdAt = System.currentTimeMillis();
    }

    private static String last4(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 4 ? s : s.substring(s.length() - 4);
    }

    /** The only form the account number is ever shown in. */
    public String maskedNumber() {
        return "•••• " + numberLast4;
    }

    public Long getId() { return id; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }

    public String getInstitution() { return institution; }
    public void setInstitution(String institution) { this.institution = institution; }

    public String getNumberLast4() { return numberLast4; }
    public void setNumberLast4(String numberLast4) { this.numberLast4 = numberLast4; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
