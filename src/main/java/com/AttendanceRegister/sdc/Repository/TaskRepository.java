package com.AttendanceRegister.sdc.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Range;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.AttendanceRegister.sdc.model.Task;
import com.AttendanceRegister.sdc.model.TaskStatus;

public interface TaskRepository extends MongoRepository<Task, String> {
    List<Task> findByUserId(String userId, Sort sort);

    // taskDate is "YYYY-MM-DD", so comparing the strings compares the dates
    List<Task> findByUserIdAndTaskDateBetween(String userId, Range<String> dates, Sort sort);

    List<Task> findByUserIdAndStatusOrderByPositionAsc(String userId, TaskStatus status);

    Optional<Task> findFirstByUserIdAndStatusOrderByPositionDesc(String userId, TaskStatus status);

    void deleteByUserId(String userId);
}
