package edu.uc.ltigradebook.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import edu.uc.ltigradebook.constants.ScaleConstants;
import edu.uc.ltigradebook.util.GradeUtils;

// All the scale tests have been implemented using the website https://escaladenotas.cl
class GradeUtilsTest {

    @Test
    public void testIsValidDouble() {
        assertTrue(GradeUtils.isValidDouble("8.9"));
        assertTrue(GradeUtils.isValidDouble("8.94455664646"));
        assertTrue(GradeUtils.isValidDouble("-8.94455664646"));
        assertTrue(GradeUtils.isValidDouble("5"));
        assertFalse(GradeUtils.isValidDouble("--8.94455664646"));
        assertFalse(GradeUtils.isValidDouble("8.9445566aaaaaaaaa"));
        assertFalse(GradeUtils.isValidDouble("8,9"));
        assertFalse(GradeUtils.isValidDouble(null));
        assertFalse(GradeUtils.isValidDouble(""));
        assertFalse(GradeUtils.isValidDouble("aaaa"));
        assertFalse(GradeUtils.isValidDouble("A"));
    }

    @Test
    public void testIsValidGrade() {
        assertTrue(GradeUtils.isValidGrade("1"));
        assertTrue(GradeUtils.isValidGrade("1.0"));
        assertTrue(GradeUtils.isValidGrade("5"));
        assertTrue(GradeUtils.isValidGrade("5.5"));
        assertTrue(GradeUtils.isValidGrade("7"));
        assertTrue(GradeUtils.isValidGrade("7.0"));
        assertTrue(GradeUtils.isValidGrade("1.000000000000000000"));
        assertTrue(GradeUtils.isValidGrade("6.999999999999999999"));
        assertFalse(GradeUtils.isValidGrade("0.999999999999999999"));
        assertFalse(GradeUtils.isValidGrade("0.9"));
        assertFalse(GradeUtils.isValidGrade("7.1"));
        assertFalse(GradeUtils.isValidGrade("-7.0"));
        assertFalse(GradeUtils.isValidGrade("-1.0"));
        assertFalse(GradeUtils.isValidGrade("8.9"));
        assertFalse(GradeUtils.isValidGrade("8.94455664646"));
        assertFalse(GradeUtils.isValidGrade("8.9445566aaaaaaaaa"));
        assertFalse(GradeUtils.isValidGrade("8,9"));
        assertFalse(GradeUtils.isValidGrade(null));
        assertFalse(GradeUtils.isValidGrade(""));
        assertFalse(GradeUtils.isValidGrade("aaaa"));
        assertFalse(GradeUtils.isValidGrade("A"));
    }

    @Test
    public void testRoundGrade() {
        assertEquals(GradeUtils.roundGrade("1"), "1.0");
        assertEquals(GradeUtils.roundGrade("0.9999999999999"), "1.0");
        assertEquals(GradeUtils.roundGrade("6.999999999999"), "7.0");
        assertEquals(GradeUtils.roundGrade("0.90000000000"), "0.9");
        assertEquals(GradeUtils.roundGrade("1.4999999999999999"), "1.5");
        assertEquals(GradeUtils.roundGrade("1.400000000000000"), "1.4");
        assertEquals(GradeUtils.roundGrade("3.14159265359"), "3.1");
    }

    @Test
    public void testMapGradeToScaleNullValues() {
        String earnedPoints = "10.0";
        String scalePassPercent = ScaleConstants.DEFAULT;
        Double totalPoints = Double.valueOf(100);
        assertEquals(GradeUtils.mapGradeToScale(null, earnedPoints, totalPoints), "");
        assertEquals(GradeUtils.mapGradeToScale(scalePassPercent, null, totalPoints), "");
        assertEquals(GradeUtils.mapGradeToScale(scalePassPercent, earnedPoints, null), "");
        assertEquals(GradeUtils.mapGradeToScale(scalePassPercent, earnedPoints, null), "");
    }

    @Test
    public void testMapGradeToScale() {
        String scalePassPercent = ScaleConstants.DEFAULT;
        Double totalPoints = Double.valueOf(100);
        this.build60Scale().entrySet().forEach(grade -> {
            assertEquals(GradeUtils.mapGradeToScale(scalePassPercent, grade.getKey(), totalPoints), grade.getValue());
        });
    }

    @Test
    public void testMapGradeToScale50() {
        String scalePassPercent = ScaleConstants.FIFTY;
        Double totalPoints = Double.valueOf(100);
        this.build50Scale().entrySet().forEach(grade -> {
            assertEquals(GradeUtils.mapGradeToScale(scalePassPercent, grade.getKey(), totalPoints), grade.getValue());
        });
    }

    @Test
    public void testMapGradeToScale60() {
        String scalePassPercent = ScaleConstants.SIXTY;
        Double totalPoints = Double.valueOf(100);
        this.build60Scale().entrySet().forEach(grade -> {
            assertEquals(GradeUtils.mapGradeToScale(scalePassPercent, grade.getKey(), totalPoints), grade.getValue());
        });
    }

    @Test
    public void testMapGradeToScale70() {
        String scalePassPercent = ScaleConstants.SEVENTY;
        Double totalPoints = Double.valueOf(100);
        this.build70Scale().entrySet().forEach(grade -> {
            assertEquals(GradeUtils.mapGradeToScale(scalePassPercent, grade.getKey(), totalPoints), grade.getValue());
        });
    }

    @Test
    public void testMapGradeToScale80() {
        String scalePassPercent = ScaleConstants.EIGHTY;
        Double totalPoints = Double.valueOf(100);
        this.build80Scale().entrySet().forEach(grade -> {
            assertEquals(GradeUtils.mapGradeToScale(scalePassPercent, grade.getKey(), totalPoints), grade.getValue());
        });
    }

    @Test
    public void testMapGradeToScale90() {
        String scalePassPercent = ScaleConstants.NINETY;
        Double totalPoints = Double.valueOf(100);
        this.build90Scale().entrySet().forEach(grade -> {
            assertEquals(GradeUtils.mapGradeToScale(scalePassPercent, grade.getKey(), totalPoints), grade.getValue());
        });
    }

    @Test
    public void testMapPercentageToScale() {
        String scalePassPercent = ScaleConstants.DEFAULT;
        this.build60Scale().entrySet().forEach(grade -> {
            assertEquals(GradeUtils.mapPercentageToScale(scalePassPercent, grade.getKey()), grade.getValue());
        });
    }

    @Test
    public void testMapPercentageToScale50() {
        String scalePassPercent = ScaleConstants.FIFTY;
        this.build50Scale().entrySet().forEach(grade -> {
            assertEquals(GradeUtils.mapPercentageToScale(scalePassPercent, grade.getKey()), grade.getValue());
        });
    }

    @Test
    public void testMapPercentageToScale60() {
        String scalePassPercent = ScaleConstants.SIXTY;
        this.build60Scale().entrySet().forEach(grade -> {
            assertEquals(GradeUtils.mapPercentageToScale(scalePassPercent, grade.getKey()), grade.getValue());
        });
    }

    @Test
    public void testMapPercentageToScale70() {
        String scalePassPercent = ScaleConstants.SEVENTY;
        this.build70Scale().entrySet().forEach(grade -> {
            assertEquals(GradeUtils.mapPercentageToScale(scalePassPercent, grade.getKey()), grade.getValue());
        });
    }

    @Test
    public void testMapPercentageToScale80() {
        String scalePassPercent = ScaleConstants.EIGHTY;
        this.build80Scale().entrySet().forEach(grade -> {
            assertEquals(GradeUtils.mapPercentageToScale(scalePassPercent, grade.getKey()), grade.getValue());
        });
    }

    @Test
    public void testMapPercentageToScale90() {
        String scalePassPercent = ScaleConstants.NINETY;
        this.build90Scale().entrySet().forEach(grade -> {
            assertEquals(GradeUtils.mapPercentageToScale(scalePassPercent, grade.getKey()), grade.getValue());
        });
    }

    private Map<String, String> build60Scale() {
        Map<String, String> map = new HashMap<>();
        map.put("0.0","1.0");
        map.put("1.0","1.1");
        map.put("2.0","1.1");
        map.put("3.0","1.2");
        map.put("4.0","1.2");
        map.put("5.0","1.3");
        map.put("6.0","1.3");
        map.put("7.0","1.4");
        map.put("8.0","1.4");
        map.put("9.0","1.5");
        map.put("10.0","1.5");
        map.put("11.0","1.6");
        map.put("12.0","1.6");
        map.put("13.0","1.7");
        map.put("14.0","1.7");
        map.put("15.0","1.8");
        map.put("16.0","1.8");
        map.put("17.0","1.9");
        map.put("18.0","1.9");
        map.put("19.0","2.0");
        map.put("20.0","2.0");
        map.put("21.0","2.1");
        map.put("22.0","2.1");
        map.put("23.0","2.2");
        map.put("24.0","2.2");
        map.put("25.0","2.3");
        map.put("26.0","2.3");
        map.put("27.0","2.4");
        map.put("28.0","2.4");
        map.put("29.0","2.5");
        map.put("30.0","2.5");
        map.put("31.0","2.6");
        map.put("32.0","2.6");
        map.put("33.0","2.7");
        map.put("34.0","2.7");
        map.put("35.0","2.8");
        map.put("36.0","2.8");
        map.put("37.0","2.9");
        map.put("38.0","2.9");
        map.put("39.0","3.0");
        map.put("40.0","3.0");
        map.put("41.0","3.1");
        map.put("42.0","3.1");
        map.put("43.0","3.2");
        map.put("44.0","3.2");
        map.put("45.0","3.3");
        map.put("46.0","3.3");
        map.put("47.0","3.4");
        map.put("48.0","3.4");
        map.put("49.0","3.5");
        map.put("50.0","3.5");
        map.put("51.0","3.6");
        map.put("52.0","3.6");
        map.put("53.0","3.7");
        map.put("54.0","3.7");
        map.put("55.0","3.8");
        map.put("56.0","3.8");
        map.put("57.0","3.9");
        map.put("58.0","3.9");
        map.put("59.0","4.0");
        map.put("60.0","4.0");
        map.put("61.0","4.1");
        map.put("62.0","4.2");
        map.put("63.0","4.2");
        map.put("64.0","4.3");
        map.put("65.0","4.4");
        map.put("66.0","4.5");
        map.put("67.0","4.5");
        map.put("68.0","4.6");
        map.put("69.0","4.7");
        map.put("70.0","4.8");
        map.put("71.0","4.8");
        map.put("72.0","4.9");
        map.put("73.0","5.0");
        map.put("74.0","5.1");
        map.put("75.0","5.1");
        map.put("76.0","5.2");
        map.put("77.0","5.3");
        map.put("78.0","5.4");
        map.put("79.0","5.4");
        map.put("80.0","5.5");
        map.put("81.0","5.6");
        map.put("82.0","5.7");
        map.put("83.0","5.7");
        map.put("84.0","5.8");
        map.put("85.0","5.9");
        map.put("86.0","6.0");
        map.put("87.0","6.0");
        map.put("88.0","6.1");
        map.put("89.0","6.2");
        map.put("90.0","6.3");
        map.put("91.0","6.3");
        map.put("92.0","6.4");
        map.put("93.0","6.5");
        map.put("94.0","6.6");
        map.put("95.0","6.6");
        map.put("96.0","6.7");
        map.put("97.0","6.8");
        map.put("98.0","6.9");
        map.put("99.0","6.9");
        map.put("100.0","7.0");
        return map;
    }

    private Map<String, String> build50Scale() {
        Map<String, String> map = new HashMap<>();
        map.put("0.0","1.0");
        map.put("1.0","1.1");
        map.put("2.0","1.1");
        map.put("3.0","1.2");
        map.put("4.0","1.2");
        map.put("5.0","1.3");
        map.put("6.0","1.4");
        map.put("7.0","1.4");
        map.put("8.0","1.5");
        map.put("9.0","1.5");
        map.put("10.0","1.6");
        map.put("11.0","1.7");
        map.put("12.0","1.7");
        map.put("13.0","1.8");
        map.put("14.0","1.8");
        map.put("15.0","1.9");
        map.put("16.0","2.0");
        map.put("17.0","2.0");
        map.put("18.0","2.1");
        map.put("19.0","2.1");
        map.put("20.0","2.2");
        map.put("21.0","2.3");
        map.put("22.0","2.3");
        map.put("23.0","2.4");
        map.put("24.0","2.4");
        map.put("25.0","2.5");
        map.put("26.0","2.6");
        map.put("27.0","2.6");
        map.put("28.0","2.7");
        map.put("29.0","2.7");
        map.put("30.0","2.8");
        map.put("31.0","2.9");
        map.put("32.0","2.9");
        map.put("33.0","3.0");
        map.put("34.0","3.0");
        map.put("35.0","3.1");
        map.put("36.0","3.2");
        map.put("37.0","3.2");
        map.put("38.0","3.3");
        map.put("39.0","3.3");
        map.put("40.0","3.4");
        map.put("41.0","3.5");
        map.put("42.0","3.5");
        map.put("43.0","3.6");
        map.put("44.0","3.6");
        map.put("45.0","3.7");
        map.put("46.0","3.8");
        map.put("47.0","3.8");
        map.put("48.0","3.9");
        map.put("49.0","3.9");
        map.put("50.0","4.0");
        map.put("51.0","4.1");
        map.put("52.0","4.1");
        map.put("53.0","4.2");
        map.put("54.0","4.2");
        map.put("55.0","4.3");
        map.put("56.0","4.4");
        map.put("57.0","4.4");
        map.put("58.0","4.5");
        map.put("59.0","4.5");
        map.put("60.0","4.6");
        map.put("61.0","4.7");
        map.put("62.0","4.7");
        map.put("63.0","4.8");
        map.put("64.0","4.8");
        map.put("65.0","4.9");
        map.put("66.0","5.0");
        map.put("67.0","5.0");
        map.put("68.0","5.1");
        map.put("69.0","5.1");
        map.put("70.0","5.2");
        map.put("71.0","5.3");
        map.put("72.0","5.3");
        map.put("73.0","5.4");
        map.put("74.0","5.4");
        map.put("75.0","5.5");
        map.put("76.0","5.6");
        map.put("77.0","5.6");
        map.put("78.0","5.7");
        map.put("79.0","5.7");
        map.put("80.0","5.8");
        map.put("81.0","5.9");
        map.put("82.0","5.9");
        map.put("83.0","6.0");
        map.put("84.0","6.0");
        map.put("85.0","6.1");
        map.put("86.0","6.2");
        map.put("87.0","6.2");
        map.put("88.0","6.3");
        map.put("89.0","6.3");
        map.put("90.0","6.4");
        map.put("91.0","6.5");
        map.put("92.0","6.5");
        map.put("93.0","6.6");
        map.put("94.0","6.6");
        map.put("95.0","6.7");
        map.put("96.0","6.8");
        map.put("97.0","6.8");
        map.put("98.0","6.9");
        map.put("99.0","6.9");
        map.put("100.0","7.0");
        return map;
    }

    private Map<String, String> build70Scale() {
        Map<String, String> map = new HashMap<>();
        map.put("0.0","1.0");
        map.put("1.0","1.0");
        map.put("2.0","1.1");
        map.put("3.0","1.1");
        map.put("4.0","1.2");
        map.put("5.0","1.2");
        map.put("6.0","1.3");
        map.put("7.0","1.3");
        map.put("8.0","1.3");
        map.put("9.0","1.4");
        map.put("10.0","1.4");
        map.put("11.0","1.5");
        map.put("12.0","1.5");
        map.put("13.0","1.6");
        map.put("14.0","1.6");
        map.put("15.0","1.6");
        map.put("16.0","1.7");
        map.put("17.0","1.7");
        map.put("18.0","1.8");
        map.put("19.0","1.8");
        map.put("20.0","1.9");
        map.put("21.0","1.9");
        map.put("22.0","1.9");
        map.put("23.0","2.0");
        map.put("24.0","2.0");
        map.put("25.0","2.1");
        map.put("26.0","2.1");
        map.put("27.0","2.2");
        map.put("28.0","2.2");
        map.put("29.0","2.2");
        map.put("30.0","2.3");
        map.put("31.0","2.3");
        map.put("32.0","2.4");
        map.put("33.0","2.4");
        map.put("34.0","2.5");
        map.put("35.0","2.5");
        map.put("36.0","2.5");
        map.put("37.0","2.6");
        map.put("38.0","2.6");
        map.put("39.0","2.7");
        map.put("40.0","2.7");
        map.put("41.0","2.8");
        map.put("42.0","2.8");
        map.put("43.0","2.8");
        map.put("44.0","2.9");
        map.put("45.0","2.9");
        map.put("46.0","3.0");
        map.put("47.0","3.0");
        map.put("48.0","3.1");
        map.put("49.0","3.1");
        map.put("50.0","3.1");
        map.put("51.0","3.2");
        map.put("52.0","3.2");
        map.put("53.0","3.3");
        map.put("54.0","3.3");
        map.put("55.0","3.4");
        map.put("56.0","3.4");
        map.put("57.0","3.4");
        map.put("58.0","3.5");
        map.put("59.0","3.5");
        map.put("60.0","3.6");
        map.put("61.0","3.6");
        map.put("62.0","3.7");
        map.put("63.0","3.7");
        map.put("64.0","3.7");
        map.put("65.0","3.8");
        map.put("66.0","3.8");
        map.put("67.0","3.9");
        map.put("68.0","3.9");
        map.put("69.0","4.0");
        map.put("70.0","4.0");
        map.put("71.0","4.1");
        map.put("72.0","4.2");
        map.put("73.0","4.3");
        map.put("74.0","4.4");
        map.put("75.0","4.5");
        map.put("76.0","4.6");
        map.put("77.0","4.7");
        map.put("78.0","4.8");
        map.put("79.0","4.9");
        map.put("80.0","5.0");
        map.put("81.0","5.1");
        map.put("82.0","5.2");
        map.put("83.0","5.3");
        map.put("84.0","5.4");
        map.put("85.0","5.5");
        map.put("86.0","5.6");
        map.put("87.0","5.7");
        map.put("88.0","5.8");
        map.put("89.0","5.9");
        map.put("90.0","6.0");
        map.put("91.0","6.1");
        map.put("92.0","6.2");
        map.put("93.0","6.3");
        map.put("94.0","6.4");
        map.put("95.0","6.5");
        map.put("96.0","6.6");
        map.put("97.0","6.7");
        map.put("98.0","6.8");
        map.put("99.0","6.9");
        map.put("100.0","7.0");
        return map;
    }

    private Map<String, String> build80Scale() {
        Map<String, String> map = new HashMap<>();
        map.put("0.0","1.0");
        map.put("1.0","1.0");
        map.put("2.0","1.1");
        map.put("3.0","1.1");
        map.put("4.0","1.2");
        map.put("5.0","1.2");
        map.put("6.0","1.2");
        map.put("7.0","1.3");
        map.put("8.0","1.3");
        map.put("9.0","1.3");
        map.put("10.0","1.4");
        map.put("11.0","1.4");
        map.put("12.0","1.5");
        map.put("13.0","1.5");
        map.put("14.0","1.5");
        map.put("15.0","1.6");
        map.put("16.0","1.6");
        map.put("17.0","1.6");
        map.put("18.0","1.7");
        map.put("19.0","1.7");
        map.put("20.0","1.8");
        map.put("21.0","1.8");
        map.put("22.0","1.8");
        map.put("23.0","1.9");
        map.put("24.0","1.9");
        map.put("25.0","1.9");
        map.put("26.0","2.0");
        map.put("27.0","2.0");
        map.put("28.0","2.1");
        map.put("29.0","2.1");
        map.put("30.0","2.1");
        map.put("31.0","2.2");
        map.put("32.0","2.2");
        map.put("33.0","2.2");
        map.put("34.0","2.3");
        map.put("35.0","2.3");
        map.put("36.0","2.4");
        map.put("37.0","2.4");
        map.put("38.0","2.4");
        map.put("39.0","2.5");
        map.put("40.0","2.5");
        map.put("41.0","2.5");
        map.put("42.0","2.6");
        map.put("43.0","2.6");
        map.put("44.0","2.7");
        map.put("45.0","2.7");
        map.put("46.0","2.7");
        map.put("47.0","2.8");
        map.put("48.0","2.8");
        map.put("49.0","2.8");
        map.put("50.0","2.9");
        map.put("51.0","2.9");
        map.put("52.0","3.0");
        map.put("53.0","3.0");
        map.put("54.0","3.0");
        map.put("55.0","3.1");
        map.put("56.0","3.1");
        map.put("57.0","3.1");
        map.put("58.0","3.2");
        map.put("59.0","3.2");
        map.put("60.0","3.3");
        map.put("61.0","3.3");
        map.put("62.0","3.3");
        map.put("63.0","3.4");
        map.put("64.0","3.4");
        map.put("65.0","3.4");
        map.put("66.0","3.5");
        map.put("67.0","3.5");
        map.put("68.0","3.6");
        map.put("69.0","3.6");
        map.put("70.0","3.6");
        map.put("71.0","3.7");
        map.put("72.0","3.7");
        map.put("73.0","3.7");
        map.put("74.0","3.8");
        map.put("75.0","3.8");
        map.put("76.0","3.9");
        map.put("77.0","3.9");
        map.put("78.0","3.9");
        map.put("79.0","4.0");
        map.put("80.0","4.0");
        map.put("81.0","4.2");
        map.put("82.0","4.3");
        map.put("83.0","4.5");
        map.put("84.0","4.6");
        map.put("85.0","4.8");
        map.put("86.0","4.9");
        map.put("87.0","5.1");
        map.put("88.0","5.2");
        map.put("89.0","5.4");
        map.put("90.0","5.5");
        map.put("91.0","5.7");
        map.put("92.0","5.8");
        map.put("93.0","6.0");
        map.put("94.0","6.1");
        map.put("95.0","6.3");
        map.put("96.0","6.4");
        map.put("97.0","6.6");
        map.put("98.0","6.7");
        map.put("99.0","6.9");
        map.put("100.0","7.0");
        return map;
    }

    private Map<String, String> build90Scale() {
        Map<String, String> map = new HashMap<>();
        map.put("0.0","1.0");
        map.put("1.0","1.0");
        map.put("2.0","1.1");
        map.put("3.0","1.1");
        map.put("4.0","1.1");
        map.put("5.0","1.2");
        map.put("6.0","1.2");
        map.put("7.0","1.2");
        map.put("8.0","1.3");
        map.put("9.0","1.3");
        map.put("10.0","1.3");
        map.put("11.0","1.4");
        map.put("12.0","1.4");
        map.put("13.0","1.4");
        map.put("14.0","1.5");
        map.put("15.0","1.5");
        map.put("16.0","1.5");
        map.put("17.0","1.6");
        map.put("18.0","1.6");
        map.put("19.0","1.6");
        map.put("20.0","1.7");
        map.put("21.0","1.7");
        map.put("22.0","1.7");
        map.put("23.0","1.8");
        map.put("24.0","1.8");
        map.put("25.0","1.8");
        map.put("26.0","1.9");
        map.put("27.0","1.9");
        map.put("28.0","1.9");
        map.put("29.0","2.0");
        map.put("30.0","2.0");
        map.put("31.0","2.0");
        map.put("32.0","2.1");
        map.put("33.0","2.1");
        map.put("34.0","2.1");
        map.put("35.0","2.2");
        map.put("36.0","2.2");
        map.put("37.0","2.2");
        map.put("38.0","2.3");
        map.put("39.0","2.3");
        map.put("40.0","2.3");
        map.put("41.0","2.4");
        map.put("42.0","2.4");
        map.put("43.0","2.4");
        map.put("44.0","2.5");
        map.put("45.0","2.5");
        map.put("46.0","2.5");
        map.put("47.0","2.6");
        map.put("48.0","2.6");
        map.put("49.0","2.6");
        map.put("50.0","2.7");
        map.put("51.0","2.7");
        map.put("52.0","2.7");
        map.put("53.0","2.8");
        map.put("54.0","2.8");
        map.put("55.0","2.8");
        map.put("56.0","2.9");
        map.put("57.0","2.9");
        map.put("58.0","2.9");
        map.put("59.0","3.0");
        map.put("60.0","3.0");
        map.put("61.0","3.0");
        map.put("62.0","3.1");
        map.put("63.0","3.1");
        map.put("64.0","3.1");
        map.put("65.0","3.2");
        map.put("66.0","3.2");
        map.put("67.0","3.2");
        map.put("68.0","3.3");
        map.put("69.0","3.3");
        map.put("70.0","3.3");
        map.put("71.0","3.4");
        map.put("72.0","3.4");
        map.put("73.0","3.4");
        map.put("74.0","3.5");
        map.put("75.0","3.5");
        map.put("76.0","3.5");
        map.put("77.0","3.6");
        map.put("78.0","3.6");
        map.put("79.0","3.6");
        map.put("80.0","3.7");
        map.put("81.0","3.7");
        map.put("82.0","3.7");
        map.put("83.0","3.8");
        map.put("84.0","3.8");
        map.put("85.0","3.8");
        map.put("86.0","3.9");
        map.put("87.0","3.9");
        map.put("88.0","3.9");
        map.put("89.0","4.0");
        map.put("90.0","4.0");
        map.put("91.0","4.3");
        map.put("92.0","4.6");
        map.put("93.0","4.9");
        map.put("94.0","5.2");
        map.put("95.0","5.5");
        map.put("96.0","5.8");
        map.put("97.0","6.1");
        map.put("98.0","6.4");
        map.put("99.0","6.7");
        map.put("100.0","7.0");
        return map;
    }

}