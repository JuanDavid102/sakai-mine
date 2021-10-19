package edu.uc.ltigradebook.model;

public enum ReportStatus {
    CREATED(false),
    RUNNING(false),
    COMPILING(false),
    COMPLETE(true),
    ERROR(true),
    ABORTED(true),
    DELETED(true),
    // Unknown status
    UNKNOWN(true),
    // No status supplied
    NONE(true);

    private boolean finished;

    ReportStatus(boolean finished) {
        this.finished = finished;
    }

    public static ReportStatus from(String value) {
        if (value != null) {
            try {
                return ReportStatus.valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return UNKNOWN;
            }
        }
        return NONE;
    }

    /**
     * Has this report finished (stopped updating).
     *
     * @return true if it's stopped.
     */
    public boolean isFinished() {
        return finished;
    }
}
