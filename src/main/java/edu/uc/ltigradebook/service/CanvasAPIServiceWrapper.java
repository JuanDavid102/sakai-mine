package edu.uc.ltigradebook.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import edu.ksu.canvas.CanvasApiFactory;
import edu.ksu.canvas.interfaces.AccountReader;
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
import edu.ksu.canvas.oauth.NonRefreshableOauthToken;
import edu.ksu.canvas.oauth.OauthToken;
import edu.ksu.canvas.requestOptions.CreateConversationOptions;
import edu.ksu.canvas.requestOptions.GetSingleAssignmentOptions;
import edu.ksu.canvas.requestOptions.GetSingleCourseOptions;
import edu.ksu.canvas.requestOptions.GetSubAccountsOptions;
import edu.ksu.canvas.requestOptions.GetUsersInCourseOptions;
import edu.ksu.canvas.requestOptions.GetUsersInCourseOptions.EnrollmentType;
import edu.ksu.canvas.requestOptions.ListAccountOptions;
import edu.uc.ltigradebook.constants.CacheConstant;
import edu.ksu.canvas.requestOptions.ListAssignmentGroupOptions;
import edu.ksu.canvas.requestOptions.GetSubmissionsOptions;
import edu.ksu.canvas.requestOptions.ListCourseAssignmentsOptions;

@Service
public class CanvasAPIServiceWrapper {

    @Autowired
    private CanvasApiFactory canvasApiFactory;

    @Value("${lti-gradebook.canvas_api_token:secret}")
    private String canvasApiToken;

    @Cacheable(CacheConstant.USERS_IN_COURSE)
    public List<User> getUsersInCourse(String courseId) throws IOException {
        OauthToken oauthToken = new NonRefreshableOauthToken(canvasApiToken);
        UserReader userReader = canvasApiFactory.getReader(UserReader.class, oauthToken);
        GetUsersInCourseOptions options = new GetUsersInCourseOptions(courseId)
                .include(Arrays.asList(GetUsersInCourseOptions.Include.ENROLLMENTS))
                .enrollmentType(Arrays.asList(EnrollmentType.STUDENT, EnrollmentType.OBSERVER));
        return userReader.getUsersInCourse(options);
    }

    @Cacheable(CacheConstant.SECTIONS_IN_COURSE)
    public List<Section> getSectionsInCourse(String courseId) throws IOException {
        OauthToken oauthToken = new NonRefreshableOauthToken(canvasApiToken);
        SectionReader sectionReader = canvasApiFactory.getReader(SectionReader.class, oauthToken);
        return sectionReader.listCourseSections(courseId, new ArrayList<>());
    }

    @Cacheable(CacheConstant.ASSIGNMENTS_IN_COURSE)
    public List<Assignment> listCourseAssignments(String courseId) throws IOException {
        OauthToken oauthToken = new NonRefreshableOauthToken(canvasApiToken);
        AssignmentReader assignmentReader = canvasApiFactory.getReader(AssignmentReader.class, oauthToken);
        ListCourseAssignmentsOptions options = new ListCourseAssignmentsOptions(courseId)
                .includes(Arrays.asList(ListCourseAssignmentsOptions.Include.ASSIGNMENT_VISIBILITY));
        return assignmentReader.listCourseAssignments(options);
    }

    @Cacheable(CacheConstant.COURSE_ASSIGNMENT_GROUPS)
    public List<AssignmentGroup> listAssignmentGroups(String courseId) throws IOException {
        OauthToken oauthToken = new NonRefreshableOauthToken(canvasApiToken);
        AssignmentGroupReader assignmentGroupReader = canvasApiFactory.getReader(AssignmentGroupReader.class, oauthToken);
        ListAssignmentGroupOptions options = new ListAssignmentGroupOptions(courseId);
        return assignmentGroupReader.listAssignmentGroup(options);
    }

    @Cacheable(CacheConstant.SINGLE_ASSIGNMENT)
    public Optional<Assignment> getSingleAssignment(String courseId, Integer assignmentId) throws IOException {
        OauthToken oauthToken = new NonRefreshableOauthToken(canvasApiToken);
        AssignmentReader assignmentReader = canvasApiFactory.getReader(AssignmentReader.class, oauthToken);
        GetSingleAssignmentOptions options = new GetSingleAssignmentOptions(courseId, assignmentId);
        return assignmentReader.getSingleAssignment(options);
    }

    @Cacheable(CacheConstant.ASSIGNMENT_SUBMISSIONS)
    public List<Submission> getCourseSubmissions(String courseId, Integer assignmentId) throws IOException {
        OauthToken oauthToken = new NonRefreshableOauthToken(canvasApiToken);
        SubmissionReader submissionReader = canvasApiFactory.getReader(SubmissionReader.class, oauthToken);
        GetSubmissionsOptions options = new GetSubmissionsOptions(courseId, assignmentId);
        return submissionReader.getCourseSubmissions(options);
    }

    @Cacheable(CacheConstant.SINGLE_SUBMISSION)
    public Optional<Submission> getSingleCourseSubmission(String courseId, Integer assignmentId, String userId) throws IOException {
        OauthToken oauthToken = new NonRefreshableOauthToken(canvasApiToken);
        SubmissionReader submissionReader = canvasApiFactory.getReader(SubmissionReader.class, oauthToken);
        GetSubmissionsOptions options = new GetSubmissionsOptions(courseId, assignmentId, userId);
        return submissionReader.getSingleCourseSubmission(options);
    }    

    @Cacheable(CacheConstant.SINGLE_COURSE)
    public Optional<Course> getSingleCourse(String courseId) throws IOException {
        OauthToken oauthToken = new NonRefreshableOauthToken(canvasApiToken);
        CourseReader courseReader = canvasApiFactory.getReader(CourseReader.class, oauthToken);
        GetSingleCourseOptions options = new GetSingleCourseOptions(courseId);
        return courseReader.getSingleCourse(options);
    }

    @Cacheable(CacheConstant.SUBACCOUNTS)
    public List<Account> getSubaccounts() throws IOException {
        OauthToken oauthToken = new NonRefreshableOauthToken(canvasApiToken);
        AccountReader accountReader = canvasApiFactory.getReader(AccountReader.class, oauthToken);
        List<Account> allAccountList = new ArrayList<Account>();
        List<Account> mainAccountList = accountReader.listAccounts(new ListAccountOptions());
        //Main level of accounts
        for(Account mainAccount : mainAccountList) {
            GetSubAccountsOptions options = new GetSubAccountsOptions(String.valueOf(mainAccount.getId()));
            List<Account> subaccountList = accountReader.getSubAccounts(options);
            //Add a second level of accounts
            allAccountList.addAll(subaccountList);
            //Add a third level of accounts
            for(Account subAccount : subaccountList) {
                GetSubAccountsOptions subOptions = new GetSubAccountsOptions(String.valueOf(subAccount.getId()));
                allAccountList.addAll(accountReader.getSubAccounts(subOptions));
            }
        }

        return allAccountList;
    }

    public void createConversation(List<String> userIds, String subject, String bodyMessage) throws IOException {
        OauthToken oauthToken = new NonRefreshableOauthToken(canvasApiToken);
        ConversationWriter conversationWriter = canvasApiFactory.getWriter(ConversationWriter.class, oauthToken);
        CreateConversationOptions createConversationOptions = new CreateConversationOptions(userIds, bodyMessage).subject(subject);
        conversationWriter.createConversation(createConversationOptions);
        return;
    }

}
