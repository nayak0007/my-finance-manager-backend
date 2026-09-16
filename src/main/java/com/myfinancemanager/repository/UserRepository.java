package com.myfinancemanager.repository;

import com.myfinancemanager.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** Neon Auth's `sub` claim, i.e. the `neon_auth.user.id` this account signs in as. */
    Optional<User> findByAuthSubject(String authSubject);
}
