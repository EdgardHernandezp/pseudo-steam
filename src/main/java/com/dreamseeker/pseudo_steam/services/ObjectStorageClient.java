package com.dreamseeker.pseudo_steam.services;

import com.dreamseeker.pseudo_steam.domains.*;
import com.dreamseeker.pseudo_steam.exceptions.BucketDoesNotExistException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNameExistsException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNotEmptyException;
import com.dreamseeker.pseudo_steam.exceptions.ObjectDoesNotExistsException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface ObjectStorageClient {
    BucketsPage.Bucket createBucket(String bucketName) throws BucketNameExistsException;

    BucketsPage fetchBuckets(Integer limit, String continuationToken);

    void deleteBucket(String bucketName) throws BucketDoesNotExistException, BucketNotEmptyException;

    ObjectUploadResponse putObjectSinglePartUpload(String bucketName, String objectKey, MultipartFile file) throws BucketDoesNotExistException;

    ObjectUploadResponse putObjectMultiPartUpload(String bucketName, String objectKey, MultipartFile file);

    void getObject(String bucketName, String objectKey, String versionId) throws ObjectDoesNotExistsException, BucketDoesNotExistException;

    String deleteObject(String bucketName, String objectKey, String versionId) throws BucketDoesNotExistException, ObjectDoesNotExistsException;

    InitiateUploadResponse initiateUpload(String studioId, InitiateUploadRequest initiateUploadRequest);

    void completeUpload(String studioId, CompleteUploadRequest completeUploadRequest);

    GameInfo fetchObjectMetadata(String bucketName, String objectKey) throws ObjectDoesNotExistsException, BucketDoesNotExistException;

    GameInfo modifyObjectMetadata(String bucketName, String objectKey, Map<String, String> metadata) throws ObjectDoesNotExistsException, BucketDoesNotExistException;
}
