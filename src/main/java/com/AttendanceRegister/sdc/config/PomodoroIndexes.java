package com.AttendanceRegister.sdc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

import com.AttendanceRegister.sdc.model.PomodoroSession;

// Lets the database itself refuse a second ACTIVE focus session for the same student,
// even when two start requests arrive at once. Creating an existing index does nothing.
@Component
public class PomodoroIndexes implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PomodoroIndexes.class);

    private final MongoTemplate mongoTemplate;

    public PomodoroIndexes(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        Index oneActivePerStudent = new Index()
                .on("userId", Sort.Direction.ASC)
                .unique()
                .partial(PartialIndexFilter.of(Criteria.where("status").is("ACTIVE")))
                .named("one_active_session_per_user");
        try {
            mongoTemplate.indexOps(PomodoroSession.class).createIndex(oneActivePerStudent);
        } catch (RuntimeException ex) {
            // Starting still checks for a running session first, so the app can carry on
            log.error("Could not create the one-active-session index on Pomodoro sessions", ex);
        }
    }
}
