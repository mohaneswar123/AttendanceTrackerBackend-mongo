package com.AttendanceRegister.sdc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Pageable;

import com.AttendanceRegister.sdc.Repository.UserActionRepository;
import com.AttendanceRegister.sdc.model.UserAction;
import com.AttendanceRegister.sdc.model.UserActionFeature;
import com.AttendanceRegister.sdc.model.UserActionType;

@ExtendWith(MockitoExtension.class)
class UserAuditServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T09:30:00Z");

    @Mock
    private UserActionRepository repository;

    private UserAuditService service;

    @BeforeEach
    void setUp() {
        service = new UserAuditService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static UserAction entry(UserActionFeature feature, UserActionType action) {
        return new UserAction("u1", feature, action, false, NOW);
    }

    @Test
    void recordsWhatHappenedAndWhen() {
        service.record("u1", UserActionFeature.TASK, UserActionType.CREATE, false);

        ArgumentCaptor<UserAction> saved = ArgumentCaptor.forClass(UserAction.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo("u1");
        assertThat(saved.getValue().getFeature()).isEqualTo(UserActionFeature.TASK);
        assertThat(saved.getValue().getAction()).isEqualTo(UserActionType.CREATE);
        assertThat(saved.getValue().isByAdmin()).isFalse();
        assertThat(saved.getValue().getAt()).isEqualTo(NOW);
    }

    @Test
    void saysWhenAnAdminActedOnTheStudentsBehalf() {
        service.record("u1", UserActionFeature.ATTENDANCE, UserActionType.DELETE, true);

        ArgumentCaptor<UserAction> saved = ArgumentCaptor.forClass(UserAction.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().isByAdmin()).isTrue();
    }

    @Test
    void keepsNothingAboutWhatTheStudentWrote() {
        service.record("u1", UserActionFeature.CALENDAR, UserActionType.CREATE, false);

        ArgumentCaptor<UserAction> saved = ArgumentCaptor.forClass(UserAction.class);
        verify(repository).save(saved.capture());
        // The whole entry is five fields; none of them can hold a title or a note
        assertThat(UserAction.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .containsExactlyInAnyOrder("id", "userId", "feature", "action", "byAdmin", "at");
    }

    @Test
    void aFailedWriteNeverBreaksTheActionBeingRecorded() {
        when(repository.save(any(UserAction.class))).thenThrow(new DataAccessResourceFailureException("mongo is down"));

        assertThatCode(() -> service.record("u1", UserActionFeature.FOCUS, UserActionType.CREATE, false))
                .doesNotThrowAnyException();
    }

    @Test
    void countsEachKindSeparately() {
        when(repository.findByUserId("u1")).thenReturn(List.of(
                entry(UserActionFeature.TASK, UserActionType.CREATE),
                entry(UserActionFeature.TASK, UserActionType.CREATE),
                entry(UserActionFeature.TASK, UserActionType.DELETE),
                entry(UserActionFeature.FOCUS, UserActionType.CREATE)));

        Map<UserActionFeature, Map<UserActionType, Long>> counts = service.counts("u1");

        assertThat(counts.get(UserActionFeature.TASK))
                .containsEntry(UserActionType.CREATE, 2L)
                .containsEntry(UserActionType.DELETE, 1L);
        assertThat(counts.get(UserActionFeature.FOCUS)).containsEntry(UserActionType.CREATE, 1L);
        // Features with nothing against them are left out rather than sent as zeroes
        assertThat(counts).doesNotContainKey(UserActionFeature.CALENDAR);
    }

    @Test
    void anAccountThatHasDoneNothingCountsAsEmpty() {
        when(repository.findByUserId("u1")).thenReturn(List.of());

        assertThat(service.counts("u1")).isEmpty();
    }

    @Test
    void asksForAtMostTheCapNoMatterWhatIsRequested() {
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);

        service.forUser("u1", 10_000);
        service.forUser("u1", 0);

        verify(repository, org.mockito.Mockito.times(2)).findByUserIdOrderByAtDesc(any(), page.capture());
        assertThat(page.getAllValues().get(0).getPageSize()).isEqualTo(UserAuditService.MAX_ENTRIES);
        assertThat(page.getAllValues().get(1).getPageSize()).isEqualTo(1);
    }
}
