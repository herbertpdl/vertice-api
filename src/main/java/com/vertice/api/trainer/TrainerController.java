package com.vertice.api.trainer;

import com.vertice.api.generated.api.TrainersApi;
import com.vertice.api.generated.model.SetPasswordRequest;
import com.vertice.api.generated.model.TrainerCreateRequest;
import com.vertice.api.generated.model.TrainerRequest;
import com.vertice.api.generated.model.TrainerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TrainerController implements TrainersApi {

    private final TrainerService trainerService;

    @Override
    public ResponseEntity<List<TrainerResponse>> listTrainers() {
        return ResponseEntity.ok(trainerService.listTrainers());
    }

    @Override
    public ResponseEntity<TrainerResponse> createTrainer(TrainerCreateRequest trainerCreateRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(trainerService.createTrainer(trainerCreateRequest));
    }

    @Override
    public ResponseEntity<TrainerResponse> getTrainer(Long id) {
        return ResponseEntity.ok(trainerService.getTrainer(id));
    }

    @Override
    public ResponseEntity<TrainerResponse> updateTrainer(Long id, TrainerRequest trainerRequest) {
        return ResponseEntity.ok(trainerService.updateTrainer(id, trainerRequest));
    }

    @Override
    public ResponseEntity<Void> deleteTrainer(Long id) {
        trainerService.deleteTrainer(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> setTrainerPassword(Long id, SetPasswordRequest setPasswordRequest) {
        trainerService.setPassword(id, setPasswordRequest.getPassword());
        return ResponseEntity.noContent().build();
    }
}
