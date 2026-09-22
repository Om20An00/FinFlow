package com.finflow.user;

import com.finflow.common.event.DomainEvent;
import com.finflow.common.event.Topics;
import com.finflow.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;
    private final OutboxService outbox;

    /** First login creates the profile and emits UserRegistered (wallet-service reacts by creating a wallet). */
    @Transactional
    public UserProfile getOrCreate(Jwt jwt) {
        String id = jwt.getSubject();
        return repository.findById(id).orElseGet(() -> {
            String username = firstNonBlank(jwt.getClaimAsString("preferred_username"), id);
            String display = firstNonBlank(jwt.getClaimAsString("name"), username);
            UserProfile profile = new UserProfile(id, username, jwt.getClaimAsString("email"), display, Instant.now());
            repository.save(profile);
            outbox.publish(Topics.USER_EVENTS, id,
                    DomainEvent.of("UserRegistered", id, "userId", id, "username", username, "displayName", display));
            return profile;
        });
    }

    @Transactional(readOnly = true)
    public List<UserProfile> list() {
        return repository.findAllByOrderByDisplayNameAsc();
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
