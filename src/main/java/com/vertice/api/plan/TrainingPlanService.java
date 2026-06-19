package com.vertice.api.plan;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TrainingPlanService {

    private final TrainingPlanRepository trainingPlanRepository;
}
