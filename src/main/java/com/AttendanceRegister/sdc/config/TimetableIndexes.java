package com.AttendanceRegister.sdc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import com.AttendanceRegister.sdc.model.TimetablePreference;

// Keeps a student's timetable preference to a single document, so the active mode is
// always one value even if two requests race to create it. Creating an existing index does nothing.
@Component
public class TimetableIndexes implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TimetableIndexes.class);

    private final MongoTemplate mongoTemplate;

    public TimetableIndexes(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        Index onePreferencePerStudent = new Index()
                .on("userId", Sort.Direction.ASC)
                .unique()
                .named("one_timetable_preference_per_user");
        try {
            mongoTemplate.indexOps(TimetablePreference.class).createIndex(onePreferencePerStudent);
        } catch (RuntimeException ex) {
            log.error("Could not create the one-preference-per-user index on timetable preferences", ex);
        }
    }
}
