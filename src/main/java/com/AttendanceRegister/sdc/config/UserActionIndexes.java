package com.AttendanceRegister.sdc.config;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import com.AttendanceRegister.sdc.model.UserAction;
import com.AttendanceRegister.sdc.service.UserAuditService;

/**
 * The audit log is always read as "this account, newest first", and it has to stop growing
 * on its own, so the database expires old entries rather than any clean-up job.
 * Creating an index that already exists does nothing.
 */
@Component
public class UserActionIndexes implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserActionIndexes.class);

    private final MongoTemplate mongoTemplate;

    public UserActionIndexes(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            mongoTemplate.indexOps(UserAction.class).createIndex(new Index()
                    .on("userId", Sort.Direction.ASC)
                    .on("at", Sort.Direction.DESC)
                    .named("user_actions_newest_first"));

            mongoTemplate.indexOps(UserAction.class).createIndex(new Index()
                    .on("at", Sort.Direction.ASC)
                    .expire(Duration.ofDays(UserAuditService.KEEP_FOR_DAYS))
                    .named("user_actions_expire"));
        } catch (RuntimeException ex) {
            // Reading still works without them; it is just slower, and nothing expires
            log.error("Could not create the user audit indexes", ex);
        }
    }
}
