package com.dreamseeker.pseudo_steam.services;

import com.dreamseeker.pseudo_steam.domains.BucketsPage;
import com.dreamseeker.pseudo_steam.domains.ObjectUploadResponse;
import com.dreamseeker.pseudo_steam.exceptions.BucketDoesNotExistException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNameExistsException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNotEmptyException;
import com.dreamseeker.pseudo_steam.exceptions.ObjectDoesNotExistsException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@AllArgsConstructor
@Component
public class AWSObjectStorageClient implements ObjectStorageClient {

    private static final long MIN_PART_SIZE = 5 * 1024 * 1024;
    private static final long MAX_PART_SIZE = 100 * 1024 * 1024; // 100MB for optimal performance
    private static final int MAX_PARTS = 10000;
    private static final String OUTPUT_DIRECTORY = "downloads/";

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
    public ObjectUploadResponse putObjectSinglePartUpload(String bucketName, String objectKey, MultipartFile file) throws BucketDoesNotExistException {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(file.getContentType())
                    .build();

            PutObjectResponse response = s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
            return new ObjectUploadResponse(bucketName, objectKey, response.versionId());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        } catch (NoSuchBucketException e) {
            log.error("Bucket ({}) does not exist", bucketName);
            throw new BucketDoesNotExistException(bucketName, e.getCause());
        }
    }

    @Override
    public ObjectUploadResponse putObjectMultiPartUpload(String bucketName, String objectKey, MultipartFile file) {
        String uploadId = null;
        try {
            uploadId = initiateMultipartUpload(bucketName, objectKey, file);
            List<CompletedPart> completedParts = uploadParts(bucketName, objectKey, file, uploadId);
            return completeMultipartUpload(bucketName, objectKey, completedParts, uploadId);
        } catch (Exception e) {
            try {
                abortMultipartUpload(bucketName, objectKey, uploadId);
            } catch (Exception abortException) {
                log.error("Failed to abort multipart upload", e);
            }
            throw new RuntimeException("Multipart upload failed", e);
        }
    }

    @Override
    public void getObject(String bucketName, String objectKey, String versionId) throws ObjectDoesNotExistsException, BucketDoesNotExistException {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .versionId(versionId)
                .build();
        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getObjectRequest)) {
            Path localPath = Paths.get(OUTPUT_DIRECTORY + objectKey);
            Files.createDirectories(localPath.getParent());
            Files.copy(response, localPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Successfully downloaded {} with version id {} in {}", objectKey, response.response().versionId(), localPath);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        } catch (NoSuchKeyException e) {
            log.error("The object: {} does not exists", bucketName.concat("/" + objectKey));
            throw new ObjectDoesNotExistsException();
        } catch (NoSuchBucketException e) {
            log.error("Bucket ({}) does not exist", bucketName);
            throw new BucketDoesNotExistException(bucketName, e.getCause());
        }
    }

    @Override
    public String deleteObject(String bucketName, String objectKey, String versionId) throws BucketDoesNotExistException, ObjectDoesNotExistsException {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder().bucket(bucketName).key(objectKey).versionId(versionId).build();
            return s3Client.deleteObject(deleteObjectRequest).versionId();
        } catch (NoSuchBucketException e) {
            log.error("Bucket ({}) does not exist", bucketName);
            throw new BucketDoesNotExistException(bucketName, e.getCause());
        } catch (NoSuchKeyException e) {
            log.error("The object: {} does not exists", bucketName.concat("/" + objectKey));
            throw new ObjectDoesNotExistsException();
        }
    }

    private void abortMultipartUpload(String bucketName, String gameName, String uploadId) {
        AbortMultipartUploadRequest abortRequest = AbortMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(gameName)
                .uploadId(uploadId)
                .build();
        s3Client.abortMultipartUpload(abortRequest);
    }

    private ObjectUploadResponse completeMultipartUpload(String bucketName, String gameName, List<CompletedPart> completedParts, String uploadId) {
        CompletedMultipartUpload completedUpload = CompletedMultipartUpload.builder()
                .parts(completedParts)
                .build();
        CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(gameName)
                .uploadId(uploadId)
                .multipartUpload(completedUpload)
                .build();
        CompleteMultipartUploadResponse completeMultipartUploadResponse = s3Client.completeMultipartUpload(completeRequest);

        return new ObjectUploadResponse(bucketName, gameName, completeMultipartUploadResponse.versionId());
    }

    private List<CompletedPart> uploadParts(String bucketName, String gameName, MultipartFile file, String uploadId) throws IOException {
        List<CompletedPart> completedParts = new ArrayList<>();
        PartCalculation calculatedParts = calculateParts(file.getSize());
        log.info("Number of parts: {}", calculatedParts.partCount);
        log.info("Parts size: {}", calculatedParts.partSize);
        for (int i = 0; i < calculatedParts.partCount(); i++) {
            int partNumber = i + 1;
            long startPos = (long) i * calculatedParts.partSize();
            long currentPartSize = Math.min(calculatedParts.partSize(), file.getSize() - startPos);
            byte[] partData = Arrays.copyOfRange(file.getBytes(), (int) startPos, (int) (startPos + currentPartSize));
            UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                    .bucket(bucketName)
                    .key(gameName)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .build();
            UploadPartResponse uploadPartResponse = s3Client.uploadPart(uploadPartRequest, RequestBody.fromBytes(partData));

            log.info("Upload part ({}) successful. Etag: {}", partNumber, uploadPartResponse.eTag());
            completedParts.add(
                    CompletedPart.builder()
                            .partNumber(partNumber)
                            .eTag(uploadPartResponse.eTag())
                            .build()
            );
        }
        return completedParts;
    }

    private String initiateMultipartUpload(String bucketName, String gameName, MultipartFile file) {
        CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(gameName)
                .contentType(file.getContentType())
                .build();
        String uploadId = s3Client.createMultipartUpload(createRequest).uploadId();
        log.info("Multipart upload created. Upload ID: {}", uploadId);
        return uploadId;
    }

    private PartCalculation calculateParts(long fileSize) {
        // Start with target part size, but ensure we don't exceed max parts
        long partSize = Math.max(MIN_PART_SIZE, fileSize / MAX_PARTS);

        // Cap at max part size for performance
        partSize = Math.min(partSize, MAX_PART_SIZE);

        // Round up to nearest MB for cleaner numbers
        partSize = ((partSize + 1024 * 1024 - 1) / (1024 * 1024)) * 1024 * 1024;

        int partCount = (int) Math.ceil((double) fileSize / partSize);

        return new PartCalculation(partSize, partCount);
    }

    record PartCalculation(long partSize, int partCount) {
    }
}
