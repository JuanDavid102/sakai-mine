package edu.uc.ltigradebook.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.apache.commons.lang3.StringUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GradeUtils {
    
    public static final String GRADE_TYPE_POINTS = "points";
    public static final String GRADE_TYPE_PERCENT = "percent";
    private static final String PERCENT_SYMBOL = "%";
    private static final String MAX_PERCENT = "100.0";

    private static final BigDecimal MIN_GRADE = new BigDecimal("1.0");
    private static final BigDecimal MAX_GRADE = new BigDecimal("7.0");
    private static final BigDecimal PASS_GRADE = new BigDecimal("4.0");

    public static boolean isValidGrade(String input) {
        if (isValidDouble(input)) {
            BigDecimal bigDecimalInput = new BigDecimal(input);
            return bigDecimalInput.compareTo(MIN_GRADE) >= 0 && bigDecimalInput.compareTo(MAX_GRADE) <= 0;
        } else {
            return false;
        }
    }

    public static boolean isValidDouble(String input) {
        try {
            Double.parseDouble(input);
            return true;
        } catch(Exception ex) {
            return false;
        }
    }
   
    public static String mapGradeToScale(String scalePassPercent, String earnedPoints, String totalPoints) {
        if(!isValidDouble(earnedPoints) || !isValidDouble(totalPoints)) {
            return StringUtils.EMPTY;
        }

        BigDecimal convertedGrade = new BigDecimal(0);
        BigDecimal minimumPoints = new BigDecimal(0);
        BigDecimal PASS_PERCENT = new BigDecimal(scalePassPercent);

        try {
            BigDecimal totalDecimalPoints = new BigDecimal(totalPoints);
            if(minimumPoints.compareTo(totalDecimalPoints) == 0) {
                return StringUtils.EMPTY;
            }

            BigDecimal earnedDecimalPoints = new BigDecimal(earnedPoints);
            BigDecimal passPoints = totalDecimalPoints.multiply(PASS_PERCENT);
    
            // If earned points are less than passpoints...
            if (earnedDecimalPoints.compareTo(passPoints) < 0) {
                convertedGrade = (PASS_GRADE.subtract(MIN_GRADE))
                        .multiply(earnedDecimalPoints
                        .divide(passPoints, 3, RoundingMode.HALF_UP))
                        .add(MIN_GRADE);
            } else {
                convertedGrade = (PASS_GRADE.subtract(MIN_GRADE))
                        .multiply(earnedDecimalPoints.subtract(passPoints)
                        .divide(totalDecimalPoints.subtract(passPoints), 3, RoundingMode.HALF_UP))
                        .add(PASS_GRADE);
            }
        } catch(Exception ex) {
            log.error("Fatal error mapping the grade to the {} scale, {} earnedPoints, {} totalPoints.", PASS_PERCENT, earnedPoints, totalPoints, ex);
            return StringUtils.EMPTY;
        }

        convertedGrade = convertedGrade.setScale(1,  RoundingMode.HALF_UP);
        log.debug("Mapping grade to the {} scale, {} earnedPoints, {} totalPoints, converted grade {}.", PASS_PERCENT, earnedPoints, totalPoints, convertedGrade);
        return convertedGrade.toString();
    }

    public static String mapPercentageToScale(String scalePassPercent, String earnedPercentage) {
        return mapGradeToScale(scalePassPercent, StringUtils.replace(earnedPercentage, PERCENT_SYMBOL, StringUtils.EMPTY), MAX_PERCENT);
    }

    public static String roundGrade(String gradeValue) {
        if(!isValidDouble(gradeValue)) {
            return StringUtils.EMPTY;
        }

        BigDecimal inputGradeValue = new BigDecimal(gradeValue);
        inputGradeValue = inputGradeValue.setScale(1,  RoundingMode.HALF_UP);
        return inputGradeValue.toString();
    }

}
