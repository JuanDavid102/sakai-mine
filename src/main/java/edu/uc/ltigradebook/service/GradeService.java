package edu.uc.ltigradebook.service;

import edu.ksu.canvas.model.assignment.Assignment;
import edu.ksu.canvas.model.assignment.AssignmentGroup;
import edu.ksu.canvas.model.assignment.GradingRules;
import edu.ksu.canvas.model.assignment.Submission;
import edu.ksu.lti.launch.model.LtiLaunchData;
import edu.ksu.lti.launch.model.LtiSession;
import edu.uc.ltigradebook.constants.LtiConstants;
import edu.uc.ltigradebook.entity.AssignmentPreference;
import edu.uc.ltigradebook.entity.CoursePreference;
import edu.uc.ltigradebook.entity.StudentGrade;
import edu.uc.ltigradebook.entity.StudentGradeId;
import edu.uc.ltigradebook.exception.GradeException;
import edu.uc.ltigradebook.repository.GradeRepository;
import edu.uc.ltigradebook.util.GradeUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;
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
    private CanvasAPIServiceWrapper canvasService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private SecurityService securityService;

    @Autowired
    private AssignmentService assignmentService;

    private static final String GRADE_NOT_AVAILABLE = "-";

    public Optional<StudentGrade> getGradeByAssignmentAndUser(String assignmentId, String userId) {
        log.debug("Getting a student grade by assignment {} and user {}.", assignmentId, userId);
        return gradeRepository.findById(new StudentGradeId(assignmentId, userId));
    }

    public void saveGrade(StudentGrade studentGrade) {
        log.info("Saving grade {} for the user {} and assignment {}.", studentGrade.getGrade(), studentGrade.getUserId(), studentGrade.getAssignmentId());
        gradeRepository.save(studentGrade);
        log.info("Grade saved successfully");
    }

    public void deleteGrade(StudentGrade studentGrade) {
        log.info("Deleting grade for the user {} and assignment {}.", studentGrade.getUserId(), studentGrade.getAssignmentId());
        gradeRepository.delete(studentGrade);
        log.info("Grade deleted successfully");
    }

    public long getGradeCount() {
        log.debug("Getting the grade count from the table.");
        return gradeRepository.count();
    }

    public long getGradedUserCount() {
        log.debug("Getting the graded user count from the table.");
        return gradeRepository.getGradedUserCount();
    }

    public JSONObject getStudentGroupMean(LtiSession ltiSession, Long groupId, Integer studentId, String courseId) throws GradeException {
        JSONObject json = new JSONObject();
        JSONArray omittedAssignments = new JSONArray();
        JSONArray mutedAssignments = new JSONArray();
        JSONArray dropHighestAssignments = new JSONArray();
        JSONArray dropLowestAssignments = new JSONArray();

        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        String canvasUserId = lld.getCustom().get(LtiConstants.CANVAS_USER_ID);
        if (StringUtils.isBlank(courseId)) {
            courseId = ltiSession.getCanvasCourseId();
        }
        CoursePreference coursePreference = courseService.getCoursePreference(courseId);
        if (canvasUserId.equals(studentId.toString()) || securityService.isFaculty(lld.getRolesList())) {
            BigDecimal groupMeanSum = BigDecimal.ZERO;
            BigDecimal gradesLength = BigDecimal.ZERO;
            boolean calculateFinalGrade = true;

            try {
                List<Assignment> assignmentList = canvasService.listCourseAssignments(courseId);
                List<Assignment> assignmentsInGroup = assignmentList.stream().filter(asn -> groupId.equals(asn.getAssignmentGroupId())).collect(Collectors.toList());
                Map<Integer, BigDecimal> assignmentGradesMap = new HashMap<>();
                Map<Integer, Submission> studentSubmissionMap = getAssignmentSubmissionsForStudent(assignmentsInGroup, studentId);
                List<AssignmentGroup> assignmentGroupList = canvasService.listAssignmentGroups(courseId);
                Optional<AssignmentGroup> assignmentGroupOptional = assignmentGroupList.stream().filter(ag -> groupId.equals(Long.valueOf(ag.getId().toString()))).findAny();
                GradingRules gradingRules = assignmentGroupOptional.isPresent() ? (assignmentGroupOptional.get().getGradingRules() != null ? assignmentGroupOptional.get().getGradingRules() : null) : null;
                for (Assignment assignment : assignmentsInGroup) {
                    Submission submission = studentSubmissionMap.get(assignment.getId());
                    if(submission == null) continue;
                    String assignmentId = String.valueOf(assignment.getId());
                    Optional<AssignmentPreference> assignmentPref = assignmentService.getAssignmentPreference(assignment.getId().toString());
                    boolean assignmentIsMuted = false;
                    if (StringUtils.isNotBlank(assignment.getMuted())) {
                        assignmentIsMuted = Boolean.valueOf(assignment.getMuted());
                    }
                    if (assignmentPref.isPresent() && assignmentPref.get().getMuted() != null) {
                        assignmentIsMuted = assignmentPref.get().getMuted();
                    }

                    JSONObject assignmentJson = new JSONObject();
                    assignmentJson.put("id", assignmentId);
                    assignmentJson.put("name", assignment.getName());

                    boolean omitFromFinalGrade = assignment.isOmitFromFinalGrade();
                    boolean isZeroPoints = assignment.getPointsPossible() == null || assignment.getPointsPossible().equals(new Double(0));
                    boolean isVisibleForUser = assignment.getAssignmentVisibility().stream().anyMatch(studentId.toString()::equals);

                    // Skip if assignment is muted, grade is omitted from final grade or assignment possible points is zero
                    if (assignmentIsMuted) mutedAssignments.put(assignmentJson);
                    else if (omitFromFinalGrade) omittedAssignments.put(assignmentJson);
                    if (assignmentIsMuted || omitFromFinalGrade || isZeroPoints || !isVisibleForUser) continue;

                    String grade = submission.getGrade();
                    boolean gradeTypeNotSupported = false;

                    String assignmentConversionScale = coursePreference.getConversionScale();
                    Optional<AssignmentPreference> assignmentPreference = assignmentService.getAssignmentPreference(assignmentId);
                    if (assignmentPreference.isPresent() && StringUtils.isNotBlank(assignmentPreference.get().getConversionScale())) {
                        assignmentConversionScale = assignmentPreference.get().getConversionScale();
                    }

                    //Grade conversion logic
                    switch (assignment.getGradingType()) {
                        case GradeUtils.GRADE_TYPE_POINTS:
                            grade = GradeUtils.mapGradeToScale(assignmentConversionScale, grade, assignment.getPointsPossible() != null ? assignment.getPointsPossible().toString() : null);
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
                        if (StringUtils.isBlank(grade)) {
                            calculateFinalGrade = false;
                            break;
                        } else {
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
                log.error("Error getting student {} group {} mean", studentId, groupId);
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

    public JSONObject getStudentTotalMean(LtiSession ltiSession, Integer studentId, boolean isCurrentGrade, String courseId) throws GradeException {
        JSONObject json = new JSONObject();
        JSONArray omittedAssignments = new JSONArray();
        JSONArray mutedAssignments = new JSONArray();
        JSONArray dropHighestAssignments = new JSONArray();
        JSONArray dropLowestAssignments = new JSONArray();

        LtiLaunchData lld = ltiSession.getLtiLaunchData();
        String canvasUserId = lld.getCustom().get(LtiConstants.CANVAS_USER_ID);
        if (StringUtils.isBlank(courseId)) {
            courseId = ltiSession.getCanvasCourseId();
        }
        CoursePreference coursePreference = courseService.getCoursePreference(courseId);
        if (canvasUserId.equals(studentId.toString()) || securityService.isFaculty(lld.getRolesList())) {
            boolean calculateFinalGrade = true;
            Map<Long, List<BigDecimal>> groupGrades = new HashMap<>();
            Map<Long, Map<Integer, BigDecimal>> groupAssignmentGrades = new HashMap<>();

            try {

                List<Assignment> assignmentList = canvasService.listCourseAssignments(courseId);
                Map<Integer, Submission> studentSubmissionMap = getAssignmentSubmissionsForStudent(assignmentList, studentId);
                for (Assignment assignment : assignmentList) {
                    Submission submission = studentSubmissionMap.get(assignment.getId());
                    if(submission == null) continue;
                    String assignmentId = String.valueOf(assignment.getId());
                    Optional<AssignmentPreference> assignmentPref = assignmentService.getAssignmentPreference(assignment.getId().toString());

                    boolean assignmentIsMuted = false;
                    if (StringUtils.isNotBlank(assignment.getMuted())) {
                        assignmentIsMuted = Boolean.valueOf(assignment.getMuted());
                    }
                    if (assignmentPref.isPresent() && assignmentPref.get().getMuted() != null) {
                        assignmentIsMuted = assignmentPref.get().getMuted();
                    }

                    JSONObject assignmentJson = new JSONObject();
                    assignmentJson.put("id", assignmentId);
                    assignmentJson.put("name", assignment.getName());

                    boolean omitFromFinalGrade = assignment.isOmitFromFinalGrade();
                    boolean isZeroPoints = assignment.getPointsPossible() == null || assignment.getPointsPossible().equals(new Double(0));
                    boolean isVisibleForUser = assignment.getAssignmentVisibility().stream().anyMatch(studentId.toString()::equals);

                    // Skip if assignment is not in the group, assignment is muted, grade is omitted from final grade or assignment possible points is zero
                    if (assignmentIsMuted) mutedAssignments.put(assignmentJson);
                    else if (omitFromFinalGrade) omittedAssignments.put(assignmentJson);
                    if (assignmentIsMuted || omitFromFinalGrade || isZeroPoints || !isVisibleForUser) continue;

                    String grade = submission.getGrade();
                    boolean gradeTypeNotSupported = false;

                    String assignmentConversionScale = coursePreference.getConversionScale();
                    Optional<AssignmentPreference> assignmentPreference = assignmentService.getAssignmentPreference(assignmentId);
                    if (assignmentPreference.isPresent() && StringUtils.isNotBlank(assignmentPreference.get().getConversionScale())) {
                        assignmentConversionScale = assignmentPreference.get().getConversionScale();
                    }

                    //Grade conversion logic
                    switch (assignment.getGradingType()) {
                        case GradeUtils.GRADE_TYPE_POINTS:
                            grade = GradeUtils.mapGradeToScale(assignmentConversionScale, grade, assignment.getPointsPossible() != null ? assignment.getPointsPossible().toString() : null);
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
                        if (StringUtils.isBlank(grade)) {
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
                        GradingRules gradingRules = assignmentGroup.getGradingRules();
                        List<BigDecimal> values = groupGrades.get(new Long(assignmentGroup.getId()));
                        Map<Integer, BigDecimal> valuesGrades = groupAssignmentGrades.get(new Long(assignmentGroup.getId()));

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
                    if (assignmentWeightSum.equals(BigDecimal.ZERO)) {
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

    private Map<Integer, Submission> getAssignmentSubmissionsForStudent(List<Assignment> assignmentList, Integer studentId) throws IOException{
        Map<Integer, Submission> studentSubmissionMap = new HashMap<Integer, Submission>();
        for (Assignment assignment : assignmentList) {
            List<Submission> assignmentSubmissions = canvasService.getCourseSubmissions(assignment.getCourseId(), assignment.getId());
            Optional<Submission> submissionOptional = assignmentSubmissions.stream().filter(submission -> submission.getUserId().equals(studentId)).findAny();
            studentSubmissionMap.put(assignment.getId(), submissionOptional.isPresent() ? submissionOptional.get() : null);                	
        }
        return studentSubmissionMap;
    }

}
