package edu.uc.ltigradebook.model;

/** 
 * The enum contains all the Account Report Types available in Canvas for the root account.
 * See https://canvas.instructure.com/doc/api/account_reports.html#method.account_reports.available_reports
 */
public enum AccountReportType {

    grade_export_csv,
    mgp_grade_export_csv,
    last_user_access_csv,
    last_enrollment_activity_csv,
    outcome_export_csv,
    provisioning_csv,
    recently_deleted_courses_csv,
    sis_export_csv,
    student_assignment_outcome_map_csv,
    students_with_no_submissions_csv,
    unpublished_courses_csv,
    public_courses_csv,
    course_storage_csv,
    unused_courses_csv,
    zero_activity_csv,
    user_access_tokens_csv,
    lti_report_csv,
    user_course_access_log_csv,
    proservices_provisioning_csv,
    proserv_assignment_export_csv,
    proserv_student_submissions_csv

}
