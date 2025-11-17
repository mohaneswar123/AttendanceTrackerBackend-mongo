package com.AttendanceRegister.sdc.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDate;

@Document(collection = "user_table")
public class User {

    @Id
    private String id;

    private String username;

    private String password;

    private String email;

    // 👉 NEW subscription fields
    private boolean active = false;     // false by default
    private LocalDate paidTill;         // date until access is allowed

    // 👉 Constructors
    public User() {}

    public User(String username, String password, String email) {
        this.username = username;
        this.password = password;
        this.email = email;
    }

    // 👉 Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    // 👉 NEW getters/setters
    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDate getPaidTill() {
        return paidTill;
    }

    public void setPaidTill(LocalDate paidTill) {
        this.paidTill = paidTill;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", email='" + email + '\'' +
                ", active=" + active +
                ", paidTill=" + paidTill +
                '}';
    }
}
