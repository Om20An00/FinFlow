package com.finflow.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRepository extends JpaRepository<UserProfile, String> {
    List<UserProfile> findAllByOrderByDisplayNameAsc();
}
