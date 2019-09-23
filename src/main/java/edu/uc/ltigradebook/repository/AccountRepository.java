package edu.uc.ltigradebook.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import edu.uc.ltigradebook.entity.AccountPreference;

@Repository
public interface AccountRepository extends CrudRepository<AccountPreference, Long> {

}