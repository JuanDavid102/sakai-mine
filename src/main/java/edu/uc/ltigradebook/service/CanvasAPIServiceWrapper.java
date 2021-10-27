package edu.uc.ltigradebook.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import edu.ksu.canvas.CanvasApiFactory;
import edu.ksu.canvas.interfaces.AccountReader;
import edu.ksu.canvas.interfaces.AccountReportReader;
import edu.ksu.canvas.interfaces.AccountReportWriter;
import edu.ksu.canvas.interfaces.AssignmentGroupReader;
import edu.ksu.canvas.interfaces.AssignmentReader;
import edu.ksu.canvas.interfaces.ConversationWriter;
import edu.ksu.canvas.interfaces.CourseReader;
import edu.ksu.canvas.interfaces.SectionReader;
import edu.ksu.canvas.interfaces.SubmissionReader;
import edu.ksu.canvas.interfaces.UserReader;
import edu.ksu.canvas.model.Account;
import edu.ksu.canvas.model.Course;
import edu.ksu.canvas.model.Section;
import edu.ksu.canvas.model.User;
import edu.ksu.canvas.model.assignment.Assignment;
import edu.ksu.canvas.model.assignment.AssignmentGroup;
import edu.ksu.canvas.model.assignment.Submission;
import edu.ksu.canvas.model.report.AccountReport;
import edu.ksu.canvas.oauth.NonRefreshableOauthToken;
import edu.ksu.canvas.oauth.OauthToken;
import edu.ksu.canvas.requestOptions.AccountReportOptions;
import edu.ksu.canvas.requestOptions.CreateConversationOptions;
import edu.ksu.canvas.requestOptions.GetSingleAssignmentOptions;
import edu.ksu.canvas.requestOptions.GetSingleCourseOptions;
import edu.ksu.canvas.requestOptions.GetSubAccountsOptions;
import edu.ksu.canvas.requestOptions.GetUsersInCourseOptions;
import edu.ksu.canvas.requestOptions.GetUsersInCourseOptions.EnrollmentType;
import edu.ksu.canvas.requestOptions.ListAccountOptions;
import edu.uc.ltigradebook.constants.CacheConstants;
import edu.uc.ltigradebook.model.AccountReportType;
import lombok.extern.slf4j.Slf4j;
import edu.ksu.canvas.requestOptions.ListAssignmentGroupOptions;
import edu.ksu.canvas.requestOptions.GetSubmissionsOptions;
import edu.ksu.canvas.requestOptions.ListCourseAssignmentsOptions;

@Slf4j
@Service
public class CanvasAPIServiceWrapper {

    @Autowired
    private CanvasApiFactory canvasApiFactory;

    @Autowired
    private TokenService tokenService;

    @Value("${lti-gradebook.canvas_api_token:secret}")
    private String canvasApiToken;

    private final String SIS_ACCOUNT_ID = "self";

    public boolean validateToken(String token) {
        OauthToken oauthToken = new NonRefreshableOauthToken(token);
        AccountReader accountReader = canvasApiFactory.getReader(AccountReader.class, oauthToken);

        try {
            accountReader.getSingleAccount("1").get();
        } catch (Exception e) {
            log.error("Validate token has detected an invalid token, full token hidden for security reasons.");
            return false;
        }

        return true;
    }

    private OauthToken getRandomOauthToken() {
        List<edu.uc.ltigradebook.entity.OauthToken> tokenList = tokenService.getAllValidTokens();

        // Return the default token is the list is empty.
        if (tokenList.isEmpty()) {
            log.debug("The LTI tool needs more tokens to work properly, using the default. Please provide more authentication tokens using the administration area.");
            return new NonRefreshableOauthToken(canvasApiToken);
        }

        // Return a random token from the valid token list.
        Random rand = new Random();
        String randomCanvasApiToken = tokenList.get(rand.nextInt(tokenList.size())).getToken();
        return new NonRefreshableOauthToken(randomCanvasApiToken);
    }

    @Cacheable(CacheConstants.USERS_IN_COURSE)
    public List<User> getUsersInCourse(String courseId) throws IOException {
        OauthToken oauthToken = this.getRandomOauthToken();
        UserReader userReader = canvasApiFactory.getReader(UserReader.class, oauthToken);
        GetUsersInCourseOptions options = new GetUsersInCourseOptions(courseId)
                .include(Arrays.asList(GetUsersInCourseOptions.Include.ENROLLMENTS))
                .enrollmentType(Arrays.asList(EnrollmentType.STUDENT, EnrollmentType.OBSERVER));
        return userReader.getUsersInCourse(options);
    }

    @Cacheable(CacheConstants.INSTRUCTORS_IN_COURSE)
    public List<User> getTeachersInCourse(String courseId) throws IOException {
        OauthToken oauthToken = this.getRandomOauthToken();
        UserReader userReader = canvasApiFactory.getReader(UserReader.class, oauthToken);
        GetUsersInCourseOptions options = new GetUsersInCourseOptions(courseId)
                .include(Arrays.asList(GetUsersInCourseOptions.Include.ENROLLMENTS))
                .enrollmentType(Arrays.asList(EnrollmentType.TEACHER));
        return userReader.getUsersInCourse(options);
    }

    @Cacheable(CacheConstants.SECTIONS_IN_COURSE)
    public List<Section> getSectionsInCourse(String courseId) throws IOException {
        OauthToken oauthToken = this.getRandomOauthToken();
        SectionReader sectionReader = canvasApiFactory.getReader(SectionReader.class, oauthToken);
        return sectionReader.listCourseSections(courseId, new ArrayList<>());
    }

    @Cacheable(CacheConstants.ASSIGNMENTS_IN_COURSE)
    public List<Assignment> listCourseAssignments(String courseId) throws IOException {
        OauthToken oauthToken = this.getRandomOauthToken();
        AssignmentReader assignmentReader = canvasApiFactory.getReader(AssignmentReader.class, oauthToken);
        ListCourseAssignmentsOptions options = new ListCourseAssignmentsOptions(courseId)
                .includes(Arrays.asList(ListCourseAssignmentsOptions.Include.ASSIGNMENT_VISIBILITY));
        return assignmentReader.listCourseAssignments(options);
    }

    @Cacheable(CacheConstants.COURSE_ASSIGNMENT_GROUPS)
    public List<AssignmentGroup> listAssignmentGroups(String courseId) throws IOException {
        OauthToken oauthToken = this.getRandomOauthToken();
        AssignmentGroupReader assignmentGroupReader = canvasApiFactory.getReader(AssignmentGroupReader.class, oauthToken);
        ListAssignmentGroupOptions options = new ListAssignmentGroupOptions(courseId);
        return assignmentGroupReader.listAssignmentGroup(options);
    }

    @Cacheable(CacheConstants.SINGLE_ASSIGNMENT)
    public Optional<Assignment> getSingleAssignment(String courseId, Integer assignmentId) throws IOException {
        OauthToken oauthToken = this.getRandomOauthToken();
        AssignmentReader assignmentReader = canvasApiFactory.getReader(AssignmentReader.class, oauthToken);
        GetSingleAssignmentOptions options = new GetSingleAssignmentOptions(courseId, assignmentId);
        return assignmentReader.getSingleAssignment(options);
    }

    @Cacheable(CacheConstants.ASSIGNMENT_SUBMISSIONS)
    public List<Submission> getCourseSubmissions(String courseId, Integer assignmentId) throws IOException {
        OauthToken oauthToken = this.getRandomOauthToken();
        SubmissionReader submissionReader = canvasApiFactory.getReader(SubmissionReader.class, oauthToken);
        GetSubmissionsOptions options = new GetSubmissionsOptions(courseId, assignmentId);
        return submissionReader.getCourseSubmissions(options);
    }

    @Cacheable(CacheConstants.SINGLE_SUBMISSION)
    public Optional<Submission> getSingleCourseSubmission(String courseId, Integer assignmentId, String userId) throws IOException {
        OauthToken oauthToken = this.getRandomOauthToken();
        SubmissionReader submissionReader = canvasApiFactory.getReader(SubmissionReader.class, oauthToken);
        GetSubmissionsOptions options = new GetSubmissionsOptions(courseId, assignmentId, userId);
        return submissionReader.getSingleCourseSubmission(options);
    }    

    @Cacheable(CacheConstants.SINGLE_COURSE)
    public Optional<Course> getSingleCourse(String courseId) throws IOException {
        OauthToken oauthToken = this.getRandomOauthToken();
        CourseReader courseReader = canvasApiFactory.getReader(CourseReader.class, oauthToken);
        GetSingleCourseOptions options = new GetSingleCourseOptions(courseId);
        return courseReader.getSingleCourse(options);
    }

    @Cacheable(CacheConstants.SUBACCOUNTS)
    public List<Account> getSubaccounts() throws IOException {
        OauthToken oauthToken = this.getRandomOauthToken();
        AccountReader accountReader = canvasApiFactory.getReader(AccountReader.class, oauthToken);
        List<Account> allAccountList = new ArrayList<Account>();
        List<Account> mainAccountList = accountReader.listAccounts(new ListAccountOptions());
        //Main level of accounts
        for(Account mainAccount : mainAccountList) {
            GetSubAccountsOptions options = new GetSubAccountsOptions(String.valueOf(mainAccount.getId()));
            List<Account> subaccountList = accountReader.getSubAccounts(options);
            //Add a second level of accounts
            allAccountList.addAll(subaccountList);
            //Add a third level of accounts. Removed in March 2020 due to changes to the banner preferences.
            /*for(Account subAccount : subaccountList) {
                GetSubAccountsOptions subOptions = new GetSubAccountsOptions(String.valueOf(subAccount.getId()));
                allAccountList.addAll(accountReader.getSubAccounts(subOptions));
            }*/
        }

        return allAccountList;
    }

    public Optional<Account> getSubAccountForCourseAccount(String accountId) throws IOException {
        OauthToken oauthToken = this.getRandomOauthToken();
        AccountReader accountReader = canvasApiFactory.getReader(AccountReader.class, oauthToken);
        List<Integer> subaccountIdList = this.getSubaccounts().stream().map(account -> account.getId()).collect(Collectors.toList());
        Optional<Account> accountOptional = accountReader.getSingleAccount(accountId);
        // Check if the account of the course exists.
        if (accountOptional.isPresent()) {
            Integer subAccountId = accountOptional.get().getId();
            // While the account of the course is not in the list of main subaccounts, we need to explore the parent accounts
            while (!subaccountIdList.contains(subAccountId)) {
                // Check the parent account, if the parent account is a main subaccount, we got it.
                subAccountId = accountOptional.get().getParentAccountId();
                accountOptional = accountReader.getSingleAccount(String.valueOf(subAccountId));
            }
            return accountOptional;
        } else {
            return Optional.empty();
        }
    }

    public void createConversation(List<String> userIds, String subject, String bodyMessage) throws IOException {
        OauthToken oauthToken = this.getRandomOauthToken();
        ConversationWriter conversationWriter = canvasApiFactory.getWriter(ConversationWriter.class, oauthToken);
        CreateConversationOptions createConversationOptions = new CreateConversationOptions(userIds, bodyMessage).subject(subject).forceNew(true);
        conversationWriter.createConversation(createConversationOptions);
        return;
    }

    /**
     * Requests an account report of the provided type.
     * @param reportType The type of the report example: provisioning_csv.
     * @returns Optional with the AccountReport object requested to Canvas.
     * @throws IOException
     */
    public Optional<AccountReport> startAccountReport(AccountReportType reportType) throws IOException {
        OauthToken oauthToken = this.getRandomOauthToken();
        AccountReportOptions options = new AccountReportOptions(reportType.name(), SIS_ACCOUNT_ID);
        options.enrollmentTermId("");
        AccountReportWriter accountReportWriter = canvasApiFactory.getWriter(AccountReportWriter.class, oauthToken);
        log.info("Account report of type {} requested for the account {}.", reportType.name(), SIS_ACCOUNT_ID);
        return accountReportWriter.startReport(options);
    }

    /**
     * Requests the status of an account report providing the report id.
     * @param accountReportId The id of the account report.
     * @param reportType The type of the report example: provisioning_csv.
     * @returns Optional with the AccountReport object requested to Canvas.
     * @throws IOException
     */
    public Optional<AccountReport> getAccountReportStatus(Integer accountReportId, AccountReportType reportType) throws IOException {
        OauthToken oauthToken = this.getRandomOauthToken();
        log.info("Getting the account report status of the report {}.", accountReportId);
        AccountReportReader accountReportReader = canvasApiFactory.getReader(AccountReportReader.class, oauthToken);
        return accountReportReader.reportStatus(SIS_ACCOUNT_ID, reportType.name(), accountReportId);
    }

}
