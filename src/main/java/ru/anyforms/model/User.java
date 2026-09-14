package ru.anyforms.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "login_code_hash")
    private String loginCodeHash;

    @Column(name = "login_code_expires_at")
    private Instant loginCodeExpiresAt;

    @Column(name = "login_code_sent_at")
    private Instant loginCodeSentAt;

    @Column(name = "login_code_attempts", nullable = false)
    private int loginCodeAttempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
