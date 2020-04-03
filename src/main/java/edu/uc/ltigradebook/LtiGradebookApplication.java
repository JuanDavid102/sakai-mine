package edu.uc.ltigradebook;

import edu.ksu.canvas.CanvasApiFactory;
import edu.ksu.lti.launch.model.InstitutionRole;
import edu.ksu.lti.launch.service.LtiLoginService;
import edu.ksu.lti.launch.service.SimpleLtiLoginService;
import edu.uc.ltigradebook.util.RoleChecker;
import lombok.extern.slf4j.Slf4j;

import com.google.common.collect.ImmutableList;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.util.Locale;
import java.util.List;

@SpringBootApplication()
@EnableCaching
@Slf4j
public class LtiGradebookApplication {

    @Value("${lti-gradebook.url:someurl}")
    private String canvasBaseUrl;

    @Value("${lti-gradebook.canvas_api_token:secret}")
    private String canvasApiToken;

    private static final String DEFAULT_LOCALE = "es";

    public static void main(String[] args) {
        SpringApplication.run(LtiGradebookApplication.class, args);
    }

    @Bean
    public LtiLoginService ltiLoginService() {
        return new SimpleLtiLoginService();
    }

    @Bean
    public RoleChecker roleChecker() {
        final List<InstitutionRole> validRoles = new ImmutableList.Builder<InstitutionRole>()
                .add(InstitutionRole.Instructor)
                .add(InstitutionRole.TeachingAssistant)
                .add(InstitutionRole.Learner)
                .add(InstitutionRole.Administrator).build();
        return new RoleChecker(validRoles);
    }

    @Bean
    public CanvasApiFactory canvasApiFactory() {
        log.info("Creating Canvas API Factory for the domain {}.", canvasBaseUrl);
        return new CanvasApiFactory(canvasBaseUrl);
    }

    @Bean
    public String canvasDomain() {
        return canvasBaseUrl;
    }

    @Bean
    public SessionLocaleResolver localeResolver() {
        SessionLocaleResolver sessionLocaleResolver = new SessionLocaleResolver();
        sessionLocaleResolver.setDefaultLocale(new Locale(DEFAULT_LOCALE));
        return sessionLocaleResolver;
    }
}
