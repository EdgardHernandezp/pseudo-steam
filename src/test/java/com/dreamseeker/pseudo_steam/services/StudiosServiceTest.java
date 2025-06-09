package com.dreamseeker.pseudo_steam.services;

import com.dreamseeker.pseudo_steam.domains.BucketsPage;
import com.dreamseeker.pseudo_steam.exceptions.BucketDoesNotExistException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNameExistsException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNotEmptyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudiosServiceTest {
    @InjectMocks
    private StudiosService studiosService;
    @Mock
    private ObjectStorageClient objectStorageClient;

    @Test
    void createBucketThrowsBucketNameExistsExceptionWhenStudioAlreadyExists() throws BucketNameExistsException {
        String bucketName = "existing-bucket";
        BucketNameExistsException bucketNameExistsException = new BucketNameExistsException(
                "test-bucket",
                S3Exception.builder().message("Bucket name is taken").build()
        );
        doThrow(new BucketNameExistsException(bucketName, bucketNameExistsException)).when(objectStorageClient).createBucket(bucketName);

        assertThrows(BucketNameExistsException.class, () -> studiosService.createStudio(bucketName));
    }

    @Test
    void fetchBucketsReturnsListOfStudios() {
        BucketsPage buckets = new BucketsPage("1", List.of(new BucketsPage.Bucket("test-bucket", Instant.now())));
        when(objectStorageClient.fetchBuckets(anyInt(), anyString())).thenReturn(buckets);

        BucketsPage result = studiosService.fetchStudios(10, "1");

        assertEquals(buckets, result);
    }

    @Test
    void deleteBucketThrowsBucketNotEmptyExceptionWhenStudioIsNotEmpty() throws BucketNotEmptyException, BucketDoesNotExistException {
        String bucketName = "non-empty-bucket";
        AwsServiceException bucketNameIsTaken = S3Exception.builder().message("Bucket not empty").build();
        doThrow(new BucketNotEmptyException(bucketName, bucketNameIsTaken)).when(objectStorageClient).deleteBucket(bucketName);

        assertThrows(BucketNotEmptyException.class, () -> studiosService.deleteStudio(bucketName));
    }

    @Test
    void deleteBucketThrowsBucketDoesNotExistExceptionWhenStudioDoesNotExist() throws BucketNotEmptyException, BucketDoesNotExistException {
        String bucketName = "non-existent-bucket";
        BucketNameExistsException bucketNameExistsException = new BucketNameExistsException(
                "test-bucket",
                S3Exception.builder().message("Bucket name is taken").build()
        );
        doThrow(new BucketDoesNotExistException(bucketName, bucketNameExistsException)).when(objectStorageClient).deleteBucket(bucketName);

        assertThrows(BucketDoesNotExistException.class, () -> studiosService.deleteStudio(bucketName));
    }

    @Test
    void deleteBucketSuccessfullyDeletesStudio() throws BucketNotEmptyException, BucketDoesNotExistException {
        String bucketName = "empty-bucket";
        assertDoesNotThrow(() -> studiosService.deleteStudio(bucketName));
        verify(objectStorageClient, times(1)).deleteBucket(bucketName);
    }
}