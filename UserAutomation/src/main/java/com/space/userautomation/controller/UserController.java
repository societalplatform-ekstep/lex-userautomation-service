package com.space.userautomation.controller;

import com.space.userautomation.model.User;
import com.space.userautomation.model.UserCredentials;
import com.space.userautomation.services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class UserController {
    public static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    UserService userService;

    @RequestMapping(value = "/token", method = RequestMethod.POST)
    public ResponseEntity<?> getTokenUsingCredentials(@RequestBody UserCredentials userCredentials) {


        String responseToken = null;
        try {
            System.out.println("token created:" +userCredentials );
            responseToken = userService.getToken(userCredentials);

        } catch (Exception e) {
            System.out.println("exception occured");

            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity<>(responseToken, HttpStatus.OK);

    }

    @RequestMapping(value = "/user", method = RequestMethod.POST)
    public ResponseEntity<?> createUser(@RequestBody User userDTO) {
        logger.info("Creating User : {}", userDTO);
        try {
            System.out.println("USERS created:" + userDTO);
            userService.createUser(userDTO);
            return new ResponseEntity<>(HttpStatus.OK);
        }

        catch (Exception ex) {

            ex.printStackTrace();
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);

        }

    }
}