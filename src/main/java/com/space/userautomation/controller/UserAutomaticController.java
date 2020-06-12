package com.space.userautomation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.space.userautomation.common.LoggerEnum;
import com.space.userautomation.common.ProjectLogger;
import com.space.userautomation.common.Response;
import com.space.userautomation.model.User;
import com.space.userautomation.services.ExcelReader;
import com.space.userautomation.services.UserService;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/usersubmission/user")
public class UserAutomaticController {

    @Autowired
    UserService userService;
    ExcelReader excelReader;
    Response response = new Response();

    @RequestMapping(value = "/v2/create", method = RequestMethod.POST)
    public ResponseEntity<?> createUser(@RequestBody User userDTO) {
        ProjectLogger.log("UserAutomation Create User Api called.", LoggerEnum.INFO.name());
        try {
            return userService.createNewUser(userDTO);
        } catch (Exception ex) {
            ex.printStackTrace();
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    //Retrieve All Users
    @RequestMapping(value = "/v1/users", method = RequestMethod.GET)
    public ResponseEntity<?> listAllUsers() {
        ProjectLogger.log("UserAutomation getUsers Api called.", LoggerEnum.INFO.name());
            return userService.userList();
    }
    
    @RequestMapping(value = "/v1/uploadFile", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<JSONObject> uploadFile(@RequestParam("DataSheet") MultipartFile file) {
        ProjectLogger.log("upload the file : ", LoggerEnum.INFO.name());
        try {
            if(!file.getOriginalFilename().isEmpty()){
                Map<String, Object> allData = excelReader.readExcelSheet(file.getInputStream(), file.getOriginalFilename());
                ProjectLogger.log("read the file : " + file.getOriginalFilename(), LoggerEnum.INFO.name());
                List<String> headers = (List<String>) allData.get("header");
                List<List<String>> content = (List<List<String>>) allData.get("data");
                List<User> userList = new ArrayList<>();
                ObjectMapper mapper = new ObjectMapper();
                for (List<String> contentEach : content) {
                    Map<String, String> userData = new HashMap<>();
                    for (int i = 0; i < contentEach.size(); i++) {
                        userData.put(headers.get(i), contentEach.get(i));
                    }
                    User user = mapper.convertValue(userData, User.class);
                    userList.add(user);
                }
                for (User user : userList) {
                    ResponseEntity<JSONObject> response = userService.createNewUser(user);
                }
                return response.getResponse("User details", HttpStatus.OK, 200, "", userList);
            }else{
                return response.getResponse("File not found", HttpStatus.BAD_REQUEST, 400, "", "");
            }
        }
        catch (Exception ex) {
            ProjectLogger.log("Exception occured in uploadFile method", LoggerEnum.INFO.name());
            return response.getResponse("Users could not be uploaded", HttpStatus.BAD_REQUEST, 400, "", "");
        }
    }
}