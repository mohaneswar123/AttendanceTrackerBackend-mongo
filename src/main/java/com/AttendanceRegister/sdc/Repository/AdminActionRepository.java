package com.AttendanceRegister.sdc.Repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.AttendanceRegister.sdc.model.AdminAction;

public interface AdminActionRepository extends MongoRepository<AdminAction, String> {

    List<AdminAction> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AdminAction> findByTargetUserIdOrderByCreatedAtDesc(String targetUserId, Pageable pageable);
}
