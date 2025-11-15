package com.AttendanceRegister.sdc.dto;

//LoginRequest.java

public class LoginRequest {
 private String email;
 private String password;
public String getEmail() {
	return email;
}
public void setEmail(String email) {
	this.email = email;
}
public String getPassword() {
	return password;
}
public void setPassword(String password) {
	this.password = password;
}
@Override
public String toString() {
	return "LoginRequest [email=" + email + ", password=" + password + ", getEmail()=" + getEmail() + ", getPassword()="
			+ getPassword() + ", getClass()=" + getClass() + ", hashCode()=" + hashCode() + ", toString()="
			+ super.toString() + "]";
}

 // Getters & Setters
 
}

