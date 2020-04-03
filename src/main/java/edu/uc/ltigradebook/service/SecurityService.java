package edu.uc.ltigradebook.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import edu.ksu.lti.launch.model.InstitutionRole;
import edu.uc.ltigradebook.entity.AccountPreference;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SecurityService {

   @Value("${lti-gradebook.admins:admin}")
   private String ltiGradebookAdmins;

   @Autowired
   private AccountService accountService;

   public boolean isAdminUser(String canvasLoginId, List<InstitutionRole> userRoles) {
       log.debug("The current admins of the application are {}.", ltiGradebookAdmins);
       // Better security check for admins
       // return userRoles.contains(InstitutionRole.Administrator) || (StringUtils.isNotEmpty(ltiGradebookAdmins) && ltiGradebookAdmins.contains(canvasLoginId));
       return StringUtils.isNotEmpty(ltiGradebookAdmins) && ltiGradebookAdmins.contains(canvasLoginId) && userRoles.contains(InstitutionRole.Administrator);
   }

   public boolean isFaculty(List<InstitutionRole> userRoles) {
       if(userRoles != null && !userRoles.isEmpty()) {
           return userRoles.contains(InstitutionRole.Administrator)
                    || userRoles.contains(InstitutionRole.Instructor)
                    || userRoles.contains(InstitutionRole.TeachingAssistant);
           }
       return false;
   }

   public boolean isStudent(List<InstitutionRole> userRoles) {
       if(userRoles != null && !userRoles.isEmpty()) {
           return userRoles.contains(InstitutionRole.Student)
                    || userRoles.contains(InstitutionRole.Learner);
           }
       return false;
   }

   public boolean isBannerEnabled(long accountId) {
       log.debug("Checking if banner is enabled for the accountId {}.", accountId);
       Optional<AccountPreference> optionalAccountPreference = accountService.getAccountPreferences(accountId);
       if(optionalAccountPreference.isPresent()) {
           AccountPreference accountPreference = optionalAccountPreference.get();
           if(accountPreference.isBannerEnabled()) {
               return true;
           } else {
               Instant fromDate = accountPreference.getBannerFromDate();
               Instant untilDate = accountPreference.getBannerUntilDate();
               Instant now = Instant.now();
               if(fromDate != null && untilDate != null) {
                   return now.isAfter(fromDate) && now.isBefore(untilDate);
               } else if(fromDate != null && untilDate == null) {
                   return now.isAfter(fromDate);
               } else if(fromDate == null && untilDate != null) {
                   return now.isBefore(untilDate);
               } else {
                  return false;
               }
           }
       } else {
           return false;
       }
   }

}
