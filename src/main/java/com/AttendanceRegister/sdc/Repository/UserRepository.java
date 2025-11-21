package com.AttendanceRegister.sdc.Repository;


import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.AttendanceRegister.sdc.model.User;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
	User findByEmail(String email);
	boolean existsByEmail(String email);
}

