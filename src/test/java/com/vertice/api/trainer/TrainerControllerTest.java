package com.vertice.api.trainer;

import com.vertice.api.common.exception.DuplicateEmailException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.config.SecurityConfig;
import com.vertice.api.generated.model.TrainerResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TrainerController.class)
@Import(SecurityConfig.class)
class TrainerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrainerService trainerService;

    @Test
    void listTrainers_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/trainers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listTrainers_withJwt_returns200() throws Exception {
        when(trainerService.listTrainers()).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/trainers").with(jwt()))
                .andExpect(status().isOk());
    }

    @Test
    void createTrainer_withValidBody_returns201() throws Exception {
        TrainerResponse response = new TrainerResponse().id(1L).name("Coach").email("coach@vertice.com");
        when(trainerService.createTrainer(any())).thenReturn(response);

        mockMvc.perform(post("/api/trainers")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Coach\",\"email\":\"coach@vertice.com\",\"password\":\"supersecret1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("coach@vertice.com"));
    }

    @Test
    void createTrainer_withBlankName_returns422() throws Exception {
        mockMvc.perform(post("/api/trainers")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"coach@vertice.com\",\"password\":\"supersecret1\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createTrainer_withMalformedEmail_returns422() throws Exception {
        mockMvc.perform(post("/api/trainers")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Coach\",\"email\":\"not-an-email\",\"password\":\"supersecret1\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createTrainer_withBlankEmail_returns422() throws Exception {
        mockMvc.perform(post("/api/trainers")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Coach\",\"email\":\"\",\"password\":\"supersecret1\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createTrainer_withMissingPassword_returns422() throws Exception {
        mockMvc.perform(post("/api/trainers")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Coach\",\"email\":\"coach@vertice.com\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createTrainer_withShortPassword_returns422() throws Exception {
        mockMvc.perform(post("/api/trainers")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Coach\",\"email\":\"coach@vertice.com\",\"password\":\"short\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createTrainer_withDuplicateEmail_returns409() throws Exception {
        when(trainerService.createTrainer(any())).thenThrow(new DuplicateEmailException("coach@vertice.com"));

        mockMvc.perform(post("/api/trainers")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Coach\",\"email\":\"coach@vertice.com\",\"password\":\"supersecret1\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void getTrainer_whenExists_returns200() throws Exception {
        TrainerResponse response = new TrainerResponse().id(1L).name("Coach").email("coach@vertice.com");
        when(trainerService.getTrainer(1L)).thenReturn(response);

        mockMvc.perform(get("/api/trainers/1").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getTrainer_whenMissing_returns404() throws Exception {
        when(trainerService.getTrainer(99L)).thenThrow(new ResourceNotFoundException("Trainer", 99L));

        mockMvc.perform(get("/api/trainers/99").with(jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateTrainer_whenExists_returns200() throws Exception {
        TrainerResponse response = new TrainerResponse().id(1L).name("New Name").email("coach@vertice.com");
        when(trainerService.updateTrainer(org.mockito.ArgumentMatchers.eq(1L), any())).thenReturn(response);

        mockMvc.perform(put("/api/trainers/1")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Name\",\"email\":\"coach@vertice.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"));
    }

    @Test
    void updateTrainer_whenMissing_returns404() throws Exception {
        when(trainerService.updateTrainer(org.mockito.ArgumentMatchers.eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Trainer", 99L));

        mockMvc.perform(put("/api/trainers/99")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Coach\",\"email\":\"coach@vertice.com\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTrainer_whenExists_returns204() throws Exception {
        mockMvc.perform(delete("/api/trainers/1").with(jwt()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteTrainer_whenMissing_returns404() throws Exception {
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Trainer", 99L))
                .when(trainerService).deleteTrainer(99L);

        mockMvc.perform(delete("/api/trainers/99").with(jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void setTrainerPassword_whenExists_returns204() throws Exception {
        mockMvc.perform(put("/api/trainers/1/password")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"brandNewPassword1\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void setTrainerPassword_whenMissing_returns404() throws Exception {
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Trainer", 99L))
                .when(trainerService).setPassword(99L, "brandNewPassword1");

        mockMvc.perform(put("/api/trainers/99/password")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"brandNewPassword1\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void setTrainerPassword_withShortPassword_returns422() throws Exception {
        mockMvc.perform(put("/api/trainers/1/password")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"short\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
