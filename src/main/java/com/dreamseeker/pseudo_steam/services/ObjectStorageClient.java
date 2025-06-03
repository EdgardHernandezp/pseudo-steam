package com.dreamseeker.pseudo_steam.services;

import com.dreamseeker.pseudo_steam.domains.BucketsPage;
import com.dreamseeker.pseudo_steam.domains.ObjectUploadResponse;
import com.dreamseeker.pseudo_steam.exceptions.BucketDoesNotExistException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNameExistsException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNotEmptyException;
import org.springframework.web.multipart.MultipartFile;

public interface ObjectStorageClient {
    BucketsPage.Bucket createBucket(String bucketName) throws BucketNameExistsException;

    BucketsPage fetchBuckets(Integer limit, String continuationToken);

    void deleteBucket(String bucketName) throws BucketDoesNotExistException, BucketNotEmptyException;

    ObjectUploadResponse putObject(String studioId, String gameName, MultipartFile file) throws BucketDoesNotExistException;
}
