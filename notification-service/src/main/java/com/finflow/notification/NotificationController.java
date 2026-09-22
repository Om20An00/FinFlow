package com.finflow.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    @GetMapping("/me")
    public List<Notification> mine(@AuthenticationPrincipal Jwt jwt) throws Exception {
        List<String> raw = redis.opsForList().range("notif:user:" + jwt.getSubject(), 0, 29);
        List<Notification> out = new ArrayList<>();
        if (raw != null) {
            for (String json : raw) {
                out.add(mapper.readValue(json, Notification.class));
            }
        }
        return out;
    }
}
