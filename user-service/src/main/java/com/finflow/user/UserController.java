package com.finflow.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    public record MeResponse(String id, String username, String email, String displayName, List<String> roles) {
    }

    public record UserSummary(String id, String username, String displayName) {
    }

    private final UserService service;

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt, Authentication auth) {
        UserProfile p = service.getOrCreate(jwt);
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.replaceFirst("^ROLE_", ""))
                .sorted()
                .toList();
        return new MeResponse(p.getId(), p.getUsername(), p.getEmail(), p.getDisplayName(), roles);
    }

    @GetMapping
    public List<UserSummary> list() {
        return service.list().stream()
                .map(u -> new UserSummary(u.getId(), u.getUsername(), u.getDisplayName()))
                .toList();
    }
}
