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
public class StudiosService {
    private final ObjectStorageClient objectStorageClient;

    public BucketsPage.Bucket createStudio(String studioName) throws BucketNameExistsException {
        return objectStorageClient.createBucket(studioName);
    }

    public BucketsPage fetchStudios(Integer limit, String continuationToken) {
        return objectStorageClient.fetchBuckets(limit, continuationToken);
    }

    public void deleteStudio(String studioId) throws BucketNotEmptyException, BucketDoesNotExistException {
        objectStorageClient.deleteBucket(studioId);
    }
}
