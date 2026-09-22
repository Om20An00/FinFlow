package com.finflow.wallet;

import com.finflow.wallet.domain.Wallet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WalletDomainTest {

    private Wallet walletWith(String balance) {
        Wallet w = new Wallet();
        w.setUserId("u1");
        w.setBalance(new BigDecimal(balance));
        return w;
    }

    @Test
    void debitReducesBalance() {
        Wallet w = walletWith("100.00");
        w.debit(new BigDecimal("40.50"));
        assertEquals(new BigDecimal("59.50"), w.getBalance());
    }

    @Test
    void debitBeyondBalanceIsRejected() {
        Wallet w = walletWith("10.00");
        assertThrows(IllegalStateException.class, () -> w.debit(new BigDecimal("10.01")));
        assertEquals(new BigDecimal("10.00"), w.getBalance());
    }

    @Test
    void creditIncreasesBalance() {
        Wallet w = walletWith("10.00");
        w.credit(new BigDecimal("5.25"));
        assertEquals(new BigDecimal("15.25"), w.getBalance());
    }
}
