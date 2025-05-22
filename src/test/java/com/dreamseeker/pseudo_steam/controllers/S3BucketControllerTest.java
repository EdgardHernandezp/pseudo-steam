package com.dreamseeker.pseudo_steam.controllers;

import com.dreamseeker.pseudo_steam.domains.BucketsPage;
import com.dreamseeker.pseudo_steam.exceptions.BucketDoesNotExistException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNameExistsException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNotEmptyException;
import com.dreamseeker.pseudo_steam.services.BucketService;
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

@WebMvcTest(controllers = S3BucketController.class)
class S3BucketControllerTest {

    @MockitoBean
    private BucketService bucketService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void whenBucketIsCreatedBucketSuccessfully() throws Exception {
        String bucketName = "test-bucket";
        when(bucketService.createBucket(eq(bucketName))).thenReturn(new BucketsPage.Bucket(bucketName, Instant.now()));
        mockMvc.perform(post("/buckets".concat("/").concat(bucketName)))
                .andExpect(status().isOk());
    }

    @Test
    void whenBucketIsCreatedThrowBucketNameExists() throws Exception {
        BucketNameExistsException bucketNameExistsException = new BucketNameExistsException(
                "test-bucket",
                S3Exception.builder().message("Bucket name is taken").build()
        );
        doThrow(bucketNameExistsException).when(bucketService).createBucket("test-bucket");
        mockMvc.perform(post("/buckets".concat("/test-bucket")))
                .andExpect(status().isConflict());
    }

    @Test
    void whenFetchBucketsReturnListOfBucketsSuccessfully() throws Exception {
        BucketsPage mockBuckets = new BucketsPage("1", List.of(new BucketsPage.Bucket("test-bucket", Instant.now())));
        when(bucketService.fetchBuckets(anyInt(), anyString())).thenReturn(mockBuckets);

        mockMvc.perform(get("/buckets").queryParam("limit", "2").queryParam("continuationToken", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buckets[0].bucketName").value("test-bucket"));
    }

    @Test
    void whenDeleteBucketReturnOk() throws Exception {
        String bucketName = "test-bucket";
        doNothing().when(bucketService).deleteBucket(bucketName);

        mockMvc.perform(delete("/buckets".concat("/test-bucket")))
                .andExpect(status().isOk());
    }

    @Test
    void whenDeleteBucketThrowBucketNotEmptyException() throws Exception {
        String bucketName = "test-bucket";
        AwsServiceException bucketNameIsTaken = S3Exception.builder().message("Bucket not empty").build();
        BucketNotEmptyException bucketNotEmptyException = new BucketNotEmptyException(bucketName, bucketNameIsTaken);
        doThrow(bucketNotEmptyException).when(bucketService).deleteBucket(bucketName);

        mockMvc.perform(delete("/buckets".concat("/test-bucket")))
                .andExpect(status().isConflict());
    }

    @Test
    void whenDeleteBucketThrowBucketDoesNotExistException() throws Exception {
        String bucketName = "test-bucket";
        BucketDoesNotExistException bucketDoesNotExistsException = new BucketDoesNotExistException(bucketName, NoSuchBucketException.builder().build());
        doThrow(bucketDoesNotExistsException).when(bucketService).deleteBucket(bucketName);

        mockMvc.perform(delete("/buckets".concat("/test-bucket")))
                .andExpect(status().isNotFound());
    }
}