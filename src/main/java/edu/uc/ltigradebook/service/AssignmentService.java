package edu.uc.ltigradebook.service;

import edu.uc.ltigradebook.entity.AssignmentPreference;
import edu.uc.ltigradebook.repository.AssignmentRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
public class AssignmentService {

    @Autowired
    private AssignmentRepository assignmentRepository;

    public Optional<AssignmentPreference> getAssignmentPreference(String assignmentId) {
        return assignmentRepository.findById(Long.valueOf(assignmentId));
    }

    public void saveAssignmentPreference(AssignmentPreference assignmentPreference) {
        log.debug("Saving assignment preferences {}.", assignmentPreference);
        assignmentRepository.save(assignmentPreference);
    }
}
