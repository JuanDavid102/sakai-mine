package edu.uc.ltigradebook.service;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import edu.ksu.canvas.model.report.AccountReport;
import edu.uc.ltigradebook.model.AccountReportType;
import edu.uc.ltigradebook.model.ReportStatus;
import lombok.extern.slf4j.Slf4j;

/**
 * Account Report Service was built to deal with Canvas Account Report requests dealing with Account Report statuses.
 */
@Service
@Slf4j
public class AccountReportService {

    @Autowired
    private CanvasAPIServiceWrapper canvasService;

    @Value("#{T(java.time.Duration).parse('${canvas.accountreport.max.duration:PT3H}')}")
    private Duration maxDuration;

    @Value("#{T(java.time.Duration).parse('${canvas.accountreport.sleep.interval:PT10M}')}")
    private Duration sleepInterval;

   /**
    * Gets the Student Submission Report from Canvas
    * Requests an account report from Canvas as CSV Records.
    * @returns an URI object containing the CSV report.
    */
    public URI getCanvasStudentSubmissionReport() throws Exception {
        return this.getAccountReport(AccountReportType.proserv_student_submissions_csv);
    }

   /**
    * Requests an account report from Canvas as CSV Records.
    * @returns an URI object containing the CSV report.
    */
    public URI getAccountReport(AccountReportType accountReportType) throws Exception {
        AccountReport accountReport = canvasService.startAccountReport(accountReportType).orElseThrow(() -> new RuntimeException("Failed to request the Canvas account report."));
        return this.waitForAccountReport(accountReport, accountReportType);
    }

   /**
    * Waits for an account report completion.
    * @param accountReport The account report to wait for.
    * @param accountReportType The type of the report
    * @throws Exception
    * @returns an URI object containing the report URI from Canvas.
    */
    private URI waitForAccountReport(AccountReport accountReport, AccountReportType accountReportType) throws Exception {
        AccountReport updatedAccountReport;
        ReportStatus reportStatus;
        Instant termination = Instant.now().plus(maxDuration);
        URI reportURI;

        do {
            if (Instant.now().isAfter(termination)) {
                throw new InterruptedException("We didn't get a report after waiting: " + maxDuration);
            }
            Thread.sleep(sleepInterval.toMillis());
            updatedAccountReport = canvasService.getAccountReportStatus(accountReport.getId(), accountReportType).orElseThrow(() -> new RuntimeException("Failed to get report status"));
            reportStatus = ReportStatus.from(updatedAccountReport.getStatus());
            log.info("Awaiting for the account report {}...", accountReport.getId());
        } while (!reportStatus.isFinished());

        switch(reportStatus) {
            case COMPLETE:
                if (updatedAccountReport.getAttachment() != null) {
                    reportURI = updatedAccountReport.getAttachment().getUrl();
                    log.info("Account report available on this url {}", reportURI.toString());
                } else {
                    throw new IllegalStateException("Report is complete but there is no URL. Report ID: " + updatedAccountReport.getId());
                }
                break;
            default: 
                throw new IllegalStateException("Report finished and isn't complete, the status is " + reportStatus);
        }

        return reportURI;
    }

}
