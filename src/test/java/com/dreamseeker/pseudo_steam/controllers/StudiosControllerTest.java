package com.dreamseeker.pseudo_steam.controllers;

import com.dreamseeker.pseudo_steam.domains.BucketsPage;
import com.dreamseeker.pseudo_steam.exceptions.BucketDoesNotExistException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNameExistsException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNotEmptyException;
import com.dreamseeker.pseudo_steam.services.StudiosService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StudiosController.class)
class StudiosControllerTest {

    @MockitoBean
    private StudiosService studiosService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void whenBucketIsCreatedBucketSuccessfully() throws Exception {
        String bucketName = "test-bucket";
        when(studiosService.createStudio(eq(bucketName))).thenReturn(new BucketsPage.Bucket(bucketName, Instant.now()));
        mockMvc.perform(post("/studios".concat("/").concat(bucketName)))
                .andExpect(status().isOk());
    }

    @Test
    void whenBucketIsCreatedThrowBucketNameExists() throws Exception {
        BucketNameExistsException bucketNameExistsException = new BucketNameExistsException(
                "test-bucket",
                S3Exception.builder().message("Bucket name is taken").build()
        );
        doThrow(bucketNameExistsException).when(studiosService).createStudio("test-bucket");
        mockMvc.perform(post("/studios".concat("/test-bucket")))
                .andExpect(status().isConflict());
    }

    @Test
    void whenFetchBucketsReturnListOfBucketsSuccessfully() throws Exception {
        BucketsPage mockBuckets = new BucketsPage("1", List.of(new BucketsPage.Bucket("test-bucket", Instant.now())));
        when(studiosService.fetchStudios(anyInt(), anyString())).thenReturn(mockBuckets);

        mockMvc.perform(get("/studios").queryParam("limit", "2").queryParam("continuationToken", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buckets[0].bucketName").value("test-bucket"));
    }

    @Test
    void whenDeleteStudioReturnOk() throws Exception {
        String bucketName = "test-bucket";
        doNothing().when(studiosService).deleteStudio(bucketName);

        mockMvc.perform(delete("/studios".concat("/test-bucket")))
                .andExpect(status().isOk());
    }

    @Test
    void whenDeleteBucketThrowStudioNotEmptyException() throws Exception {
        String bucketName = "test-bucket";
        AwsServiceException bucketNameIsTaken = S3Exception.builder().message("Bucket not empty").build();
        BucketNotEmptyException bucketNotEmptyException = new BucketNotEmptyException(bucketName, bucketNameIsTaken);
        doThrow(bucketNotEmptyException).when(studiosService).deleteStudio(bucketName);

        mockMvc.perform(delete("/studios".concat("/test-bucket")))
                .andExpect(status().isConflict());
    }

    @Test
    void whenDeleteBucketThrowStudioDoesNotExistException() throws Exception {
        String bucketName = "test-bucket";
        BucketDoesNotExistException bucketDoesNotExistsException = new BucketDoesNotExistException(bucketName, NoSuchBucketException.builder().build());
        doThrow(bucketDoesNotExistsException).when(studiosService).deleteStudio(bucketName);

        mockMvc.perform(delete("/studios".concat("/test-bucket")))
                .andExpect(status().isNotFound());
    }
}