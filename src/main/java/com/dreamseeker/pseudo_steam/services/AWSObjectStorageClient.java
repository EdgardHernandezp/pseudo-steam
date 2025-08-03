package com.dreamseeker.pseudo_steam.services;

import com.dreamseeker.pseudo_steam.domains.*;
import com.dreamseeker.pseudo_steam.exceptions.BucketDoesNotExistException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNameExistsException;
import com.dreamseeker.pseudo_steam.exceptions.ObjectDoesNotExistsException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectVersionsIterable;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@AllArgsConstructor
@Component
@Primary
public class AWSObjectStorageClient implements ObjectStorageClient {

    private static final long MIN_PART_SIZE = 5 * 1024 * 1024;
    private static final long MAX_PART_SIZE = 100 * 1024 * 1024; // 100MB for optimal performance
    private static final int MAX_PARTS = 10000;
    private static final String OUTPUT_DIRECTORY = "downloads/";
    private static final String VERSION = "version";
    private static final String GENRE = "genre";

    protected final S3Client s3Client;
    private final S3Presigner s3Presigner;

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

            addLifecycleConfigs(newBucketName);

            return new BucketsPage.Bucket(newBucketName, Instant.now());
        } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException e) {
            log.error("Bucket ({}) already exists", bucketName, e);
            throw new BucketNameExistsException(bucketName, e);
        }
    }

    private void addLifecycleConfigs(String bucketName) {
        NoncurrentVersionTransition transition = NoncurrentVersionTransition.builder()
                .noncurrentDays(30)
                .storageClass(TransitionStorageClass.STANDARD_IA)
                .build();

        NoncurrentVersionExpiration expiration = NoncurrentVersionExpiration.builder()
                .newerNoncurrentVersions(5)
                .noncurrentDays(60)
                .build();

        LifecycleRule rule = LifecycleRule.builder()
                .id("Non_current_version_rules")
                .status(ExpirationStatus.ENABLED)
                .filter(LifecycleRuleFilter.builder().build())
                .noncurrentVersionTransitions(transition)
                .noncurrentVersionExpiration(expiration)
                .build();

        BucketLifecycleConfiguration lifecycleConfig = BucketLifecycleConfiguration.builder()
                .rules(rule)
                .build();

        PutBucketLifecycleConfigurationRequest request = PutBucketLifecycleConfigurationRequest.builder()
                .bucket(bucketName)
                .lifecycleConfiguration(lifecycleConfig)
                .build();

        s3Client.putBucketLifecycleConfiguration(request);
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
    public void deleteBucket(String bucketName) throws BucketDoesNotExistException {
        try {
            deleteObjects(bucketName);
            DeleteBucketRequest deleteBucketRequest = DeleteBucketRequest.builder().bucket(bucketName).build();
            s3Client.deleteBucket(deleteBucketRequest);
        } catch (NoSuchBucketException e) {
            log.error("Bucket ({}) does not exist", bucketName);
            throw new BucketDoesNotExistException(bucketName, e.getCause());
        } catch (SdkClientException | AwsServiceException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void deleteObjects(String bucketName) {
        List<ObjectIdentifier> objectIdentifiers = new ArrayList<>();
        ListObjectVersionsRequest listObjectVersionsRequest = ListObjectVersionsRequest.builder().bucket(bucketName).build();
        ListObjectVersionsIterable listObjectVersionsResponses = s3Client.listObjectVersionsPaginator(listObjectVersionsRequest);
        for (ListObjectVersionsResponse listObjectVersionsResponse : listObjectVersionsResponses) {

            listObjectVersionsResponse.versions().forEach(objectVersion -> {
                ObjectIdentifier objectIdentifier = ObjectIdentifier.builder().key(objectVersion.key()).versionId(objectVersion.versionId()).build();
                objectIdentifiers.add(objectIdentifier);
            });
            log.info("Found {} previous versions", listObjectVersionsResponse.versions().size());

            listObjectVersionsResponse.deleteMarkers().forEach(deleteMarker -> {
                ObjectIdentifier objectIdentifier = ObjectIdentifier.builder().key(deleteMarker.key()).versionId(deleteMarker.versionId()).build();
                objectIdentifiers.add(objectIdentifier);
            });
            log.info("Found {} delete markers", listObjectVersionsResponse.deleteMarkers().size());
        }

        if (!objectIdentifiers.isEmpty()) {
            log.info("Deleting {} previous versions/delete markers in bucket {}", objectIdentifiers.size(), bucketName);
            Delete deletions = Delete.builder().objects(objectIdentifiers).build();
            s3Client.deleteObjects(DeleteObjectsRequest.builder().bucket(bucketName).delete(deletions).build());
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

    @Override
    public InitiateUploadResponse initiateUpload(String bucketName, InitiateUploadRequest initiateUploadRequest) {
        CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(initiateUploadRequest.gameName())
                .contentType(initiateUploadRequest.contentType())
                .metadata(initiateUploadRequest.metadata())
                .build();
        String uploadId = s3Client.createMultipartUpload(createRequest).uploadId();

        PartCalculation calculatedParts = calculateParts(initiateUploadRequest.fileSize());
        List<PreSignedPartUrl> preSignedUrls = generatePreSignedUrls(
                bucketName,
                initiateUploadRequest.gameName(),
                uploadId,
                calculatedParts.partCount(),
                calculatedParts.partSize(),
                initiateUploadRequest.fileSize()
        );

        log.info("Initiated multipart upload for bucket: {} with uploadId: {} and {} parts",
                initiateUploadRequest.gameName(), uploadId, calculatedParts.partCount());
        return new InitiateUploadResponse(uploadId, initiateUploadRequest.gameName(), preSignedUrls);
    }

    @Override
    public void completeUpload(String bucketName, CompleteUploadRequest completeUploadRequest) {
        List<CompleteUploadRequest.CompletedPart> sortedParts = completeUploadRequest.parts().stream()
                .sorted(Comparator.comparing(CompleteUploadRequest.CompletedPart::partNumber))
                .toList();
        List<software.amazon.awssdk.services.s3.model.CompletedPart> s3Parts = sortedParts.stream()
                .map(part -> software.amazon.awssdk.services.s3.model.CompletedPart.builder()
                        .partNumber(part.partNumber())
                        .eTag(part.etag())
                        .build())
                .collect(Collectors.toList());
        CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(completeUploadRequest.key())
                .uploadId(completeUploadRequest.uploadId())
                .multipartUpload(CompletedMultipartUpload.builder()
                        .parts(s3Parts)
                        .build())
                .build();

        CompleteMultipartUploadResponse response = s3Client.completeMultipartUpload(completeRequest);
        log.info("Successfully completed multipart upload in bucket {} for key: {} with ETag: {}",
                bucketName, completeUploadRequest.key(), response.eTag());
    }

    @Override
    public GameInfo fetchObjectMetadata(String bucketName, String objectKey) throws ObjectDoesNotExistsException, BucketDoesNotExistException {
        try {
            Map<String, String> metadata = performHeadObjectRequest(bucketName, objectKey);
            String genre = metadata.get(GENRE);
            String version = metadata.get(VERSION);
            return new GameInfo(bucketName, objectKey, genre, version);
        } catch (NoSuchKeyException e) {
            log.error("The object: {} does not exists", bucketName.concat("/" + objectKey));
            throw new ObjectDoesNotExistsException();
        } catch (NoSuchBucketException e) {
            log.error("Bucket ({}) does not exist", bucketName);
            throw new BucketDoesNotExistException(bucketName, e.getCause());
        }
    }

    private Map<String, String> performHeadObjectRequest(String bucketName, String objectKey) {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();
            return s3Client.headObject(headRequest).metadata();
    }

    @Override
    public GameInfo modifyObjectMetadata(String bucketName, String objectKey, Map<String, String> metadata) throws ObjectDoesNotExistsException, BucketDoesNotExistException {
        try {
            Map<String, String> currentMetadata = performHeadObjectRequest(bucketName, objectKey);
            Map<String, String> newMetadata = new HashMap<>(currentMetadata);
            newMetadata.put(GENRE, metadata.get(GENRE));

            CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                    .sourceBucket(bucketName)
                    .sourceKey(objectKey)
                    .destinationBucket(bucketName)
                    .destinationKey(objectKey)
                    .metadata(newMetadata)
                    .metadataDirective(MetadataDirective.REPLACE)
                    .build();

            s3Client.copyObject(copyRequest);

            return new GameInfo(bucketName, objectKey, newMetadata.get(GENRE), newMetadata.get(VERSION));
        } catch (NoSuchKeyException e) {
            log.error("The object: {} does not exists", bucketName.concat("/" + objectKey));
            throw new ObjectDoesNotExistsException();
        } catch (NoSuchBucketException e) {
            log.error("Bucket ({}) does not exist", bucketName);
            throw new BucketDoesNotExistException(bucketName, e.getCause());
        }
    }

    private List<PreSignedPartUrl> generatePreSignedUrls(String bucketName, String objectKey, String uploadId,
                                                         int partCount, long partSize, long totalFileSize) {
        List<PreSignedPartUrl> preSignedUrls = new ArrayList<>();
        for (int partNumber = 1; partNumber <= partCount; partNumber++) {
            long currentPartSize = partSize;
            if (partNumber == partCount)
                currentPartSize = totalFileSize - (partSize * (partCount - 1));

            UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .build();

            UploadPartPresignRequest presignRequest = UploadPartPresignRequest.builder()
                    .signatureDuration(Duration.ofHours(1))
                    .uploadPartRequest(uploadPartRequest)
                    .build();
            PresignedUploadPartRequest presignedRequest = s3Presigner.presignUploadPart(presignRequest);

            preSignedUrls.add(new PreSignedPartUrl(
                    partNumber,
                    presignedRequest.url().toString(),
                    currentPartSize
            ));
        }

        return preSignedUrls;
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
