package com.dreamseeker.pseudo_steam.services;

import com.dreamseeker.pseudo_steam.domains.BucketsPage;
import com.dreamseeker.pseudo_steam.exceptions.BucketDoesNotExistException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNameExistsException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNotEmptyException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class BucketService {
    private final ObjectStorageClient objectStorageClient;

    public BucketsPage.Bucket createBucket(String bucketName) throws BucketNameExistsException {
        return objectStorageClient.createBucket(bucketName);
    }

    public BucketsPage fetchBuckets(Integer limit, String continuationToken) {
        return objectStorageClient.fetchBuckets(limit, continuationToken);
    }

    public void deleteBucket(String bucketName) throws BucketNotEmptyException, BucketDoesNotExistException {
        objectStorageClient.deleteBucket(bucketName);
    }
}
