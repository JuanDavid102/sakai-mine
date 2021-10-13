package edu.uc.ltigradebook.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import edu.uc.ltigradebook.constants.CacheConstants;
import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableCaching
@EnableScheduling
@Slf4j
public class CachingConfig {

    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
            CacheConstants.ASSIGNMENTS_IN_COURSE,
            CacheConstants.ASSIGNMENT_SUBMISSIONS,
            CacheConstants.COURSE_ASSIGNMENT_GROUPS,
            CacheConstants.EVENTS,
            CacheConstants.SECTIONS_IN_COURSE,
            CacheConstants.SINGLE_ASSIGNMENT,
            CacheConstants.SINGLE_COURSE,
            CacheConstants.SINGLE_EVENT,
            CacheConstants.SINGLE_SUBMISSION,
            CacheConstants.SUBACCOUNTS,
            CacheConstants.USERS_IN_COURSE,
            CacheConstants.INSTRUCTORS_IN_COURSE
            );
        return cacheManager;
    }

    //Evict all the caches every 10 minutes.
    @CacheEvict(allEntries = true, value = { 
            CacheConstants.COURSE_ASSIGNMENT_GROUPS, 
            CacheConstants.EVENTS,
            CacheConstants.SINGLE_ASSIGNMENT,
            CacheConstants.SECTIONS_IN_COURSE,
            CacheConstants.SINGLE_COURSE,
            CacheConstants.SINGLE_EVENT,
            CacheConstants.SUBACCOUNTS,
            CacheConstants.USERS_IN_COURSE,
            CacheConstants.INSTRUCTORS_IN_COURSE,
            CacheConstants.ASSIGNMENTS_IN_COURSE,
            CacheConstants.ASSIGNMENT_SUBMISSIONS,
            CacheConstants.SINGLE_SUBMISSION
            })
    @Scheduled(fixedDelayString = "${cache.expiry.interval}", initialDelayString = "${cache.initial.delay}")
    public void cacheEvict() {
        log.info("Evicting caches of the Gradebook application....");
    }

}
