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
class BucketServiceTest {
    @InjectMocks
    private BucketService bucketService;
    @Mock
    private ObjectStorageClient objectStorageClient;

    @Test
    void createBucketThrowsBucketNameExistsExceptionWhenBucketAlreadyExists() throws BucketNameExistsException {
        String bucketName = "existing-bucket";
        BucketNameExistsException bucketNameExistsException = new BucketNameExistsException(
                "test-bucket",
                S3Exception.builder().message("Bucket name is taken").build()
        );
        doThrow(new BucketNameExistsException(bucketName, bucketNameExistsException)).when(objectStorageClient).createBucket(bucketName);

        assertThrows(BucketNameExistsException.class, () -> bucketService.createBucket(bucketName));
    }

    @Test
    void fetchBucketsReturnsListOfBuckets() {
        BucketsPage buckets = new BucketsPage("1", List.of(new BucketsPage.Bucket("test-bucket", Instant.now())));
        when(objectStorageClient.fetchBuckets(anyInt(), anyString())).thenReturn(buckets);

        BucketsPage result = bucketService.fetchBuckets(10, "1");

        assertEquals(buckets, result);
    }

    @Test
    void deleteBucketThrowsBucketNotEmptyExceptionWhenBucketIsNotEmpty() throws BucketNotEmptyException, BucketDoesNotExistException {
        String bucketName = "non-empty-bucket";
        AwsServiceException bucketNameIsTaken = S3Exception.builder().message("Bucket not empty").build();
        doThrow(new BucketNotEmptyException(bucketName, bucketNameIsTaken)).when(objectStorageClient).deleteBucket(bucketName);

        assertThrows(BucketNotEmptyException.class, () -> bucketService.deleteBucket(bucketName));
    }

    @Test
    void deleteBucketThrowsBucketDoesNotExistExceptionWhenBucketDoesNotExist() throws BucketNotEmptyException, BucketDoesNotExistException {
        String bucketName = "non-existent-bucket";
        BucketNameExistsException bucketNameExistsException = new BucketNameExistsException(
                "test-bucket",
                S3Exception.builder().message("Bucket name is taken").build()
        );
        doThrow(new BucketDoesNotExistException(bucketName, bucketNameExistsException)).when(objectStorageClient).deleteBucket(bucketName);

        assertThrows(BucketDoesNotExistException.class, () -> bucketService.deleteBucket(bucketName));
    }

    @Test
    void deleteBucketSuccessfullyDeletesBucket() throws BucketNotEmptyException, BucketDoesNotExistException {
        String bucketName = "empty-bucket";
        assertDoesNotThrow(() -> bucketService.deleteBucket(bucketName));
        verify(objectStorageClient, times(1)).deleteBucket(bucketName);
    }
}