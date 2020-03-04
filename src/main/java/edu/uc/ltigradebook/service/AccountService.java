package edu.uc.ltigradebook.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import edu.uc.ltigradebook.entity.AccountPreference;
import edu.uc.ltigradebook.repository.AccountRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AccountService {

    @Autowired
    private AccountRepository accountRepository;

    public Iterable<AccountPreference> getAllAccountPreferences() {
        log.debug("Getting all account preferences.");
        return accountRepository.findAll();
    }

    public Optional<AccountPreference> getAccountPreferences(long accountId) {
        log.debug("Getting account preferences by accountId {}.", accountId);
        return accountRepository.findById(accountId);
    }

    public void saveAccountPreferences(AccountPreference accountPreference) {
        log.debug("Saving account preferences {}.", accountPreference);
        accountRepository.save(accountPreference);
    }

    public void deleteAccountPreferences(AccountPreference accountPreference) {
        log.debug("Deleting account preferences {}.", accountPreference);
        accountRepository.delete(accountPreference);		
    }

}
