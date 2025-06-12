package com.dreamseeker.pseudo_steam.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@Service
public class S3ClientUtils {

    @Autowired
    S3Client s3Client;

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
}
