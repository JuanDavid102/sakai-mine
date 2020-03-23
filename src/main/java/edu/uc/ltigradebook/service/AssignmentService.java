package edu.uc.ltigradebook.service;

import edu.uc.ltigradebook.entity.AssignmentPreference;
import edu.uc.ltigradebook.entity.AssignmentStatistic;
import edu.uc.ltigradebook.repository.AssignmentRepository;
import edu.uc.ltigradebook.repository.AssignmentStatisticRepository;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
public class AssignmentService {

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private AssignmentStatisticRepository assignmentStatisticRepo;

    public Optional<AssignmentPreference> getAssignmentPreference(String assignmentId) {
        return assignmentRepository.findById(Long.valueOf(assignmentId));
    }

    public Optional<AssignmentStatistic> getAssignmentStatistic(String assignmentId) {
        return assignmentStatisticRepo.findById(Long.valueOf(assignmentId));
    }

    public void saveAssignmentPreference(AssignmentPreference assignmentPreference) {
        log.debug("Saving assignment preferences {}.", assignmentPreference);
        assignmentRepository.save(assignmentPreference);
    }

    public void saveAssignmentStatistic(AssignmentStatistic assignmentStatistic) {
        log.debug("Saving assignment statistics {}.", assignmentStatistic);
        assignmentStatisticRepo.save(assignmentStatistic);
    }
}
