package edu.uc.ltigradebook.service;

import edu.ksu.canvas.model.assignment.Assignment;
import edu.ksu.canvas.model.assignment.AssignmentGroup;
import edu.ksu.canvas.model.assignment.GradingRules;
import edu.ksu.lti.launch.model.LtiLaunchData;
import edu.ksu.lti.launch.model.LtiSession;
import edu.uc.ltigradebook.constants.LtiConstants;
import edu.uc.ltigradebook.entity.AssignmentPreference;
import edu.uc.ltigradebook.entity.CoursePreference;
import edu.uc.ltigradebook.entity.StudentCanvasGrade;
import edu.uc.ltigradebook.entity.StudentFinalGrade;
import edu.uc.ltigradebook.entity.StudentFinalGradeId;
import edu.uc.ltigradebook.entity.StudentGrade;
import edu.uc.ltigradebook.entity.StudentGradeId;
import edu.uc.ltigradebook.exception.GradeException;
import edu.uc.ltigradebook.repository.CanvasGradeRepository;
import edu.uc.ltigradebook.repository.FinalGradeRepository;
import edu.uc.ltigradebook.repository.GradeRepository;
import edu.uc.ltigradebook.util.GradeUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

@Service
@Slf4j
public class GradeService {

    @Autowired
    private GradeRepository gradeRepository;

    @Autowired
    private CanvasGradeRepository canvasGradeRepository;

    @Autowired
    private FinalGradeRepository finalGradeRepository;

    @Autowired
    private CanvasAPIServiceWrapper canvasService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private SecurityService securityService;

    @Autowired
    private AssignmentService assignmentService;

    @Value("${spring.jpa.properties.hibernate.jdbc.batch_size:30}")
    private int batchSize = 30;

    private static final String GRADE_NOT_AVAILABLE = "-";

    public Optional<StudentGrade> getGradeByAssignmentAndUser(String assignmentId, String userId) {
        log.debug("Getting a student grade by assignment {} and user {}.", assignmentId, userId);
        return gradeRepository.findById(new StudentGradeId(assignmentId, userId));
    }

    public Optional<StudentCanvasGrade> getCanvasGradeByAssignmentAndUser(String assignmentId, String userId) {
        log.debug("Getting a student canvas grade by assignment {} and user {}.", assignmentId, userId);
        return canvasGradeRepository.findById(new StudentGradeId(assignmentId, userId));
    }

    public void saveGrade(StudentGrade studentGrade) {
        log.debug("Saving grade {} for the user {} and assignment {}.", studentGrade.getGrade(), studentGrade.getUserId(), studentGrade.getAssignmentId());
        gradeRepository.save(studentGrade);
        log.debug("Grade saved successfully");
    }

    public void saveCanvasGrade(StudentCanvasGrade studentCanvasGrade) {
        log.debug("Saving Canvas grade {} for the user {} and assignment {}.", studentCanvasGrade.getGrade(), studentCanvasGrade.getUserId(),studentCanvasGrade.getAssignmentId());
        canvasGradeRepository.save(studentCanvasGrade);
        log.debug("Grade saved successfully");
    }

    public void saveFinalGrade(StudentFinalGrade studentFinalGrade) {
        log.debug("Saving grade {} for the user {} and course {}.", studentFinalGrade.getGrade(), studentFinalGrade.getUserId(), studentFinalGrade.getCourseId());
        finalGradeRepository.save(studentFinalGrade);
        log.debug("Grade saved successfully");
    }

    public void deleteGrade(StudentGrade studentGrade) {
        log.debug("Deleting grade for the user {} and assignment {}.", studentGrade.getUserId(), studentGrade.getAssignmentId());
        gradeRepository.delete(studentGrade);
        log.debug("Grade deleted successfully");
    }

    public void deleteFinalGrade(StudentFinalGrade studentFinalGrade) {
        log.debug("Deleting grade for the user {} and course {}.", studentFinalGrade.getUserId(), studentFinalGrade.getCourseId());
        finalGradeRepository.delete(studentFinalGrade);
        log.debug("Grade deleted successfully");
    }

    public long getGradeCount() {
        log.debug("Getting the grade count from the table.");
        return gradeRepository.count();
    }

    public long getGradedUserCount() {
        log.debug("Getting the graded user count from the table.");
        return gradeRepository.getGradedUserCount();
    }

    public JSONObject getStudentGroupMean(LtiSession ltiSession, Long groupId, long studentId, String courseId) throws Exception {
        JSONObject json = new JSONObject();
        JSONArray omittedAssignments = new JSONArray();
        JSONArray mutedAssignments = new JSONArray();
        JSONArray dropHighestAssignments = new JSONArray();
        JSONArray dropLowestAssignments = new JSONArray();

        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        String canvasUserId = lld.getCustom().get(LtiConstants.CANVAS_USER_ID);
        String studentIdString = String.valueOf(studentId);
        if (StringUtils.isBlank(courseId)) {
            courseId = ltiSession.getCanvasCourseId();
        }
        CoursePreference coursePreference = courseService.getCoursePreference(courseId);
        if (canvasUserId.equals(studentIdString) || securityService.isFaculty(lld.getRolesList())) {
            BigDecimal groupMeanSum = BigDecimal.ZERO;
            BigDecimal gradesLength = BigDecimal.ZERO;
            boolean calculateFinalGrade = true;

            try {
                List<Assignment> assignmentList = canvasService.listCourseAssignments(courseId);
                List<Assignment> assignmentsInGroup = assignmentList.stream().filter(asn -> groupId.equals(asn.getAssignmentGroupId())).collect(Collectors.toList());
                Map<Integer, BigDecimal> assignmentGradesMap = new HashMap<>();
                Map<Integer, String> studentSubmissionMap = this.getAssignmentScoresForStudent(assignmentsInGroup, studentId);
                List<AssignmentGroup> assignmentGroupList = canvasService.listAssignmentGroups(courseId);
                Optional<AssignmentGroup> assignmentGroupOptional = assignmentGroupList.stream().filter(ag -> groupId.equals(Long.valueOf(ag.getId().toString()))).findAny();
                GradingRules gradingRules = assignmentGroupOptional.isPresent() ? (assignmentGroupOptional.get().getGradingRules() != null ? assignmentGroupOptional.get().getGradingRules() : null) : null;
                for (Assignment assignment : assignmentsInGroup) {
                    // Initialize Assignment Variables
                    boolean gradeTypeNotSupported = false;
                    String grade = studentSubmissionMap.get(assignment.getId());
                    String assignmentId = String.valueOf(assignment.getId());
                    boolean omitFromFinalGrade = assignment.isOmitFromFinalGrade();
                    boolean isVisibleForUser = assignment.getAssignmentVisibility().stream().anyMatch(studentIdString::equals);
                    Optional<AssignmentPreference> assignmentPreference = assignmentService.getAssignmentPreference(assignmentId);

                    // The conversion scale can be overriden by the assignment preferences.
                    String assignmentConversionScale = coursePreference.getConversionScale();
                    if (assignmentPreference.isPresent() && StringUtils.isNotBlank(assignmentPreference.get().getConversionScale())) {
                        assignmentConversionScale = assignmentPreference.get().getConversionScale();
                    }

                    // The assignment can be muted by the assignment preferences.
                    boolean assignmentIsMuted = false;
                    if (StringUtils.isNotBlank(assignment.getMuted())) {
                        assignmentIsMuted = Boolean.valueOf(assignment.getMuted());
                    }
                    if (assignmentPreference.isPresent() && assignmentPreference.get().getMuted() != null) {
                        assignmentIsMuted = assignmentPreference.get().getMuted();
                    }

                    JSONObject assignmentJson = new JSONObject();
                    assignmentJson.put("id", assignmentId);
                    assignmentJson.put("name", assignment.getName());

                    // Skip if assignment is muted, grade is omitted from final grade or assignment possible points is zero
                    if (assignmentIsMuted) mutedAssignments.put(assignmentJson);
                    else if (omitFromFinalGrade) omittedAssignments.put(assignmentJson);
                    if (assignmentIsMuted || omitFromFinalGrade || !isVisibleForUser) continue;

                    //Grade conversion logic
                    switch (assignment.getGradingType()) {
                        case GradeUtils.GRADE_TYPE_POINTS:
                            grade = GradeUtils.mapGradeToScale(assignmentConversionScale, grade, assignment.getPointsPossible());
                            break;
                        case GradeUtils.GRADE_TYPE_PERCENT:
                            grade = GradeUtils.mapPercentageToScale(assignmentConversionScale, grade);
                            break;
                        default:
                            grade = GRADE_NOT_AVAILABLE;
                            gradeTypeNotSupported = true;
                            break;
                    }

                    //Get the grade from persistence, get the grade from the API otherwise.
                    Optional<StudentGrade> overwrittenStudentGrade = this.getGradeByAssignmentAndUser(assignmentId, String.valueOf(studentId));
                    if (overwrittenStudentGrade.isPresent()) {
                        grade = overwrittenStudentGrade.get().getGrade();
                        gradeTypeNotSupported = false;
                    }

                    if (!gradeTypeNotSupported) {
                        if (StringUtils.isNotBlank(grade)) {
                            BigDecimal assignmentGrade = new BigDecimal(grade);
                            groupMeanSum = groupMeanSum.add(assignmentGrade);
                            gradesLength = gradesLength.add(BigDecimal.ONE);
                            assignmentGradesMap.put(assignment.getId(), assignmentGrade);
                        }
                    }
                }

                if (gradingRules != null) {
                    if (gradingRules.getDropLowest() == null) gradingRules.setDropLowest(0);
                    if (gradingRules.getDropHighest() == null) gradingRules.setDropHighest(0);
                    if (gradingRules.getNeverDrop() == null) gradingRules.setNeverDrop(new ArrayList<>());
                    if (gradingRules.getDropLowest() > 0 || gradingRules.getDropHighest() > 0) {
                        Map<Integer, BigDecimal> lowestGrades = assignmentGradesMap
                                .entrySet().stream()
                                .filter(a -> !(gradingRules.getNeverDrop().contains(a.getKey())))
                                .sorted((a1, a2) -> a1.getValue().compareTo(a2.getValue()))
                                .limit(gradingRules.getDropLowest())
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,  
                                (oldValue, newValue) -> oldValue, LinkedHashMap::new));
                        Map<Integer, BigDecimal> highestGrades = assignmentGradesMap
                                .entrySet().stream()
                                .filter(a -> !(gradingRules.getNeverDrop().contains(a.getKey())))
                                .sorted((a1, a2) -> -a1.getValue().compareTo(a2.getValue()))
                                .limit(gradingRules.getDropHighest())
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,  
                                (oldValue, newValue) -> oldValue, LinkedHashMap::new));
                        Map<Integer, BigDecimal> removeGrades = new HashMap<>();
                        removeGrades.putAll(lowestGrades);
                        removeGrades.putAll(highestGrades);
                        int removed = 0;
                        for (Map.Entry<Integer, BigDecimal> entry : removeGrades.entrySet()) {
                            removed++;
                            groupMeanSum = groupMeanSum.subtract(entry.getValue());
                            gradesLength = gradesLength.subtract(BigDecimal.ONE);
                            Optional<Assignment> assignment = assignmentList.stream().filter(asn -> entry.getKey().equals(asn.getId())).findFirst();
                            if (assignment.isPresent()) {
                                JSONObject assignmentJson = new JSONObject();
                                assignmentJson.put("id", assignment.get().getId());
                                assignmentJson.put("name", assignment.get().getName());
                                if (removed <= gradingRules.getDropLowest()) dropLowestAssignments.put(assignmentJson);
                                else dropHighestAssignments.put(assignmentJson);
                            }
                        }
                    }
                }

            } catch (IOException ex) {
                log.error("Error getting student {} group {} mean", studentIdString, groupId);
            }

            if (calculateFinalGrade && !gradesLength.equals(BigDecimal.ZERO)) {
                BigDecimal groupMean = groupMeanSum.divide(gradesLength, 3, RoundingMode.HALF_UP);
                json.put("mutedAssignments", mutedAssignments);
                json.put("omittedAssignments", omittedAssignments);
                json.put("dropHighestAssignments", dropHighestAssignments);
                json.put("dropLowestAssignments", dropLowestAssignments);
                json.put("grade", new BigDecimal(GradeUtils.roundGrade(groupMean.toString())).toString());
            } else {
                json.put("grade", GRADE_NOT_AVAILABLE);
            }
            return json;

        } else {
            log.error("This user is not allowed to see student {} group {} mean", studentId, groupId);
            throw new GradeException();
        }
    }

    public JSONObject getStudentTotalMean(LtiSession ltiSession, long studentId, boolean isCurrentGrade, String courseId) throws Exception {
        JSONObject json = new JSONObject();
        JSONArray omittedAssignments = new JSONArray();
        JSONArray mutedAssignments = new JSONArray();
        JSONArray dropHighestAssignments = new JSONArray();
        JSONArray dropLowestAssignments = new JSONArray();

        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        String canvasUserId = lld.getCustom().get(LtiConstants.CANVAS_USER_ID);
        String studentIdString = String.valueOf(studentId);
        if (StringUtils.isBlank(courseId)) {
            courseId = ltiSession.getCanvasCourseId();
        }
        CoursePreference coursePreference = courseService.getCoursePreference(courseId);
        // If is final grade and the grade was overrided...
        if (!isCurrentGrade) {
            Optional<StudentFinalGrade> studentFinalGradeOverride = this.getStudentOverridedFinalGrade(courseId, studentIdString);
            if (studentFinalGradeOverride.isPresent()) {
                json.put("grade", studentFinalGradeOverride.get().getGrade());
                return json;
            }
        }
        if (canvasUserId.equals(studentIdString) || securityService.isFaculty(lld.getRolesList())) {
            boolean calculateFinalGrade = true;
            Map<Long, List<BigDecimal>> groupGrades = new HashMap<>();
            Map<Long, Map<Integer, BigDecimal>> groupAssignmentGrades = new HashMap<>();

            try {

                List<Assignment> assignmentList = canvasService.listCourseAssignments(courseId);
                Map<Integer, String> studentSubmissionMap = this.getAssignmentScoresForStudent(assignmentList, studentId);
                for (Assignment assignment : assignmentList) {
                    // Initialize assignment values
                    boolean gradeTypeNotSupported = false;
                    String grade = studentSubmissionMap.get(assignment.getId());
                    String assignmentId = String.valueOf(assignment.getId());
                    boolean omitFromFinalGrade = assignment.isOmitFromFinalGrade();
                    boolean isVisibleForUser = assignment.getAssignmentVisibility().stream().anyMatch(studentIdString::equals);
                    Optional<AssignmentPreference> assignmentPreference = assignmentService.getAssignmentPreference(assignmentId);
                    
                    // The conversion scale can be overriden by assignment preferences.
                    String assignmentConversionScale = coursePreference.getConversionScale();
                    if (assignmentPreference.isPresent() && StringUtils.isNotBlank(assignmentPreference.get().getConversionScale())) {
                        assignmentConversionScale = assignmentPreference.get().getConversionScale();
                    }

                    // The assignment can be muted by assignment preferences.
                    boolean assignmentIsMuted = false;
                    if (StringUtils.isNotBlank(assignment.getMuted())) {
                        assignmentIsMuted = Boolean.valueOf(assignment.getMuted());
                    }
                    if (assignmentPreference.isPresent() && assignmentPreference.get().getMuted() != null) {
                        assignmentIsMuted = assignmentPreference.get().getMuted();
                    }

                    JSONObject assignmentJson = new JSONObject();
                    assignmentJson.put("id", assignmentId);
                    assignmentJson.put("name", assignment.getName());

                    // Skip if assignment is not in the group, assignment is muted and you are getting user current grade, grade is omitted from final grade or assignment possible points is zero
                    if (assignmentIsMuted) mutedAssignments.put(assignmentJson);
                    else if (omitFromFinalGrade) omittedAssignments.put(assignmentJson);
                    if ((assignmentIsMuted && isCurrentGrade) || omitFromFinalGrade || !isVisibleForUser) continue;

                    //Grade conversion logic
                    switch (assignment.getGradingType()) {
                        case GradeUtils.GRADE_TYPE_POINTS:
                            grade = GradeUtils.mapGradeToScale(assignmentConversionScale, grade, assignment.getPointsPossible());
                            break;
                        case GradeUtils.GRADE_TYPE_PERCENT:
                            grade = GradeUtils.mapPercentageToScale(assignmentConversionScale, grade);
                            break;
                        default:
                            grade = GRADE_NOT_AVAILABLE;
                            gradeTypeNotSupported = true;
                            break;
                    }

                    //Get the grade from persistence, get the grade from the API otherwise.
                    Optional<StudentGrade> overwrittenStudentGrade = this.getGradeByAssignmentAndUser(assignmentId, String.valueOf(studentId));
                    if (overwrittenStudentGrade.isPresent()) {
                        grade = overwrittenStudentGrade.get().getGrade();
                        gradeTypeNotSupported = false;
                    }

                    if (!gradeTypeNotSupported) {
                        if (StringUtils.isBlank(grade) || assignmentIsMuted) {
                            // If there is no grade or assignment is muted, and you are getting the final grade for the user:
                            if (!isCurrentGrade) {
                                calculateFinalGrade = false;
                                break;
                            }
                        } else {
                            BigDecimal gradeBigDecimal = new BigDecimal(grade);
                            List<BigDecimal> values = groupGrades.getOrDefault(assignment.getAssignmentGroupId(), new ArrayList<>());
                            values.add(gradeBigDecimal);
                            groupGrades.put(assignment.getAssignmentGroupId(), values);

                            Map<Integer, BigDecimal> valuesGrades = groupAssignmentGrades.getOrDefault(assignment.getAssignmentGroupId(), new HashMap<>());
                            valuesGrades.put(assignment.getId(), gradeBigDecimal);
                            groupAssignmentGrades.put(assignment.getAssignmentGroupId(), valuesGrades);
                        }
                    }
                }

                BigDecimal finalValue = BigDecimal.ZERO;
                if (calculateFinalGrade) {
                    BigDecimal assignmentWeightSum = BigDecimal.ZERO;
                    List<AssignmentGroup> assignmentGroupList = canvasService.listAssignmentGroups(courseId);
                    for (AssignmentGroup assignmentGroup : assignmentGroupList) {
                        Long assignmentGroupId = Long.valueOf(assignmentGroup.getId().toString());
                        GradingRules gradingRules = assignmentGroup.getGradingRules();
                        List<BigDecimal> values = groupGrades.get(assignmentGroupId);
                        Map<Integer, BigDecimal> valuesGrades = groupAssignmentGrades.get(assignmentGroupId);

                        if (gradingRules != null && valuesGrades != null) {
                            if (gradingRules.getDropLowest() == null) gradingRules.setDropLowest(0);
                            if (gradingRules.getDropHighest() == null) gradingRules.setDropHighest(0);
                            if (gradingRules.getNeverDrop() == null) gradingRules.setNeverDrop(new ArrayList<>());
                            if (gradingRules.getDropLowest() > 0 || gradingRules.getDropHighest() > 0) {
                                Map<Integer, BigDecimal> lowestGrades = valuesGrades
                                        .entrySet().stream()
                                        .filter(a -> !(gradingRules.getNeverDrop().contains(a.getKey())))
                                        .sorted((a1, a2) -> a1.getValue().compareTo(a2.getValue()))
                                        .limit(gradingRules.getDropLowest())
                                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,  
                                        (oldValue, newValue) -> oldValue, LinkedHashMap::new));
                                Map<Integer, BigDecimal> highestGrades = valuesGrades
                                        .entrySet().stream()
                                        .filter(a -> !(gradingRules.getNeverDrop().contains(a.getKey())))
                                        .sorted((a1, a2) -> -a1.getValue().compareTo(a2.getValue()))
                                        .limit(gradingRules.getDropHighest())
                                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,  
                                        (oldValue, newValue) -> oldValue, LinkedHashMap::new));
                                Map<Integer, BigDecimal> removeGrades = new HashMap<>();
                                removeGrades.putAll(lowestGrades);
                                removeGrades.putAll(highestGrades);
                                int removed = 0;
                                for (Map.Entry<Integer, BigDecimal> entry : removeGrades.entrySet()) {
                                    removed++;
                                    values.remove(entry.getValue());
                                    Optional<Assignment> assignment = assignmentList.stream().filter(asn -> entry.getKey().equals(asn.getId())).findFirst();
                                    if (assignment.isPresent()) {
                                        JSONObject assignmentJson = new JSONObject();
                                        assignmentJson.put("id", assignment.get().getId());
                                        assignmentJson.put("name", assignment.get().getName());
                                        if (removed <= gradingRules.getDropLowest()) dropLowestAssignments.put(assignmentJson);
                                        else dropHighestAssignments.put(assignmentJson);
                                    }
                                }
                            }
                        }

                        if (values != null && !values.isEmpty()) {
                            BigDecimal totalValues = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                            BigDecimal totalMean = totalValues.divide(new BigDecimal(values.size()), 3, RoundingMode.HALF_UP);
                            BigDecimal value = totalMean.multiply(new BigDecimal(assignmentGroup.getGroupWeight() / 100));
                            finalValue = finalValue.add(value);
                            assignmentWeightSum = assignmentWeightSum.add(new BigDecimal(assignmentGroup.getGroupWeight()));
                        }
                    }
                    if ((assignmentWeightSum.compareTo(new BigDecimal(100)) != 0 && !isCurrentGrade) || assignmentWeightSum.equals(BigDecimal.ZERO)) {
                        json.put("grade", GRADE_NOT_AVAILABLE);
                        return json;
                    }
                    json.put("mutedAssignments", mutedAssignments);
                    json.put("omittedAssignments", omittedAssignments);
                    json.put("dropHighestAssignments", dropHighestAssignments);
                    json.put("dropLowestAssignments", dropLowestAssignments);
                    json.put("grade", new BigDecimal(GradeUtils.roundGrade(finalValue.multiply(new BigDecimal(100)).divide(assignmentWeightSum, 3, RoundingMode.HALF_UP).toString())).toString());
                } else {
                    json.put("grade", GRADE_NOT_AVAILABLE);
                }

            } catch (IOException ex) {
                log.error("Error getting student {} total mean", studentId);
                json.put("grade", GRADE_NOT_AVAILABLE);
            }
            return json;

        } else {
            log.error("This user is not allowed to see the total mean of the student with id {}", studentId);
            throw new GradeException();
        }
    }

    private Map<Integer, String> getAssignmentScoresForStudent(List<Assignment> assignmentList, long studentId) throws IOException{
        Map<Integer, String> studentSubmissionMap = new HashMap<>();
        for (Assignment assignment : assignmentList) {
            String assignmentScore = null;
            String assignmentId = String.valueOf(assignment.getId());
            String studentIdString = String.valueOf(studentId);
            StudentCanvasGrade studentCanvasGrade = this.getCanvasGradeByAssignmentAndUser(assignmentId, studentIdString).orElse(null);
            if (studentCanvasGrade != null) {
                assignmentScore = studentCanvasGrade.getGrade();
            }
            studentSubmissionMap.put(assignment.getId(), assignmentScore);
        }
        return studentSubmissionMap;
    }

    public Optional<StudentFinalGrade> getStudentOverridedFinalGrade(String courseId, String studentId) {
        return finalGradeRepository.findById(new StudentFinalGradeId(courseId, studentId));
    }

    public void syncCourseGrades(String courseId) {
        log.info("Syncing grades from course {}", courseId);
        StopWatch stopwatch = StopWatch.createStarted();
        List<StudentCanvasGrade> canvasGradeList = new ArrayList<>();
        try {
            // This trick is a courseId checker, it fails if the course does not exist in the Canvas instance, ideally when mixing instances or when a course is deleted.
            canvasService.getSingleCourse(courseId);
            // For each course present in the system, gets the assignments using the API.
            canvasService.listCourseAssignments(courseId).forEach(assignment -> {
                Integer assignmentId = assignment.getId();
                try {
                    // For each assignment present in the course, gets the course submissions.
                    canvasService.getCourseSubmissions(courseId, assignmentId).forEach(assignmentSubmission -> {
                        // For each grade present in the submission, stores the grade in the local DB.
                        String userId = assignmentSubmission.getUserId().toString();
                        String grade = assignmentSubmission.getScore() != null ? assignmentSubmission.getScore().toString() : null;
                        log.debug("Dumping grades from submission {}, user {}, assignment {} and course {}, grade is {}.", assignmentSubmission.getId(), userId, assignmentId, courseId, grade);
                        if (StringUtils.isBlank(grade)) {
                            return;
                        }
                        StudentCanvasGrade studentCanvasGrade = new StudentCanvasGrade();
                        studentCanvasGrade.setUserId(userId);
                        studentCanvasGrade.setGrade(grade);
                        studentCanvasGrade.setAssignmentId(assignment.getId().toString());
                        canvasGradeList.add(studentCanvasGrade);
                    });
                } catch (Exception e) {
                    log.error("Fatal error getting course submissions from course {} and assignment {}. {}", courseId, assignmentId, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Fatal error getting course {}, skipping. {}", courseId, e.getMessage());
        }
        log.info("All the grades from the course {} have been dumped, {} grades in total.", courseId, canvasGradeList.size());
        this.saveCanvasGradeInBatch(canvasGradeList);
        stopwatch.stop();
        log.info("Syncing complete for course {} in {}.", courseId, stopwatch);
    }

    public void saveCanvasGradeInBatch(List<StudentCanvasGrade> studentCanvasGradeList) {
        log.info("Persisting {} Canvas Grades in batches of {} elements.", studentCanvasGradeList.size(), batchSize);
        StopWatch stopwatch = StopWatch.createStarted();
        int listSize = studentCanvasGradeList.size();
        IntStream.range(0, (listSize + batchSize - 1) / batchSize)
            .mapToObj(i -> studentCanvasGradeList.subList(i * batchSize, Math.min(listSize, (i + 1) * batchSize)))
            .forEach(batch -> {
                canvasGradeRepository.saveAll(batch);
            });
        stopwatch.stop();
        log.info("{} grades persisted successfully in {}.", studentCanvasGradeList.size(), stopwatch);
    }

}
