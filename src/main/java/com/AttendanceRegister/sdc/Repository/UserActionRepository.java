package com.AttendanceRegister.sdc.Repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.AttendanceRegister.sdc.model.UserAction;

public interface UserActionRepository extends MongoRepository<UserAction, String> {

    List<UserAction> findByUserIdOrderByAtDesc(String userId, Pageable pageable);

    List<UserAction> findByUserId(String userId);

    void deleteByUserId(String userId);
}
