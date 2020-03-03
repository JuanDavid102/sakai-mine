package edu.uc.ltigradebook.repository;

import edu.uc.ltigradebook.entity.OauthToken;

import java.util.List;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TokenRepository extends CrudRepository<OauthToken, Long> {
    List<OauthToken> findByToken(String token);
    List<OauthToken> findByStatus(boolean status);
}
