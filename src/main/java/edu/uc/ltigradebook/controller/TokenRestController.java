package edu.uc.ltigradebook.controller;

import java.time.Instant;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.ksu.lti.launch.model.LtiLaunchData;
import edu.ksu.lti.launch.model.LtiSession;
import edu.ksu.lti.launch.oauth.LtiPrincipal;
import edu.uc.ltigradebook.constants.EventConstants;
import edu.uc.ltigradebook.constants.LtiConstants;
import edu.uc.ltigradebook.entity.OauthToken;
import edu.uc.ltigradebook.exception.TokenException;
import edu.uc.ltigradebook.service.CanvasAPIServiceWrapper;
import edu.uc.ltigradebook.service.EventTrackingService;
import edu.uc.ltigradebook.service.SecurityService;
import edu.uc.ltigradebook.service.TokenService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class TokenRestController {

    @Autowired
    private CanvasAPIServiceWrapper canvasService;

    @Autowired
    EventTrackingService eventTrackingService;

    @Autowired
    SecurityService securityService;

    @Autowired
    TokenService tokenService;

    @PostMapping(value = "/saveToken")
    public boolean saveToken(@RequestParam String token, @ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession) throws TokenException {
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        String userId = lld.getCustom().get(LtiConstants.CANVAS_USER_ID);
        String canvasLoginId = ltiPrincipal.getUser();
        String eventDetails = new JSONObject().put("token", token).toString();

        if (!securityService.isAdminUser(canvasLoginId, lld.getRolesList())) {
            log.error("Security error when trying to save a token, reporting the issue.");
            eventTrackingService.postEvent(EventConstants.ADMIN_ACCESS_FORBIDDEN, userId, StringUtils.EMPTY, eventDetails);
            throw new TokenException();
        }

        OauthToken oauthToken = new OauthToken();
        if (StringUtils.isBlank(token) || !canvasService.validateToken(token)) {
            log.error("Cannot create an invalid token {}.", token);
            throw new TokenException();
        }

        log.debug("Saving token by the user {}, token hidden for security reasons.", userId);
        oauthToken.setToken(token);
        oauthToken.setCreatedBy(userId);
        oauthToken.setCreatedDate(Instant.now());
        oauthToken.setStatus(true);
        tokenService.saveToken(oauthToken);

        //Post an event
        eventTrackingService.postEvent(EventConstants.ADMIN_SAVE_TOKEN, userId, StringUtils.EMPTY, eventDetails);
        return true;
    }

    @PostMapping(value = "/deleteToken")
    public boolean deleteToken(@RequestParam String token, @ModelAttribute LtiPrincipal ltiPrincipal, LtiSession ltiSession) throws TokenException {
        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        String userId = lld.getCustom().get(LtiConstants.CANVAS_USER_ID);
        String canvasLoginId = ltiPrincipal.getUser();
        String eventDetails = new JSONObject().put("token", token).toString();

        if (!securityService.isAdminUser(canvasLoginId, lld.getRolesList())) {
            log.error("Security error when trying to delete a token, reporting the issue.");
            eventTrackingService.postEvent(EventConstants.ADMIN_ACCESS_FORBIDDEN, userId, StringUtils.EMPTY, eventDetails);
            throw new TokenException();
        }

        log.debug("Deleting token by the user {}.", userId);
        List<OauthToken> tokenList = tokenService.getOauthToken(token);
        if (tokenList.isEmpty()) {
            throw new TokenException();
        }

        for (OauthToken tokenObject : tokenList) {
            tokenService.deleteToken(tokenObject);
        }

        //Post an event
        eventTrackingService.postEvent(EventConstants.ADMIN_DELETE_TOKEN, userId, StringUtils.EMPTY, eventDetails);
        return true;
    }

}
