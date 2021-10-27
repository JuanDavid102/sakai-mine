package edu.uc.ltigradebook.controller;

import java.math.BigInteger;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import edu.uc.ltigradebook.constants.LiveEventConstants;
import edu.uc.ltigradebook.entity.StudentCanvasGrade;
import edu.uc.ltigradebook.model.GradeChangeEvent;
import edu.uc.ltigradebook.service.GradeService;

/**
 * This controller listens and process events configured in the Data Streams section.
 */
@RestController
@RequestMapping("/datastream")
@Slf4j
public class EventHandlingController {

    @Autowired
    GradeService gradeService;

    private static final BigInteger zeros = new BigInteger("10000000000000");

    private final JwtDecoder jwtDecoder;
    private final ObjectMapper objectMapper;

    public EventHandlingController(@Qualifier("liveEvents") JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
        objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @RequestMapping(value = "/post", method = RequestMethod.POST)
    public ResponseEntity parseEvent(@RequestBody String canvasJwt) {
        log.debug("Received JWT Event : {}", canvasJwt);

        // remove quotation marks from jwt
        if(canvasJwt.startsWith("\"")) {
            // This shouldn't be required but appears to be included by Canvas
            log.debug("Removing extra quotes from JWT.");
            canvasJwt = canvasJwt.replace("\"", "");
        }

        Jwt processedJwt;
        try {
            processedJwt = jwtDecoder.decode(canvasJwt);
        } catch(JwtException je) {
            log.error("Problem decoding JWT", je);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Failed to decode JWT");
        }
        Map<String, Object> claims = processedJwt.getClaims();
        Map<String,Object> metadata = (Map<String,Object>) claims.get(LiveEventConstants.EVENT_METADATA);
        Map<String,Object> body = (Map<String, Object>) claims.get(LiveEventConstants.EVENT_BODY);
        if(metadata == null || body == null || metadata.get(LiveEventConstants.EVENT_NAME) == null) {
            log.error("Processed JWT is missing expected event structure - whole JWT: {} ", canvasJwt);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected claims where not found in JWT");
        }
        String eventName = (String) metadata.get(LiveEventConstants.EVENT_NAME);
        log.debug("Processing event: {}", eventName);
        log.debug("Processing metadata: {}", metadata);
        log.debug("Processing body: {}", body);
        try {
            switch(eventName) {
                case LiveEventConstants.EVENT_GRADE_CHANGE_NAME:
                    GradeChangeEvent eventData = objectMapper.convertValue(body, GradeChangeEvent.class);
                    String assignmentId = this.getModFromString(eventData.getAssignment_id());
                    String studentId = this.getModFromString(eventData.getStudent_id());
                    String grade = eventData.getGrade();
                    log.debug("Received a grade {} for user {} and assignment {}", grade, studentId, assignmentId);
                    StudentCanvasGrade studentCanvasGrade = new StudentCanvasGrade();
                    studentCanvasGrade.setAssignmentId(assignmentId);
                    studentCanvasGrade.setGrade(grade);
                    studentCanvasGrade.setUserId(studentId);
                    gradeService.saveCanvasGrade(studentCanvasGrade);
                    break;
                default:
                    log.info("Unsupported event {} - whole body: {}", eventName, body.toString());
            }
        } catch(IllegalArgumentException e) {
            log.error("Failed to map body {} to object {} ", body, eventName, e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to parse event of type " + eventName);
        }

        return ResponseEntity.status(HttpStatus.OK).build();
    }

    private String getModFromString(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }
        return new BigInteger(value).remainder(zeros).toString();
    }
}

