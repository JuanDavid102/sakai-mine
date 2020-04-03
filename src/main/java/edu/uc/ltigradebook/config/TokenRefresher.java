package edu.uc.ltigradebook.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import edu.uc.ltigradebook.entity.OauthToken;
import edu.uc.ltigradebook.service.CanvasAPIServiceWrapper;
import edu.uc.ltigradebook.service.TokenService;
import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableScheduling
@Slf4j
public class TokenRefresher {

    @Autowired
    TokenService tokenService;

    @Autowired
    CanvasAPIServiceWrapper canvasService;

    @Scheduled(fixedDelay =  60 * 60 * 1000,  initialDelay = 500)
    public void refreshTokens() {
        log.info("Refreshing authentication Tokens, the tokens are refreshed every hour.");
        List<OauthToken> oauthTokenList = new ArrayList<OauthToken>();
        tokenService.getAllTokens().forEach(oauthTokenList::add);
        if (oauthTokenList.isEmpty()) {
            log.warn("IMPORTANT: Please provide some authentication tokens using the administration area or the gradebook application may fail in high load conditions.");
            return;
        }

        for (OauthToken oauthToken : oauthTokenList) {
            if (oauthToken.isStatus() && !canvasService.validateToken(oauthToken.getToken())) {
                log.error("The token {} has been detected as invalid, invalidating it in the database.", oauthToken.getToken());
                oauthToken.setStatus(false);
                tokenService.saveToken(oauthToken);
            }
        }
        log.info("Tokens refreshed successfully.");
    }

}
