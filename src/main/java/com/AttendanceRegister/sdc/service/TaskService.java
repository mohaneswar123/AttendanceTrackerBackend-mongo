package com.AttendanceRegister.sdc.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
import com.AttendanceRegister.sdc.model.TaskStep;
import com.AttendanceRegister.sdc.util.Dates;

@Service
public class TaskService {

    static final double POSITION_STEP = 1024;
    // Below this, two positions are too close to fit another task between them
    static final double MIN_GAP = 1e-6;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_NOTE_LENGTH = 1000;
    private static final int MAX_STEP_TITLE_LENGTH = 200;
    static final int MAX_STEPS = 20;
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

    // New tasks go to the bottom of the list
    public Task createTask(String userId, TaskRequest request) {
        Instant now = clock.instant();
        Task task = new Task();
        task.setUserId(userId);
        task.setStatus(TaskStatus.TODO);
        task.setPosition(endOfList(userId));
        task.setCreatedAt(now);
        apply(task, request, now);
        return taskRepository.save(task);
    }

    public Task updateTask(String userId, String taskId, TaskRequest request) {
        Task task = getOwnedTask(userId, taskId);
        apply(task, request, clock.instant());
        return taskRepository.save(task);
    }

    /** Tick a task off, or put it back. One tap, so it gets its own call. */
    public Task setDone(String userId, String taskId, boolean done) {
        Task task = getOwnedTask(userId, taskId);
        Instant now = clock.instant();
        if (done && !task.isDone()) {
            task.setCompletedAt(now);
        } else if (!done) {
            task.setCompletedAt(null);
        }
        task.setStatus(done ? TaskStatus.DONE : TaskStatus.TODO);
        task.setUpdatedAt(now);
        return taskRepository.save(task);
    }

    /** Drag to reorder. There are no columns now, so the order is one list. */
    public Task moveTask(String userId, String taskId, MoveTaskRequest request) {
        Task task = getOwnedTask(userId, taskId);

        List<Task> list = new ArrayList<>(taskRepository.findByUserId(userId, BY_POSITION));
        list.removeIf(t -> t.getId().equals(taskId));
        Task after = neighbour(list, request.afterTaskId(), taskId);
        Task before = neighbour(list, request.beforeTaskId(), taskId);
        if (after != null && before != null && list.indexOf(after) >= list.indexOf(before)) {
            throw invalidPosition();
        }

        Double position = positionBetween(list, after, before);
        if (position == null) {
            renumber(list);
            position = positionBetween(list, after, before);
        }

        task.setPosition(position);
        task.setUpdatedAt(clock.instant());
        return taskRepository.save(task);
    }

    public void deleteTask(String userId, String taskId) {
        taskRepository.delete(getOwnedTask(userId, taskId));
    }

    private void apply(Task task, TaskRequest request, Instant now) {
        if (request == null) {
            throw ApiException.badRequest("Nothing to save");
        }
        task.setTitle(validTitle(request.title()));
        task.setTaskDate(Dates.parseIsoDate(request.taskDate(), "Task date").toString());
        task.setImportant(Boolean.TRUE.equals(request.important()));
        task.setNote(validNote(request.note()));
        task.setSteps(validSteps(request.steps()));
        task.setUpdatedAt(now);
    }

    /**
     * The steps as the sheet last showed them. A step with no id has just been typed, so
     * it gets one; anything the sheet left out has been deleted.
     */
    private static List<TaskStep> validSteps(List<TaskRequest.TaskStepRequest> steps) {
        if (steps == null) {
            return new ArrayList<>();
        }
        if (steps.size() > MAX_STEPS) {
            throw ApiException.badRequest("A task can have at most " + MAX_STEPS
                    + " steps. More than that is really two tasks.");
        }
        List<TaskStep> out = new ArrayList<>();
        for (TaskRequest.TaskStepRequest step : steps) {
            String title = step.title() == null ? "" : step.title().trim();
            if (title.isEmpty()) {
                continue;
            }
            if (title.length() > MAX_STEP_TITLE_LENGTH) {
                throw ApiException.badRequest("A step must be at most " + MAX_STEP_TITLE_LENGTH + " characters");
            }
            boolean typedJustNow = step.id() == null || step.id().isBlank();
            out.add(new TaskStep(
                    typedJustNow ? UUID.randomUUID().toString() : step.id(),
                    title,
                    Boolean.TRUE.equals(step.done())));
        }
        return out;
    }

    private static String validNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String trimmed = note.trim();
        if (trimmed.length() > MAX_NOTE_LENGTH) {
            throw ApiException.badRequest("The note must be at most " + MAX_NOTE_LENGTH + " characters");
        }
        return trimmed;
    }

    // Tasks of other students look the same as tasks that don't exist
    private Task getOwnedTask(String userId, String taskId) {
        return taskRepository.findById(taskId)
                .filter(task -> userId.equals(task.getUserId()))
                .orElseThrow(() -> ApiException.notFound("Task not found"));
    }

    private double endOfList(String userId) {
        return taskRepository.findByUserId(userId, BY_POSITION).stream()
                .mapToDouble(Task::getPosition)
                .max()
                .orElse(0) + POSITION_STEP;
    }

    // A neighbour must be another of this student's own tasks, still in the list
    private static Task neighbour(List<Task> list, String neighbourId, String movingTaskId) {
        if (neighbourId == null) {
            return null;
        }
        if (neighbourId.equals(movingTaskId)) {
            throw invalidPosition();
        }
        return list.stream()
                .filter(t -> t.getId().equals(neighbourId))
                .findFirst()
                .orElseThrow(TaskService::invalidPosition);
    }

    // Right after `after` (or right before `before`), looking at the whole list so tasks
    // the open view does not show keep their place. Null when there is no room left.
    private static Double positionBetween(List<Task> list, Task after, Task before) {
        Double lower;
        Double upper;
        if (after != null) {
            int index = list.indexOf(after);
            lower = after.getPosition();
            upper = index + 1 < list.size() ? list.get(index + 1).getPosition() : null;
        } else if (before != null) {
            int index = list.indexOf(before);
            upper = before.getPosition();
            lower = index > 0 ? list.get(index - 1).getPosition() : null;
        } else {
            lower = list.isEmpty() ? null : list.get(list.size() - 1).getPosition();
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

    // Spreads the list out again, keeping its order
    private void renumber(List<Task> list) {
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setPosition((i + 1) * POSITION_STEP);
        }
        taskRepository.saveAll(list);
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
                "The task can't be placed there. Refresh the list and try again.");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
