package edu.uc.ltigradebook.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import edu.uc.ltigradebook.entity.OauthToken;
import edu.uc.ltigradebook.repository.TokenRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TokenService {

    @Autowired
    private TokenRepository tokenRepository;

    public Iterable<OauthToken> getAllTokens() {
        log.debug("Getting all the tokens from the table.");
        return tokenRepository.findAll();
    }

    public List<OauthToken> getAllValidTokens() {
        log.debug("Getting all the valid tokens from the table.");
        return tokenRepository.findByStatus(true);
    }

    public List<OauthToken> getOauthToken(String token) {
        log.debug("Getting token by token id.");
        return tokenRepository.findByToken(token);
    }

    public void saveToken(OauthToken oauthToken) {
        log.debug("Saving token for the user {}.", oauthToken.getCreatedBy());
        tokenRepository.save(oauthToken);
        log.debug("Token saved successfully");
    }

    public void deleteToken(OauthToken oauthToken) {
        log.debug("Deleting the token by the user {}.", oauthToken.getCreatedBy());
        tokenRepository.delete(oauthToken);
        log.debug("Token deleted successfully");
    }

}
