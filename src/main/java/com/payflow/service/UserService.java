package com.payflow.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.payflow.entity.User;
import com.payflow.repository.UserRepository;

@Service
public class UserService {

    // Spring creates a UserRepository proxy at startup and injects it here,
    // so we never call 'new' on it ourselves.
    @Autowired
    private UserRepository userRepository;

    public User registerUser(User user) {
        return userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    public User findByUpiId(String upiId) {
        return userRepository.findByUpiId(upiId);
    }

    public List<User> findRicherThan(Double amount) {
        return userRepository.findRicherThan(amount);
    }
}
