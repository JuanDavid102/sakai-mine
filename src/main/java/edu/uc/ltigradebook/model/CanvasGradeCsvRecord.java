package edu.uc.ltigradebook.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.csv.CSVRecord;

/**
 * Class for reading Submissions from the Canvas submission CSV report.
 */
@Getter
@ToString
@EqualsAndHashCode
public class CanvasGradeCsvRecord {

    private String canvasUserId;
    private String sisUserId;
    private String userName;
    private String canvasCourseId;
    private String sisCourseId;
    private String courseName;
    private String assignmentId;
    private String assignmentName;
    private String submissionDate;
    private String gradedDate;
    private String score;
    private String pointsPossible;
    private String submissionId;
    private String workflowState;
    private String excused;
    private String postToSis;
    private String canvasSectionId;
    private String sisSectionId;
    private String dueAt;
    private String postedAt;
    private String assignmentWorkflowState;
    private String gradingPeriodId;

    public CanvasGradeCsvRecord(CSVRecord record) {
        canvasUserId = record.get("canvas user id");
        sisUserId = record.get("sis user id");
        userName = record.get("user name");
        canvasCourseId = record.get("canvas course id");
        sisCourseId = record.get("sis course id");
        courseName = record.get("course name");
        assignmentId = record.get("assignment id");
        assignmentName = record.get("assignment name");
        submissionDate = record.get("submission date");
        gradedDate = record.get("graded date");
        score = record.get("score");
        pointsPossible = record.get("points possible");
        submissionId = record.get("submission id");
        workflowState = record.get("workflow state");
        excused = record.get("excused");
        postToSis = record.get("post to sis");
        canvasSectionId = record.get("canvas section id");
        sisSectionId = record.get("sis section id");
        dueAt = record.get("due at");
        postedAt = record.get("posted at");
        assignmentWorkflowState = record.get("assignment workflow state");
        gradingPeriodId = record.get("grading period id");
    }

}
