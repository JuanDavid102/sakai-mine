package edu.uc.ltigradebook.config;

import edu.ksu.lti.launch.service.LtiLoginService;
import edu.ksu.lti.launch.service.SingleToolConsumerService;
import edu.ksu.lti.launch.service.ToolConsumerService;
import edu.ksu.lti.launch.spring.config.LtiConfigurer;
import edu.ksu.lti.launch.spring.config.LtiLaunchCsrfMatcher;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.provisioning.UserDetailsManager;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    @Value("${lti-gradebook.instance:consumer}")
    private String instance;

    @Value("${lti-gradebook.name:LTI Gradebook}")
    private String name;

    @Value("${lti-gradebook.url:someurl}")
    private String url;

    @Value("${lti-gradebook.secret:secret}")
    private String secret;

    @Autowired
    private LtiLoginService ltiLoginService;

    @Autowired
    private ToolConsumerService toolConsumerService;

    @Override
    public void configure(WebSecurity webSecurity) throws Exception {
        super.configure(webSecurity);
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.setSharedObject(LtiLoginService.class, ltiLoginService);
        LtiConfigurer<HttpSecurity> ltiConfigurer = new LtiConfigurer<>(toolConsumerService, "/launch", false, false, null);
        http.apply(ltiConfigurer);

        // We have to disable CSRF on the LTI launch.
        http.csrf().requireCsrfProtectionMatcher(new LtiLaunchCsrfMatcher("/launch"));

        // Setup spring security to require all requests to be authenticated.
        http.authorizeRequests()
            .anyRequest().authenticated()
            .and()
            .headers().frameOptions().disable().and().csrf().disable();
    }

    @Bean
    public ToolConsumerService toolConsumerService() {
        return new SingleToolConsumerService(instance, name, url, secret);
    }

    /**
     * This is just to disable the creation of the default user.
     */
    @Bean
    public UserDetailsManager userDetailsManager() {
        return new InMemoryUserDetailsManager();
    }

}
