package com.finflow.wallet.api;

import com.finflow.wallet.WalletService;
import com.finflow.wallet.api.dto.TxView;
import com.finflow.wallet.api.dto.WalletView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WalletController {

    public record TopUpRequest(@NotNull @DecimalMin("1.00") @DecimalMax("50000.00") BigDecimal amount) {
    }

    private final WalletService service;

    @GetMapping("/wallets/me")
    public WalletView me(@AuthenticationPrincipal Jwt jwt) {
        return service.view(jwt.getSubject());
    }

    @GetMapping("/wallets/me/transactions")
    public List<TxView> transactions(@AuthenticationPrincipal Jwt jwt) {
        return service.history(jwt.getSubject());
    }

    @PostMapping("/wallets/me/topup")
    public WalletView topUp(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TopUpRequest request) {
        return service.topUp(jwt.getSubject(), request.amount());
    }

    // ------------------------------------------------------------ ADMIN only

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/wallets")
    public List<WalletView> all() {
        return service.listAll();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/wallets/{userId}/freeze")
    public WalletView freeze(@PathVariable String userId, @AuthenticationPrincipal Jwt jwt) {
        return service.setFrozen(userId, true, jwt.getClaimAsString("preferred_username"));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/wallets/{userId}/unfreeze")
    public WalletView unfreeze(@PathVariable String userId, @AuthenticationPrincipal Jwt jwt) {
        return service.setFrozen(userId, false, jwt.getClaimAsString("preferred_username"));
    }
}
