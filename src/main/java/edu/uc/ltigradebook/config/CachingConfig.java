package edu.uc.ltigradebook.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import edu.uc.ltigradebook.constants.CacheConstant;
import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableCaching
@EnableScheduling
@Slf4j
public class CachingConfig {

    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
            CacheConstant.ASSIGNMENT_SUBMISSIONS, 
            CacheConstant.ASSIGNMENTS_IN_COURSE, 
            CacheConstant.COURSE_ASSIGNMENT_GROUPS, 
            CacheConstant.EVENTS, 
            CacheConstant.SINGLE_ASSIGNMENT, 
            CacheConstant.SINGLE_EVENT, 
            CacheConstant.SINGLE_SUBMISSION, 
            CacheConstant.USERS_IN_COURSE,
            CacheConstant.SECTIONS_IN_COURSE,
            CacheConstant.SINGLE_COURSE,
            CacheConstant.SUBACCOUNTS
            );
        return cacheManager;
    }
    
    //Evict the grades cache every 2 minutes.
    @CacheEvict(allEntries = true, value = {
            CacheConstant.ASSIGNMENT_SUBMISSIONS,
            CacheConstant.ASSIGNMENTS_IN_COURSE,  
            CacheConstant.SINGLE_SUBMISSION
            })
    @Scheduled(fixedDelay =  2 * 60 * 1000,  initialDelay = 500)
    public void gradeCacheEvict() {
        log.info("Evicting grade caches of the Gradebook application, the grades are refreshed every 2 minutes.");
    }

    //Evict all the caches every 10 minutes.
    @CacheEvict(allEntries = true, value = { 
            CacheConstant.COURSE_ASSIGNMENT_GROUPS, 
            CacheConstant.EVENTS, 
            CacheConstant.SINGLE_ASSIGNMENT, 
            CacheConstant.SINGLE_EVENT, 
            CacheConstant.USERS_IN_COURSE,
            CacheConstant.SECTIONS_IN_COURSE,
            CacheConstant.SINGLE_COURSE,
            CacheConstant.SUBACCOUNTS
            })
    @Scheduled(fixedDelay =  10 * 60 * 1000,  initialDelay = 500)
    public void cacheEvict() {
        log.info("Evicting caches of the Gradebook application, the cache is refreshed every 10 minutes.");
    }

}
