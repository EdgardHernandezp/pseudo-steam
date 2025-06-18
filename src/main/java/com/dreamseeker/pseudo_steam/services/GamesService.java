package com.dreamseeker.pseudo_steam.services;

import com.dreamseeker.pseudo_steam.domains.CompleteUploadRequest;
import com.dreamseeker.pseudo_steam.domains.InitiateUploadRequest;
import com.dreamseeker.pseudo_steam.domains.InitiateUploadResponse;
import com.dreamseeker.pseudo_steam.exceptions.BucketDoesNotExistException;
import com.dreamseeker.pseudo_steam.exceptions.ObjectDoesNotExistsException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class GamesService {
    private final ObjectStorageClient objectStorageClient;

    public void deleteGame(String studioId, String gameName) throws BucketDoesNotExistException, ObjectDoesNotExistsException {
        objectStorageClient.deleteObject(studioId, gameName, null);
    }

    public InitiateUploadResponse initiateGameUpload(String studioId, InitiateUploadRequest initiateUploadRequest) {
        return objectStorageClient.initiateUpload(studioId, initiateUploadRequest);
    }

    public void completeGameUpload(String studioId, CompleteUploadRequest completeUploadRequest) {
        objectStorageClient.completeUpload(studioId, completeUploadRequest);
    }
}
