package com.genzenglish.partner.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleSubject(String googleSubject);

    boolean existsByEmail(String email);

    /** Database cascades remove the profile, tokens, sessions and turns. */
    @Modifying
    @Query(value = "delete from app_user where id = :id", nativeQuery = true)
    int hardDelete(UUID id);
}
