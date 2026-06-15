package com.payflow.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.payflow.entity.User;
import com.payflow.service.UserService;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping
    public User createUser(@RequestBody User user) {
        System.out.println("With @RequestBody -> name=" + user.getName() + ", upiId=" + user.getUpiId());
        return userService.registerUser(user);
    }

    @GetMapping
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/{id}")
    public User getUserById(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    @GetMapping("/upi/{upiId}")
    public User getUserByUpiId(@PathVariable String upiId) {
        return userService.findByUpiId(upiId);
    }

    // Same payload, but without @RequestBody Spring never binds the JSON,
    // so every field comes back null. Used for the Task 5.4 write-up.
    @PostMapping("/without-requestbody")
    public User createUserWithoutBody(User user) {
        System.out.println("Without @RequestBody -> name=" + user.getName() + ", upiId=" + user.getUpiId());
        return user;
    }
}
