package com.dreamseeker.pseudo_steam.utils;

import com.dreamseeker.pseudo_steam.services.AWSObjectStorageClient;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Service
public final class AWSObjectStorageClientUtils extends AWSObjectStorageClient {
    public AWSObjectStorageClientUtils(S3Client s3Client, S3Presigner s3Presigner) {
        super(s3Client, s3Presigner);
    }

    public ListObjectVersionsResponse fetchListObjectVersions(String bucketName, String objectKey) {
        return s3Client.listObjectVersions(ListObjectVersionsRequest.builder().bucket(bucketName).prefix(objectKey).build());
    }

    public boolean doesObjectExists(String bucketName, String objectKey) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();
            s3Client.headObject(headRequest);
            return true;
        } catch (NoSuchKeyException | NoSuchBucketException e) {
            return false;
        } catch (S3Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean doesBucketExists(String bucketName) {
        try {
            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder().bucket(bucketName).build();
            s3Client.headBucket(headBucketRequest);
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        } catch (S3Exception e) {
            throw new RuntimeException(e);
        }
    }

    public GetBucketLifecycleConfigurationResponse fetchLifecycleConfigurationRules(String studioId) {
        return s3Client.getBucketLifecycleConfiguration(GetBucketLifecycleConfigurationRequest.builder().bucket(studioId).build());
    }

    public void assignBucketPolicy(String bucketName, String policy) {
        PutBucketPolicyRequest putBucketPolicyRequest = PutBucketPolicyRequest.builder()
                .bucket(bucketName)
                .policy(policy)
                .build();
        PutBucketPolicyResponse putBucketPolicyResponse = s3Client.putBucketPolicy(putBucketPolicyRequest);
        System.out.println("Policy configured:" + putBucketPolicyResponse.sdkHttpResponse().isSuccessful());
    }
}
