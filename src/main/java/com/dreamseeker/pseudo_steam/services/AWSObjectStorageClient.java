package com.dreamseeker.pseudo_steam.services;

import com.dreamseeker.pseudo_steam.domains.BucketsPage;
import com.dreamseeker.pseudo_steam.domains.ObjectUploadResponse;
import com.dreamseeker.pseudo_steam.exceptions.BucketDoesNotExistException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNameExistsException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNotEmptyException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@AllArgsConstructor
@Component
public class AWSObjectStorageClient implements ObjectStorageClient {

    private final S3Client s3Client;

    @Override
    public BucketsPage.Bucket createBucket(String bucketName) throws BucketNameExistsException {
        try {
            String newBucketName = String.format("dev.%s-%s", bucketName, UUID.randomUUID());
            CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                    .bucket(newBucketName)
                    .build();
            s3Client.createBucket(createBucketRequest);

            PutBucketVersioningRequest versioningRequest = PutBucketVersioningRequest.builder()
                    .bucket(newBucketName)
                    .versioningConfiguration(VersioningConfiguration.builder()
                            .status(BucketVersioningStatus.ENABLED)
                            .build())
                    .build();
            s3Client.putBucketVersioning(versioningRequest);

            return new BucketsPage.Bucket(newBucketName, Instant.now());
        } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException e) {
            log.error("Bucket ({}) already exists", bucketName, e);
            throw new BucketNameExistsException(bucketName, e);
        }
    }

    @Override
    public BucketsPage fetchBuckets(Integer limit, String continuationToken) {
        ListBucketsRequest listBucketsRequest = ListBucketsRequest.builder()
                .maxBuckets(limit)
                .continuationToken(continuationToken)
                .prefix("dev.")
                .build();
        ListBucketsResponse bucketsResponse = s3Client.listBuckets(listBucketsRequest);
        List<BucketsPage.Bucket> buckets = bucketsResponse.buckets().stream().map(bucket -> new BucketsPage.Bucket(bucket.name(), bucket.creationDate())).toList();
        return new BucketsPage(bucketsResponse.continuationToken(), buckets);
    }

    @Override
    public void deleteBucket(String bucketName) throws BucketDoesNotExistException, BucketNotEmptyException {
        try {
            DeleteBucketRequest deleteBucketRequest = DeleteBucketRequest.builder().bucket(bucketName).build();
            s3Client.deleteBucket(deleteBucketRequest);
        } catch (NoSuchBucketException e) {
            log.error("Bucket ({}) does not exist", bucketName);
            throw new BucketDoesNotExistException(bucketName, e.getCause());
        } catch (AwsServiceException e) {
            log.error(e.getMessage(), e);
            if (e.awsErrorDetails().errorCode().equals("BucketNotEmpty")) {
                log.error("Bucket ({}) is not empty, remove all objects before deleting", bucketName);
                throw new BucketNotEmptyException(bucketName, e);
            }
        }

    }

    @Override
    public ObjectUploadResponse putObject(String studioId, String gameName, MultipartFile file) throws BucketDoesNotExistException {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(studioId)
                    .key(gameName)
                    .contentType(file.getContentType())
                    .build();

            PutObjectResponse response = s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
            return new ObjectUploadResponse(studioId, gameName, response.versionId());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        } catch (NoSuchBucketException e) {
            log.error("Bucket ({}) does not exist", studioId);
            throw new BucketDoesNotExistException(studioId, e.getCause());
        }
    }
}
