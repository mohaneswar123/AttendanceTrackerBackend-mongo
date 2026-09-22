package com.AttendanceRegister.sdc.Repository;


import org.springframework.data.mongodb.repository.MongoRepository;

import com.AttendanceRegister.sdc.model.User;

public interface UserRepository extends MongoRepository<User, String> {
	User findByEmail(String email);
	boolean existsByEmail(String email);
}

