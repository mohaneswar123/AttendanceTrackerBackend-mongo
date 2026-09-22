package com.AttendanceRegister.sdc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Range;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import com.AttendanceRegister.sdc.Repository.TaskRepository;
import com.AttendanceRegister.sdc.dto.MoveTaskRequest;
import com.AttendanceRegister.sdc.dto.TaskRequest;
import com.AttendanceRegister.sdc.exception.ApiException;
import com.AttendanceRegister.sdc.model.Task;
import com.AttendanceRegister.sdc.model.TaskPriority;
import com.AttendanceRegister.sdc.model.TaskStatus;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");

    @Mock
    private TaskRepository taskRepository;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Task task(String id, TaskStatus status, double position) {
        Task task = new Task();
        task.setId(id);
        task.setUserId("u1");
        task.setTitle("Task " + id);
        task.setTaskDate("2026-09-22");
        task.setStatus(status);
        task.setPosition(position);
        return task;
    }

    private void givenTask(Task task) {
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
    }

    private void givenColumn(TaskStatus status, Task... tasks) {
        when(taskRepository.findByUserIdAndStatusOrderByPositionAsc("u1", status)).thenReturn(List.of(tasks));
    }

    private Task move(String taskId, TaskStatus status, String afterId, String beforeId) {
        // lenient: rejected moves never get as far as saving
        lenient().when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        return taskService.moveTask("u1", taskId, new MoveTaskRequest(status, afterId, beforeId));
    }

    private static void assertApiError(Throwable thrown, HttpStatus status, String code) {
        assertThat(thrown).isInstanceOfSatisfying(ApiException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(status);
            assertThat(ex.getCode()).isEqualTo(code);
        });
    }

    @Test
    void createPutsTaskAtTheEndOfToDo() {
        when(taskRepository.findFirstByUserIdAndStatusOrderByPositionDesc("u1", TaskStatus.TODO))
                .thenReturn(Optional.of(task("last", TaskStatus.TODO, 2048)));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task created = taskService.createTask("u1", new TaskRequest("  Revise Unit 2  ", "2026-09-22", null));

        assertThat(created.getTitle()).isEqualTo("Revise Unit 2");
        assertThat(created.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(created.getPriority()).isNull();
        assertThat(created.getPosition()).isEqualTo(3072);
        assertThat(created.getUserId()).isEqualTo("u1");
        assertThat(created.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void createRejectsImpossibleDate() {
        Throwable thrown = catchThrowable(
                () -> taskService.createTask("u1", new TaskRequest("Read", "2026-02-30", null)));

        assertApiError(thrown, HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        verify(taskRepository, never()).save(any());
    }

    @Test
    void createRejectsBlankOrLongTitle() {
        assertApiError(catchThrowable(() -> taskService.createTask("u1", new TaskRequest(" ", "2026-09-22", null))),
                HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        assertApiError(catchThrowable(() -> taskService.createTask("u1", new TaskRequest("x".repeat(201), "2026-09-22", null))),
                HttpStatus.BAD_REQUEST, "BAD_REQUEST");
    }

    @Test
    void updateCanClearPriority() {
        Task existing = task("t1", TaskStatus.TODO, 1024);
        existing.setPriority(TaskPriority.HIGH);
        givenTask(existing);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task updated = taskService.updateTask("u1", "t1", new TaskRequest("New title", "2026-09-23", null));

        assertThat(updated.getPriority()).isNull();
        assertThat(updated.getTaskDate()).isEqualTo("2026-09-23");
    }

    @Test
    void anotherStudentsTaskIsNotFound() {
        Task someoneElses = task("t9", TaskStatus.TODO, 1024);
        someoneElses.setUserId("u2");
        givenTask(someoneElses);

        assertApiError(catchThrowable(() -> taskService.deleteTask("u1", "t9")), HttpStatus.NOT_FOUND, "NOT_FOUND");
        verify(taskRepository, never()).delete(any());
    }

    @Test
    void moveBetweenTwoNeighbours() {
        givenTask(task("x", TaskStatus.TODO, 1024));
        givenColumn(TaskStatus.IN_PROGRESS,
                task("a", TaskStatus.IN_PROGRESS, 1024), task("b", TaskStatus.IN_PROGRESS, 2048));

        Task moved = move("x", TaskStatus.IN_PROGRESS, "a", "b");

        assertThat(moved.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(moved.getPosition()).isEqualTo(1536);
    }

    @Test
    void moveKeepsTheOrderOfTasksHiddenByTheFilter() {
        // "h" is dated another day, so the student saw a and b next to each other
        givenTask(task("x", TaskStatus.TODO, 1024));
        givenColumn(TaskStatus.IN_PROGRESS, task("a", TaskStatus.IN_PROGRESS, 1024),
                task("h", TaskStatus.IN_PROGRESS, 2048), task("b", TaskStatus.IN_PROGRESS, 3072));

        Task moved = move("x", TaskStatus.IN_PROGRESS, "a", "b");

        assertThat(moved.getPosition()).isGreaterThan(1024).isLessThan(2048);
    }

    @Test
    void moveIntoAnEmptyColumnOrToTheEnd() {
        givenTask(task("x", TaskStatus.TODO, 1024));
        givenColumn(TaskStatus.DONE);
        assertThat(move("x", TaskStatus.DONE, null, null).getPosition()).isEqualTo(1024);

        givenTask(task("y", TaskStatus.TODO, 1024));
        givenColumn(TaskStatus.IN_PROGRESS, task("a", TaskStatus.IN_PROGRESS, 5000));
        assertThat(move("y", TaskStatus.IN_PROGRESS, null, null).getPosition()).isEqualTo(6024);
    }

    @Test
    void moveToTheTopBeforeTheFirstTask() {
        givenTask(task("x", TaskStatus.TODO, 1024));
        givenColumn(TaskStatus.IN_PROGRESS, task("a", TaskStatus.IN_PROGRESS, 1024));

        assertThat(move("x", TaskStatus.IN_PROGRESS, null, "a").getPosition()).isEqualTo(0);
    }

    @Test
    void moveWithinTheSameColumnIgnoresTheTaskItself() {
        Task x = task("x", TaskStatus.TODO, 1024);
        givenTask(x);
        givenColumn(TaskStatus.TODO, x, task("a", TaskStatus.TODO, 2048), task("b", TaskStatus.TODO, 3072));

        assertThat(move("x", TaskStatus.TODO, "a", "b").getPosition()).isEqualTo(2560);
    }

    @Test
    void moveRejectsNeighboursOutsideTheDestinationColumn() {
        givenTask(task("x", TaskStatus.TODO, 1024));
        // "z" might be in another column or belong to another student: either way it isn't here
        givenColumn(TaskStatus.IN_PROGRESS, task("a", TaskStatus.IN_PROGRESS, 1024));

        Throwable thrown = catchThrowable(() -> move("x", TaskStatus.IN_PROGRESS, "z", null));

        assertApiError(thrown, HttpStatus.BAD_REQUEST, "INVALID_POSITION");
        verify(taskRepository, never()).save(any());
    }

    @Test
    void moveRejectsNeighboursInTheWrongOrderOrItself() {
        givenTask(task("x", TaskStatus.TODO, 1024));
        givenColumn(TaskStatus.IN_PROGRESS,
                task("a", TaskStatus.IN_PROGRESS, 1024), task("b", TaskStatus.IN_PROGRESS, 2048));

        assertApiError(catchThrowable(() -> move("x", TaskStatus.IN_PROGRESS, "b", "a")),
                HttpStatus.BAD_REQUEST, "INVALID_POSITION");
        assertApiError(catchThrowable(() -> move("x", TaskStatus.IN_PROGRESS, "x", null)),
                HttpStatus.BAD_REQUEST, "INVALID_POSITION");
    }

    @Test
    @SuppressWarnings("unchecked")
    void moveRenumbersTheColumnWhenThereIsNoRoomLeft() {
        Task a = task("a", TaskStatus.TODO, 1.0);
        Task b = task("b", TaskStatus.TODO, 1.0 + 1e-7);
        givenTask(task("x", TaskStatus.DONE, 1024));
        givenColumn(TaskStatus.TODO, a, b);

        Task moved = move("x", TaskStatus.TODO, "a", "b");

        ArgumentCaptor<List<Task>> renumbered = ArgumentCaptor.forClass(List.class);
        verify(taskRepository).saveAll(renumbered.capture());
        assertThat(renumbered.getValue()).extracting(Task::getPosition).containsExactly(1024.0, 2048.0);
        assertThat(moved.getPosition()).isEqualTo(1536);
    }

    @Test
    void doneSetsCompletedAtAndLeavingDoneClearsIt() {
        givenTask(task("x", TaskStatus.IN_PROGRESS, 1024));
        givenColumn(TaskStatus.DONE);
        Task done = move("x", TaskStatus.DONE, null, null);
        assertThat(done.getCompletedAt()).isEqualTo(NOW);

        givenTask(done);
        givenColumn(TaskStatus.TODO);
        assertThat(move("x", TaskStatus.TODO, null, null).getCompletedAt()).isNull();
    }

    @Test
    void getTasksUsesInclusiveDatesAndOpenEnds() {
        taskService.getTasks("u1", "2026-09-22", "2026-09-22");
        verify(taskRepository).findByUserIdAndTaskDateBetween(eq("u1"),
                eq(Range.closed("2026-09-22", "2026-09-22")), any(Sort.class));

        taskService.getTasks("u1", "2026-09-23", null);
        verify(taskRepository).findByUserIdAndTaskDateBetween(eq("u1"),
                eq(Range.rightUnbounded(Range.Bound.inclusive("2026-09-23"))), any(Sort.class));

        taskService.getTasks("u1", null, null);
        verify(taskRepository).findByUserId(eq("u1"), any(Sort.class));
    }

    @Test
    void getTasksRejectsImpossibleFilterDates() {
        assertApiError(catchThrowable(() -> taskService.getTasks("u1", "2026-13-01", null)),
                HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        verify(taskRepository, never()).findByUserIdAndTaskDateBetween(any(), any(), any());
        verify(taskRepository, never()).saveAll(anyList());
    }
}
