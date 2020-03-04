package edu.uc.ltigradebook.controller;

import java.io.IOException;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.ksu.lti.launch.model.LtiLaunchData;
import edu.ksu.lti.launch.model.LtiSession;
import edu.ksu.lti.launch.oauth.LtiPrincipal;
import edu.uc.ltigradebook.constants.EventConstants;
import edu.uc.ltigradebook.constants.LtiConstants;
import edu.uc.ltigradebook.entity.AccountPreference;
import edu.uc.ltigradebook.exception.AccountException;
import edu.uc.ltigradebook.service.AccountService;
import edu.uc.ltigradebook.service.CanvasAPIServiceWrapper;
import edu.uc.ltigradebook.service.EventTrackingService;
import edu.uc.ltigradebook.util.DateUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class AccountRestController {

    @Autowired
    AccountService accountService;

    @Autowired
    CanvasAPIServiceWrapper canvasService;

    @Autowired
    EventTrackingService eventTrackingService;

    @RequestMapping(value = "/saveAccountPreferences", method = RequestMethod.POST)
    public boolean saveAccountPreferences(@RequestBody AccountPreference accountPreference, @ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession) throws AccountException, IOException {
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        String canvasUserId = lld.getCustom().get(LtiConstants.CANVAS_USER_ID);
        log.info("Saving banner account preferences {}.", accountPreference);

        if(StringUtils.isNotBlank(accountPreference.getBannerFromStringDate())) {
            accountPreference.setBannerFromDate(DateUtils.convertDateToInstant(accountPreference.getBannerFromStringDate()));
        }

        if(StringUtils.isNotBlank(accountPreference.getBannerUntilStringDate())) {
            accountPreference.setBannerUntilDate(DateUtils.convertDateToInstant(accountPreference.getBannerUntilStringDate()));
        }

        if(accountPreference.getBannerFromDate() != null && accountPreference.getBannerUntilDate() != null && accountPreference.getBannerFromDate().isAfter(accountPreference.getBannerUntilDate())) {
            throw new AccountException();
        }

        accountService.saveAccountPreferences(accountPreference);
        log.info("Account {} preferences saved successfully.", accountPreference);

        // Post an event
        String eventDetails = new JSONObject().put("accountId", accountPreference.getAccountId()).put("bannerFromStringDate", accountPreference.getBannerFromStringDate()).put("bannerUntilStringDate", accountPreference.getBannerUntilStringDate()).put("bannerEnabled", accountPreference.isBannerEnabled()).toString();
        eventTrackingService.postEvent(EventConstants.ADMIN_SAVE_BANNER, canvasUserId, StringUtils.EMPTY, eventDetails);

        return true;
    }

    @PostMapping(value = "/deleteAccountPreferences")
    public boolean deleteAccountPreferences(@RequestParam String accountId, @ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession) throws AccountException, IOException {
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        String canvasUserId = lld.getCustom().get(LtiConstants.CANVAS_USER_ID);
        log.info("Deleting banner account preferences for the account {}.", accountId);

        long longAccountId = Long.MIN_VALUE;
        try {
            longAccountId = Long.valueOf(accountId);
        } catch (Exception e) {
            log.error("Trying to delete an invalid accountId");
            throw new AccountException();            
        }

        Optional<AccountPreference> accountServiceOptional = accountService.getAccountPreferences(longAccountId);
        if (accountServiceOptional.isPresent()) {
            accountService.deleteAccountPreferences(accountServiceOptional.get());
            log.info("Account {} preferences deleted successfully.", accountId);

            // Post an event
            String eventDetails = new JSONObject().put("accountId", accountId).toString();
            eventTrackingService.postEvent(EventConstants.ADMIN_DELETE_BANNER, canvasUserId, StringUtils.EMPTY, eventDetails);
        } else {
            throw new AccountException();
        }

        return true;
    }

}
