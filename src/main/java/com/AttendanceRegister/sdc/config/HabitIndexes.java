package com.AttendanceRegister.sdc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import com.AttendanceRegister.sdc.model.HabitEntry;

/**
 * Lets the database itself refuse a second mark on the same habit and day, even when two
 * taps arrive at once, so a day can never hold two contradictory answers.
 * Creating an index that already exists does nothing.
 */
@Component
public class HabitIndexes implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HabitIndexes.class);

    private final MongoTemplate mongoTemplate;

    public HabitIndexes(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            mongoTemplate.indexOps(HabitEntry.class).createIndex(new Index()
                    .on("habitId", Sort.Direction.ASC)
                    .on("date", Sort.Direction.ASC)
                    .unique()
                    .named("one_mark_per_habit_per_day"));

            mongoTemplate.indexOps(HabitEntry.class).createIndex(new Index()
                    .on("userId", Sort.Direction.ASC)
                    .on("date", Sort.Direction.ASC)
                    .named("habit_entries_by_day"));
        } catch (RuntimeException ex) {
            // Marking still looks for an existing entry first, so the app can carry on
            log.error("Could not create the habit entry indexes", ex);
        }
    }
}
