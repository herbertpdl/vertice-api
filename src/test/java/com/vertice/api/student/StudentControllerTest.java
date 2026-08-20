package com.vertice.api.student;

import com.vertice.api.common.exception.DuplicateEmailException;
import com.vertice.api.common.exception.ResourceNotFoundException;
import com.vertice.api.config.SecurityConfig;
import com.vertice.api.generated.model.StudentResponse;
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

@WebMvcTest(StudentController.class)
@Import(SecurityConfig.class)
class StudentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudentService studentService;

    @Test
    void listStudents_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/students"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listStudents_withJwt_returns200() throws Exception {
        when(studentService.listStudents()).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/students").with(jwt()))
                .andExpect(status().isOk());
    }

    @Test
    void createStudent_withValidBody_returns201() throws Exception {
        StudentResponse response = new StudentResponse().id(1L).name("Student").email("student@vertice.com");
        when(studentService.createStudent(any())).thenReturn(response);

        mockMvc.perform(post("/api/students")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Student\",\"email\":\"student@vertice.com\",\"password\":\"supersecret1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("student@vertice.com"));
    }

    @Test
    void createStudent_withBlankName_returns422() throws Exception {
        mockMvc.perform(post("/api/students")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"student@vertice.com\",\"password\":\"supersecret1\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createStudent_withBlankEmail_returns422() throws Exception {
        mockMvc.perform(post("/api/students")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Student\",\"email\":\"\",\"password\":\"supersecret1\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createStudent_withMalformedEmail_returns422() throws Exception {
        mockMvc.perform(post("/api/students")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Student\",\"email\":\"not-an-email\",\"password\":\"supersecret1\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createStudent_withMissingPassword_returns422() throws Exception {
        mockMvc.perform(post("/api/students")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Student\",\"email\":\"student@vertice.com\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createStudent_withShortPassword_returns422() throws Exception {
        mockMvc.perform(post("/api/students")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Student\",\"email\":\"student@vertice.com\",\"password\":\"short\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createStudent_withDuplicateEmail_returns409() throws Exception {
        when(studentService.createStudent(any())).thenThrow(new DuplicateEmailException("student@vertice.com"));

        mockMvc.perform(post("/api/students")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Student\",\"email\":\"student@vertice.com\",\"password\":\"supersecret1\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void getStudent_whenExists_returns200() throws Exception {
        StudentResponse response = new StudentResponse().id(1L).name("Student").email("student@vertice.com");
        when(studentService.getStudent(1L)).thenReturn(response);

        mockMvc.perform(get("/api/students/1").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getStudent_whenMissing_returns404() throws Exception {
        when(studentService.getStudent(99L)).thenThrow(new ResourceNotFoundException("Student", 99L));

        mockMvc.perform(get("/api/students/99").with(jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateStudent_whenExists_returns200() throws Exception {
        StudentResponse response = new StudentResponse().id(1L).name("New Name").email("student@vertice.com");
        when(studentService.updateStudent(org.mockito.ArgumentMatchers.eq(1L), any())).thenReturn(response);

        mockMvc.perform(put("/api/students/1")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Name\",\"email\":\"student@vertice.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"));
    }

    @Test
    void updateStudent_whenMissing_returns404() throws Exception {
        when(studentService.updateStudent(org.mockito.ArgumentMatchers.eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Student", 99L));

        mockMvc.perform(put("/api/students/99")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Student\",\"email\":\"student@vertice.com\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteStudent_whenExists_returns204() throws Exception {
        mockMvc.perform(delete("/api/students/1").with(jwt()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteStudent_whenMissing_returns404() throws Exception {
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Student", 99L))
                .when(studentService).deleteStudent(99L);

        mockMvc.perform(delete("/api/students/99").with(jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void setStudentPassword_whenExists_returns204() throws Exception {
        mockMvc.perform(put("/api/students/1/password")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"brandNewPassword1\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void setStudentPassword_whenMissing_returns404() throws Exception {
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Student", 99L))
                .when(studentService).setPassword(99L, "brandNewPassword1");

        mockMvc.perform(put("/api/students/99/password")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"brandNewPassword1\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void setStudentPassword_withShortPassword_returns422() throws Exception {
        mockMvc.perform(put("/api/students/1/password")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"short\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
