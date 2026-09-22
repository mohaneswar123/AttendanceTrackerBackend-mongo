package com.AttendanceRegister.sdc.security;

import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.AttendanceRegister.sdc.exception.ApiException;
import com.AttendanceRegister.sdc.model.Admin;
import com.AttendanceRegister.sdc.model.User;

// Passwords used to be stored in plain text. On startup, replace any that are
// left with their BCrypt hash. Does nothing once every password is hashed.
@Component
public class LegacyPasswordMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyPasswordMigration.class);

    private final MongoTemplate mongoTemplate;
    private final PasswordHasher passwordHasher;

    public LegacyPasswordMigration(MongoTemplate mongoTemplate, PasswordHasher passwordHasher) {
        this.mongoTemplate = mongoTemplate;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public void run(ApplicationArguments args) {
        long users = hashPlainPasswords(User.class, User::getId, User::getPassword);
        long admins = hashPlainPasswords(Admin.class, Admin::getId, Admin::getPassword);
        if (users + admins > 0) {
            log.info("Hashed {} user and {} admin passwords that were stored in plain text", users, admins);
        }
    }

    private <T> long hashPlainPasswords(Class<T> type, Function<T, String> id, Function<T, String> password) {
        Query plainText = Query.query(Criteria.where("password").exists(true).not().regex("^\\$2[aby]\\$"));
        long hashed = 0;
        for (T account : mongoTemplate.find(plainText, type)) {
            String stored = password.apply(account);
            if (stored == null) {
                continue;
            }
            String hash;
            try {
                hash = passwordHasher.hash(stored);
            } catch (ApiException ex) {
                log.warn("Left the password of {} {} as is: {}", type.getSimpleName(), id.apply(account), ex.getMessage());
                continue;
            }
            // Only replace the value we read, in case it changed in the meantime.
            Query unchanged = Query.query(Criteria.where("_id").is(id.apply(account)).and("password").is(stored));
            hashed += mongoTemplate.updateFirst(unchanged, Update.update("password", hash), type).getModifiedCount();
        }
        return hashed;
    }
}
