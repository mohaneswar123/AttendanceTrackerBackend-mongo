package com.AttendanceRegister.sdc.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Range;
import org.springframework.data.domain.Range.Bound;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.AttendanceRegister.sdc.Repository.TaskRepository;
import com.AttendanceRegister.sdc.dto.MoveTaskRequest;
import com.AttendanceRegister.sdc.dto.TaskRequest;
import com.AttendanceRegister.sdc.exception.ApiException;
import com.AttendanceRegister.sdc.model.Task;
import com.AttendanceRegister.sdc.model.TaskStatus;
import com.AttendanceRegister.sdc.util.Dates;

@Service
public class TaskService {

    static final double POSITION_STEP = 1024;
    // Below this, two positions are too close to fit another task between them
    static final double MIN_GAP = 1e-6;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final Sort BY_POSITION = Sort.by("position");

    private final TaskRepository taskRepository;
    private final Clock clock;

    public TaskService(TaskRepository taskRepository, Clock clock) {
        this.taskRepository = taskRepository;
        this.clock = clock;
    }

    // Tasks dated from..to inclusive; either end may be left open, and no dates means all tasks
    public List<Task> getTasks(String userId, String from, String to) {
        String fromDate = isBlank(from) ? null : Dates.parseIsoDate(from, "from").toString();
        String toDate = isBlank(to) ? null : Dates.parseIsoDate(to, "to").toString();
        if (fromDate == null && toDate == null) {
            return taskRepository.findByUserId(userId, BY_POSITION);
        }
        Range<String> dates = Range.of(
                fromDate == null ? Bound.unbounded() : Bound.inclusive(fromDate),
                toDate == null ? Bound.unbounded() : Bound.inclusive(toDate));
        return taskRepository.findByUserIdAndTaskDateBetween(userId, dates, BY_POSITION);
    }

    // New tasks go to the bottom of To Do
    public Task createTask(String userId, TaskRequest request) {
        Instant now = clock.instant();
        Task task = new Task();
        task.setUserId(userId);
        task.setTitle(validTitle(request.title()));
        task.setTaskDate(Dates.parseIsoDate(request.taskDate(), "Task date").toString());
        task.setPriority(request.priority());
        task.setStatus(TaskStatus.TODO);
        task.setPosition(endOfColumn(userId, TaskStatus.TODO));
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return taskRepository.save(task);
    }

    public Task updateTask(String userId, String taskId, TaskRequest request) {
        Task task = getOwnedTask(userId, taskId);
        task.setTitle(validTitle(request.title()));
        task.setTaskDate(Dates.parseIsoDate(request.taskDate(), "Task date").toString());
        task.setPriority(request.priority());
        task.setUpdatedAt(clock.instant());
        return taskRepository.save(task);
    }

    public Task moveTask(String userId, String taskId, MoveTaskRequest request) {
        Task task = getOwnedTask(userId, taskId);
        TaskStatus status = request.status();
        if (status == null) {
            throw ApiException.badRequest("Status is required");
        }

        List<Task> column = new ArrayList<>(taskRepository.findByUserIdAndStatusOrderByPositionAsc(userId, status));
        column.removeIf(t -> t.getId().equals(taskId));
        Task after = neighbour(column, request.afterTaskId(), taskId);
        Task before = neighbour(column, request.beforeTaskId(), taskId);
        if (after != null && before != null && column.indexOf(after) >= column.indexOf(before)) {
            throw invalidPosition();
        }

        Double position = positionBetween(column, after, before);
        if (position == null) {
            renumber(column);
            position = positionBetween(column, after, before);
        }

        Instant now = clock.instant();
        if (status == TaskStatus.DONE && task.getStatus() != TaskStatus.DONE) {
            task.setCompletedAt(now);
        } else if (status != TaskStatus.DONE) {
            task.setCompletedAt(null);
        }
        task.setStatus(status);
        task.setPosition(position);
        task.setUpdatedAt(now);
        return taskRepository.save(task);
    }

    public void deleteTask(String userId, String taskId) {
        taskRepository.delete(getOwnedTask(userId, taskId));
    }

    // Tasks of other students look the same as tasks that don't exist
    private Task getOwnedTask(String userId, String taskId) {
        return taskRepository.findById(taskId)
                .filter(task -> userId.equals(task.getUserId()))
                .orElseThrow(() -> ApiException.notFound("Task not found"));
    }

    private double endOfColumn(String userId, TaskStatus status) {
        return taskRepository.findFirstByUserIdAndStatusOrderByPositionDesc(userId, status)
                .map(last -> last.getPosition() + POSITION_STEP)
                .orElse(POSITION_STEP);
    }

    // A neighbour must be another of this student's tasks already in the destination column
    private static Task neighbour(List<Task> column, String neighbourId, String movingTaskId) {
        if (neighbourId == null) {
            return null;
        }
        if (neighbourId.equals(movingTaskId)) {
            throw invalidPosition();
        }
        return column.stream()
                .filter(t -> t.getId().equals(neighbourId))
                .findFirst()
                .orElseThrow(TaskService::invalidPosition);
    }

    // Right after `after` (or right before `before`), looking at the whole column so tasks
    // hidden by the date filter keep their place. Null when there is no room left.
    private static Double positionBetween(List<Task> column, Task after, Task before) {
        Double lower;
        Double upper;
        if (after != null) {
            int index = column.indexOf(after);
            lower = after.getPosition();
            upper = index + 1 < column.size() ? column.get(index + 1).getPosition() : null;
        } else if (before != null) {
            int index = column.indexOf(before);
            upper = before.getPosition();
            lower = index > 0 ? column.get(index - 1).getPosition() : null;
        } else {
            lower = column.isEmpty() ? null : column.get(column.size() - 1).getPosition();
            upper = null;
        }

        if (lower == null && upper == null) {
            return POSITION_STEP;
        }
        if (lower == null) {
            return upper - POSITION_STEP;
        }
        if (upper == null) {
            return lower + POSITION_STEP;
        }
        if (upper - lower < MIN_GAP) {
            return null;
        }
        return (lower + upper) / 2;
    }

    // Spreads the column out again, keeping its order
    private void renumber(List<Task> column) {
        for (int i = 0; i < column.size(); i++) {
            column.get(i).setPosition((i + 1) * POSITION_STEP);
        }
        taskRepository.saveAll(column);
    }

    private static String validTitle(String title) {
        if (title == null || title.isBlank()) {
            throw ApiException.badRequest("Task title is required");
        }
        String trimmed = title.trim();
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw ApiException.badRequest("Task title must be at most " + MAX_TITLE_LENGTH + " characters");
        }
        return trimmed;
    }

    private static ApiException invalidPosition() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_POSITION",
                "The task can't be placed there. Refresh the board and try again.");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
