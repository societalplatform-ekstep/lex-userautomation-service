package com.space.userautomation.services;
import com.space.userautomation.common.LoggerEnum;
import com.space.userautomation.common.ProjectLogger;
import com.space.userautomation.common.Response;
import com.space.userautomation.common.UserAutomationEnum;
import com.space.userautomation.database.cassandra.Cassandra;
import com.space.userautomation.database.postgresql.Postgresql;
import com.space.userautomation.model.User;
import com.space.userautomation.model.UserCredentials;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

@Component
public class UserRoleService {
    
    Cassandra cassandra = new Cassandra();
    Response response = new Response();
    EmailService emailService = new EmailService();
    Postgresql postgresql = new Postgresql();
    UpdateUserInformation userInformation = new UpdateUserInformation();
    

    private String root_org = System.getenv("rootOrg");
    private String org = System.getenv("org");
    private String locale = System.getenv("locale");
    String roleForAdminUser = "org-admin";


//    List<String> oldUserRole = new ArrayList<>();
    
    static final String  alreadyPresent = "A";
    static final String notPresent = "N";
    static final String create = "C";
    static final String fixedRole= "F"; 

    public ResponseEntity<JSONObject> createUserRole(User userData) throws IOException {
        String userId = userData.getUser_id();
        try {
            ProjectLogger.log("createUserRole method is called" , LoggerEnum.INFO.name());
            JSONObject jObj = new JSONObject((Map) getRoleForAdmin(userData).getBody().get("DATA"));
            Boolean isORG_ADMIN = (Boolean) jObj.get("isAdminUser");
            if (isORG_ADMIN) {
                Map<String, Object> userDetails = new HashMap<>();
                String token = new String();
                UserCredentials userCredentials = new UserCredentials();
                userCredentials.setUsername(userId);
                userCredentials.setPassword(userData.getPassword());
                token =  new UserService().getToken(userCredentials);
                JSONParser parser = new JSONParser();
                JSONObject tokenJson = (JSONObject) parser.parse(token);
                String accessToken = tokenJson.get("access_token").toString();
                String wid =  getWidIdFromWToken(accessToken).get("wid").toString();
                userData.setUser_id(wid);
                cassandra.insertUser(userData.toMapUserRole());
                userDetails.put("WID" ,userData.getUser_id() );
                userDetails.put("Root_Org",userData.getRoot_org());
                userDetails.put("User_Roles" , userData.getRole());
                return response.getResponse("User Role assigned successfully", HttpStatus.OK, UserAutomationEnum.SUCCESS_RESPONSE_STATUS_CODE, userData.getApiId(), userDetails);
            }
            else{
                return response.getResponse("User role cannot be assigned", HttpStatus.BAD_REQUEST, UserAutomationEnum.BAD_REQUEST_STATUS_CODE, userData.getApiId(), "");
            }
        }
        catch(Exception ex){
            return response.getResponse(ex.getMessage(), HttpStatus.BAD_REQUEST, UserAutomationEnum.INTERNAL_SERVER_ERROR, userData.getApiId(),"");
        }
    }

    public Map<String, Object> getWidIdFromWToken(String accessToken) throws IOException {
        Map<String, Object>  responseFromWid = new HashMap<>();
         String userId = new String();
        try {
            ProjectLogger.log("getWidIdFromWToken method is called" , LoggerEnum.INFO.name());
            CloseableHttpClient httpClient = HttpClientBuilder.create().build();
            HttpGet request = new HttpGet(System.getenv("productionUrl")+"apis/protected/v8/user/details/wtoken");
            request.setHeader("Authorization", "bearer " + accessToken);
            request.setHeader("rootOrg", root_org);
            request.setHeader("org", org);
            request.setHeader("locale", locale);
            ProjectLogger.log("Request sent for wtoken api" + userId, LoggerEnum.INFO.name());
            HttpResponse response = httpClient.execute(request);
            int statusId = response.getStatusLine().getStatusCode();
            ProjectLogger.log("Status Id for wtoken api : " + statusId, LoggerEnum.INFO.name());
            String responseData = EntityUtils.toString(response.getEntity());
            ProjectLogger.log("Response from wtoken api : " + responseData, LoggerEnum.INFO.name());
            if (statusId == UserAutomationEnum.SUCCESS_RESPONSE_STATUS_CODE) {
                JSONParser parser = new JSONParser();
                JSONObject jsonObj = (JSONObject) parser.parse(responseData);
                JSONObject userDetails = (JSONObject) jsonObj.get("user");
                userId = userDetails.get("wid").toString();
                ProjectLogger.log("UserId retrieved from WToken " + userId, LoggerEnum.INFO.name());
                responseFromWid.put("wid", userId);
                responseFromWid.put("status_code",statusId);
                return responseFromWid;
            }
            if(statusId == UserAutomationEnum.INTERNAL_SERVER_ERROR){
                ProjectLogger.log("Internal server error from wtoken api ", LoggerEnum.ERROR.name());
                responseFromWid.put("wid", "");
                responseFromWid.put("status_code",statusId);
                return responseFromWid;
            }
            else {
                ProjectLogger.log("Failed to fetch wid from wtoken api. ", LoggerEnum.ERROR.name());
                responseFromWid.put("wid", "");
                responseFromWid.put("status_code",statusId);
                return responseFromWid;
            }
        } catch (Exception ex) {
            ProjectLogger.log("Exception " + ex  + Arrays.toString(ex.getStackTrace()) + "while retieving userId from wtoken api ", LoggerEnum.INFO.name());
        }
        return responseFromWid;
    }

    public ResponseEntity<JSONObject> getRoleForAdmin(User userDetailsForRoles){
        try {
            Map<String,Boolean> roles = new HashMap<String, Boolean>();
            ProjectLogger.log("wid for admin role" + userDetailsForRoles.getWid_OrgAdmin(), LoggerEnum.INFO.name());
            userDetailsForRoles.setUser_id(userDetailsForRoles.getWid_OrgAdmin());
            List<String> userRoles =  new Postgresql().getUserRoles(userDetailsForRoles.toMapUserRole());
            if(getSpecificRole(userRoles)){
                roles.put("isAdminUser",true);
                return response.getResponse("org admin role", HttpStatus.FOUND, UserAutomationEnum.SUCCESS_RESPONSE_STATUS_CODE, userDetailsForRoles.getApiId(),roles);
            }
            else{
                roles.put("isAdminUser",false);
                return response.getResponse("org admin role not found ", HttpStatus.NOT_FOUND, UserAutomationEnum.BAD_REQUEST_STATUS_CODE, userDetailsForRoles.getApiId(),roles);
            }
        }
        catch(Exception ex){
            ProjectLogger.log("Exception occured  in getRoleForAdmin method "+ ex + Arrays.toString(ex.getStackTrace()) , LoggerEnum.ERROR.name());
            return response.getResponse(ex.getMessage(), HttpStatus.BAD_REQUEST, UserAutomationEnum.INTERNAL_SERVER_ERROR, userDetailsForRoles.getApiId(),"");
        }
    }

    public boolean getSpecificRole(List<String> userRoles){
        if(userRoles.contains(roleForAdminUser)){
            return true;
        }
        else{
            return false;
        }
    }

    public ResponseEntity<JSONObject> getAcceptedUser(User userDetails){
        //Fetching user data
        String apiId = userDetails.getApiId();
        String userName = userDetails.getUsername();
//        String updatedPassword = userDetails.getPassword();
        try{
            Map<String, Object> userResponse = new HashMap<>();
            ResponseEntity<JSONObject> responseEntity = userInformation.intializationRequest(userDetails);
            ProjectLogger.log("Data from getAcceptedUser user from  intialization Request method"+ responseEntity , LoggerEnum.ERROR.name());
            JSONObject jsonData = new JSONObject((Map) responseEntity.getBody().get("DATA"));
            JSONObject enabeldetails = (JSONObject) jsonData.get("enableDetails");
            Boolean isEnable = (Boolean) enabeldetails.get("Enabled");
            JSONObject passwordDetails = (JSONObject) jsonData.get("passwordDetails");
            String updatedPassword = (String) passwordDetails.get("password");
            Boolean isPasswordSet = (Boolean) passwordDetails.get("updatedPassword");
            if(isEnable && isPasswordSet) {

                //Retreive the user roles of admin user.
                JSONObject jObjForUserRole = new JSONObject((Map) getRoleForAdmin(userDetails).getBody().get("DATA"));

                //Check if role of ADMIN user is ORG_ADMIN.
                Boolean isORG_ADMIN = (Boolean) jObjForUserRole.get("isAdminUser");
                if (isORG_ADMIN) {

                    //Create token for  new user.
                    String token = new String();
                    UserCredentials userCredentials = new UserCredentials();
                    userCredentials.setUsername(userName);
                    userCredentials.setPassword(updatedPassword);
                    token = new UserService().getToken(userCredentials);
                    JSONParser parser = new JSONParser();
                    JSONObject tokenJson = (JSONObject) parser.parse(token);
                    String accessToken = tokenJson.get("access_token").toString();

                    //retrieve wid of the new  user from token genserated.
                    Map<String,Object> responseFromWtoken = getWidIdFromWToken(accessToken);

                    String widForNewUser = responseFromWtoken.get("wid").toString();
                    Integer responseStatusCode = (Integer) responseFromWtoken.get("status_code");
                    if(!widForNewUser.isEmpty() && (responseStatusCode == UserAutomationEnum.SUCCESS_RESPONSE_STATUS_CODE)) {
                        //assign roles for the new user from requested role to cassandra
                        userDetails.setUser_id(widForNewUser);
                        ResponseEntity<JSONObject> responseData = cassandra.insertUser(userDetails.toMapUserRole());
                        Integer statusCode = (Integer) responseData.getBody().get("STATUS_CODE");
                        if (statusCode == UserAutomationEnum.SUCCESS_RESPONSE_STATUS_CODE) {
                            //send email to new user with user credentials of user and platform link.
                            emailService.acceptmail(userName, updatedPassword, userDetails.getOrganisation());

                            //return the response for the user role assigned.
                            userResponse.put("WID", widForNewUser);
                            userResponse.put("Root_Org", userDetails.getRoot_org());
                            userResponse.put("User_Roles", userDetails.getRole());
                            return response.getResponse("User Role assigned successfully", HttpStatus.OK, UserAutomationEnum.SUCCESS_RESPONSE_STATUS_CODE, apiId, userResponse);
                        } else {
                            return response.getResponse("User could not be accepted", HttpStatus.BAD_REQUEST, UserAutomationEnum.BAD_REQUEST_STATUS_CODE, apiId, userResponse);
                        }
                    }else{
                        ProjectLogger.log("Wid was not fetched from wtoken api ", LoggerEnum.INFO.name());
                        return response.getResponse("User role couldn't be assigned, failed to fetch wid from wtoken.Please try again", HttpStatus.BAD_REQUEST, UserAutomationEnum.BAD_REQUEST_STATUS_CODE, apiId, userResponse);
                    }
                } else {
                    return response.getResponse("Permission denaid, user role can be assigned by admin user only.", HttpStatus.FORBIDDEN, UserAutomationEnum.FORBIDDEN, apiId, "");
                }
            } else{
                return response.getResponse("User is not enabled and password is not updated", HttpStatus.BAD_REQUEST, UserAutomationEnum.BAD_REQUEST_STATUS_CODE, apiId, "");
            }
        }
        catch(Exception ex){
            ProjectLogger.log("Internal Server Exception for accepting user roles "+ ex , LoggerEnum.ERROR.name());
            return response.getResponse(ex.getMessage(), HttpStatus.BAD_REQUEST, UserAutomationEnum.INTERNAL_SERVER_ERROR, apiId,"");
        }
    }
    public ResponseEntity<JSONObject> getAllRoles(User userData) {
        try {
            JSONObject jObj = new JSONObject((Map) getRoleForAdmin(userData).getBody().get("DATA"));
            Boolean isORG_ADMIN = (Boolean) jObj.get("isAdminUser");
            if (isORG_ADMIN) {
                userData.setUser_id("external_user_roles");
               List<String> userRoles =  new Postgresql().getUserRoles(userData.toMapUserRole());
                return response.getResponse("roles of users", HttpStatus.OK, 200, "", userRoles);
            } else {
                return response.getResponse("Permission denied,user role can be retireved by admin only", HttpStatus.FORBIDDEN, UserAutomationEnum.FORBIDDEN, "", "");
            }
        } catch (Exception ex) {
            ProjectLogger.log("Exception occured in getAllRoles" +  Arrays.toString(ex.getStackTrace()) + ex, LoggerEnum.ERROR.name());
            return response.getResponse(ex.getMessage(), HttpStatus.BAD_REQUEST, 500, userData.getApiId(), "");
        }
    }

    public ResponseEntity<JSONObject> changeRole(User userData) {
        Map<String, Object> userResponse = new HashMap<>();
      
        try {
            if((!userData.getWid().isEmpty() && userData.getWid() != null) &&(!userData.getRoles().isEmpty()) && (!userData.getName().isEmpty() && userData.getName() != null) &&(!userData.getEmail().isEmpty() && userData.getEmail() != null)) {
                //validate for the admin user role
                JSONObject jObj = new JSONObject((Map) getRoleForAdmin(userData).getBody().get("DATA"));
                Boolean isORG_ADMIN = (Boolean) jObj.get("isAdminUser");
                if (isORG_ADMIN) {
                    //get the external user roles 
//                    List<String> externalUserRoles = getExternalUserRoles(userData);
                    userData.setUser_id(userData.getWid());
                    Timestamp timestamp = new Postgresql().getTimestampValue();
                    userData.setUpdated_on(timestamp);
                    userData.setUpdated_by(userData.getWid_OrgAdmin());
                    List<String> oldUserRoles =  getOldUserRoles(userData);
                    List<String> externalUserRoles = getExternalUserRoles(userData);
                    //validate if the roles provided is existing in master roles    
                    if (validateUserFromMasterRoles(userData)) {
                     
                        userData.setUser_id(userData.getWid());
                        //update the roles for the user
                        Map<String, Object> responseMap = validateAndUpdateRoles(userData, oldUserRoles, externalUserRoles);
                        Integer statusCodeList = (Integer) responseMap.get("statusCode");
                        userResponse.put("email",userData.getEmail());
                        userResponse.put("wid",userData.getWid());
                        if (statusCodeList == 200) {
                            List<String> userExistingRoles =  new Postgresql().getUserRoles(userData.toMapUserRole());
                            emailService.changeRole(userData, userExistingRoles, (List<String>) oldUserRoles);
//                            userResponse.put("User_Roles", responseMap.get("data"));
                            userResponse.put("User_Roles", userExistingRoles);
                            ProjectLogger.log("User Role updated successfully", LoggerEnum.INFO.name());
                            return response.getResponse("User Role updated successfully", HttpStatus.OK, UserAutomationEnum.SUCCESS_RESPONSE_STATUS_CODE, userData.getApiId(), userResponse);
                        } else {
                            userResponse.put("User_Roles", "");
                            ProjectLogger.log("Role could not be updated to the requested user, verify whether all roles insertion and deletion is success.", LoggerEnum.ERROR.name());
                            return response.getResponse("Role could not be updated,please try again.", HttpStatus.BAD_REQUEST, UserAutomationEnum.BAD_REQUEST_STATUS_CODE, userData.getApiId(), userResponse);
                        }
                    } else {
                        return response.getResponse("Roles can be assigned from master roles only,please verify the roles before inserting", HttpStatus.BAD_REQUEST, UserAutomationEnum.BAD_REQUEST_STATUS_CODE, userData.getApiId(), "");
                    }
                } else {
                    return response.getResponse("Permission denied,user role can be changed by admin user only", HttpStatus.BAD_REQUEST, UserAutomationEnum.BAD_REQUEST_STATUS_CODE, userData.getApiId(), "");
                }
            }else{
                 return response.getResponse("User role could not be updated,please provide appropriate params", HttpStatus.BAD_REQUEST, UserAutomationEnum.BAD_REQUEST_STATUS_CODE, userData.getApiId(), "");
            }
        } catch (Exception ex) {
            ProjectLogger.log("Exception occured while changing role "  + ex, LoggerEnum.ERROR.name());
            ProjectLogger.log( Arrays.toString(ex.getStackTrace()), LoggerEnum.ERROR.name());
            return response.getResponse(ex.getMessage(), HttpStatus.BAD_REQUEST, 500, userData.getApiId(), "");
        }
    }
    
    //updating the roles and validate if there is existing roles for the user, and update only required roles.
    public Map<String, Object> validateAndUpdateRoles(User userData, List<String> existingUserRoles, List<String> externalUserRoles) throws SQLException {
        Map<String, Object>  response = new HashMap<>();
        Map<Object, Object> allstatusCode = new HashMap<Object,Object>();
      Integer statusCode;
        //validate user role with existing roles.
        Map<Object, Object> flaggedRoles = validateUserRole(userData, existingUserRoles, externalUserRoles);
        List<String> responseData = updateRoles(flaggedRoles, userData, allstatusCode);
        if((allstatusCode.get("InsertionFailure") == (Integer) UserAutomationEnum.INTERNAL_SERVER_ERROR) || (allstatusCode.get("DeletionFailure") == (Integer) UserAutomationEnum.INTERNAL_SERVER_ERROR)){
            statusCode = UserAutomationEnum.INTERNAL_SERVER_ERROR;
            response.put("statusCode",statusCode );
            response.put("data",responseData);
        }
        else{
            statusCode = UserAutomationEnum.SUCCESS_RESPONSE_STATUS_CODE;
             response.put("statusCode",statusCode );  
             response.put("data", responseData);
        }
        return response;
     }
     
    // Validate already existing roles with the currently requested role
    public Map<Object,Object> validateUserRole(User userData, List<String> existingUserRoles, List<String> externalUserRoles) {
        Map<Object, Object>  requiredRoles = new HashMap<Object, Object>();
        List<String> existingRoles = new ArrayList<>();
        requiredRoles.clear();
        existingRoles.clear();
        
        // filter the user existing roles that has roles from external_user_role table
        for(String role:existingUserRoles ){
            for(String externalRole: externalUserRoles){
                if(role.equals(externalRole)){
                    existingRoles.add(role);
                }
            }
        }
        
        //assign the role to hashmap as already created
        for(String role: existingRoles) {
            if (role.equals(roleForAdminUser)) {
                requiredRoles.put(role, fixedRole);
            } else {
                requiredRoles.put(role, alreadyPresent);
            }
        }
        for (String role : userData.getRoles()) {
            if (!existingRoles.contains(role)) {
                requiredRoles.put(role, create);
            }
        }
        for (String role : existingRoles) {
            if (!userData.getRoles().contains(role)) {
                if (role.equals(roleForAdminUser)) {
                    requiredRoles.put(role, fixedRole);
                } else {
                    requiredRoles.put(role, notPresent);
                }
            }
        }
        return requiredRoles;
    }
    //get external roles
    public List<String> getExternalUserRoles(User userData){
        userData.setUser_id("external_user_roles");
        List<String> userRoles =  new Postgresql().getUserRoles(userData.toMapUserRole());
        return userRoles;
    }
    
    //get all existing user roles
    public List<String> getOldUserRoles(User userData){
        List<String> userRoles = new Postgresql().getUserRoles(userData.toMapUserRole());
        return userRoles;
    }
    

    
    public List<String> updateRoles(Map<Object,Object> flaggedRoles, User userData, Map<Object, Object> allstatusCode) throws SQLException {
          List<String> userRoles = new ArrayList<>();
          
        Iterator it = flaggedRoles.entrySet().iterator();
        while (it.hasNext()){
            Map.Entry item = (Map.Entry) it.next();
            String value = (String)item.getValue(); 
            String key = (String) item.getKey();
            if (value == create) {
                userData.setRole(key);
                ResponseEntity<JSONObject> responseData = new Postgresql().insertUserRoles(userData.toMapUserRole());
                Integer statusCode = (Integer) responseData.getBody().get("STATUS_CODE");
                if (statusCode == UserAutomationEnum.SUCCESS_RESPONSE_STATUS_CODE) {
                    userRoles.add(key);
                    allstatusCode.put("InsertionSuccess", UserAutomationEnum.SUCCESS_RESPONSE_STATUS_CODE);
                    continue;
                } else {
                    allstatusCode.put("InsertionFailure", UserAutomationEnum.INTERNAL_SERVER_ERROR);
                    ProjectLogger.log("user role already exists from the requested roles for user " + userData.getWid(), LoggerEnum.ERROR.name());
                    continue;
                }
            }
            if(value == notPresent){
                userData.setRole(key);
                ResponseEntity<JSONObject> responseData = new Postgresql().deleteUserRole(userData.toMapUserRole());
                Integer statusCode = (Integer) responseData.getBody().get("STATUS_CODE");
                if (statusCode == UserAutomationEnum.SUCCESS_RESPONSE_STATUS_CODE) {
                    allstatusCode.put("DeletionSuccess", UserAutomationEnum.SUCCESS_RESPONSE_STATUS_CODE);
                    ProjectLogger.log("User role deleted  successfully" + userData.getWid(), LoggerEnum.ERROR.name());
                    continue;
                } else {
                    allstatusCode.put("DeletionFailure", UserAutomationEnum.INTERNAL_SERVER_ERROR);
                    ProjectLogger.log("User role cannot be deleted " + userData.getWid(), LoggerEnum.ERROR.name());
                    continue;
                }
            }
            if(value == alreadyPresent || value == fixedRole){
                userRoles.add(key);
                allstatusCode.put("AllSuccess", UserAutomationEnum.SUCCESS_RESPONSE_STATUS_CODE);
                continue;
            }
           it.remove();
        }
       return  userRoles;
    }
    
    public List<String> newUserRoles(User user, List<String> existingRoles){
        Map<String, Object> mappedNewRoles =  assignAndMapRoles(user,existingRoles);
        List<String>  newListOfRoles = (List<String>) getKeysFromMappedRoles(mappedNewRoles);
        return newListOfRoles;
    }
    
    //getting keys from mapped roles and assigning to list of roles
    public Object getKeysFromMappedRoles(Map<String, Object> mappedRoles){
        List<String> listOfRoles = new ArrayList<>();
        for (Map.Entry<String, Object> entry : mappedRoles.entrySet()) {
            String key = entry.getKey();
            listOfRoles.add(key);
        }
        return listOfRoles;
    }


    //assigning the default roles for user
    public List<String> assignNewUserAsDefault(User user){
        List<String> defaultRoles = new ArrayList<>();
        user.setUser_id("Default");
        List<String> defaultRoleList = postgresql.getUserRoles(user.toMapUserRole());
        for(String role: defaultRoleList){
            defaultRoles.add(role);
        }
        return defaultRoles;
    }

    public  Map<String, Object> assignAndMapRoles(User user, List<String> existingRolesOfUser){
        //entry should be present with id of add_new_role and the id of that specific role
        //eg: admin_role has role name ="newrole1" and newRole1 has role name = "member"
        //The role name is shown in email template, so proper naming is to be provided in database.
        user.setUser_id("add_new_group");
        Map<String, Object> newMapOfRoles = new HashMap<>();
        List<String> addNewRoles = postgresql.getUserRoles(user.toMapUserRole());
        for(String role: addNewRoles){
            user.setUser_id(role);
           List<String> allRoles = postgresql.getUserRoles(user.toMapUserRole());
            if(allRoles.stream().anyMatch(t -> existingRolesOfUser.stream().anyMatch(t::contains))){
                newMapOfRoles.put(role,allRoles);
            }
        }
        return newMapOfRoles;
    }
    
    //validate for master roles
    public boolean validateUserFromMasterRoles(User userData){
        userData.setUser_id("external_user_roles");
        List<String> userRoles =  new Postgresql().getUserRoles(userData.toMapUserRole());
        for(String role: userData.getRoles()){
            if(!userRoles.contains(role)){
                return  false;
            }
        }
        return true;
    }
//    public ResponseEntity<JSONObject> getRoles(String user_id,User userDetailsForRoles) {
//        try {
//            JSONObject jObj = new JSONObject((Map) getRoleForAdmin(userDetailsForRoles).getBody().get("DATA"));
//            Boolean isORG_ADMIN = (Boolean) jObj.get("ORG_ADMIN");
//            if (isORG_ADMIN) {
//                List<String> userRoles = new ArrayList<>();
//                Map<String, Boolean> roles = new HashMap<String, Boolean>();
//                userDetailsForRoles.setUser_id(user_id);
//                List<User> userList = cassandra.getUserRoles(userDetailsForRoles.toMapUserRole());
////                for (User user : userList) {
////                    userRoles = user.getRoles();
////                }
//                return response.getResponse("roles of users", HttpStatus.OK, 200, "", userRoles);
//            }else {
//                return response.getResponse("Permission denied,user role can be retireved by admin only", HttpStatus.FORBIDDEN, UserAutomationEnum.FORBIDDEN, "", "");
//            }
//        } catch (Exception ex) {
//            ProjectLogger.log("Exception occured " + ex, LoggerEnum.ERROR.name());
//            return response.getResponse(ex.getMessage(), HttpStatus.BAD_REQUEST, 500, userDetailsForRoles.getApiId(), "");
//        }
//    }
}
