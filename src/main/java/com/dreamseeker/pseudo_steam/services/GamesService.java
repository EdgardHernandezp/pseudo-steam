package com.dreamseeker.pseudo_steam.services;

import com.dreamseeker.pseudo_steam.domains.ObjectUploadResponse;
import com.dreamseeker.pseudo_steam.exceptions.BucketDoesNotExistException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@AllArgsConstructor
public class GamesService {
    private static final long MULTIPART_THRESHOLD = 100 * 1024 * 1024;

    private final ObjectStorageClient objectStorageClient;

    public ObjectUploadResponse uploadGame(String studioId, String gameName, MultipartFile file) throws BucketDoesNotExistException {
        if (file.getSize() >= MULTIPART_THRESHOLD)
            return objectStorageClient.putObjectMultiPartUpload(studioId, gameName, file);
        else
            return objectStorageClient.putObjectSinglePartUpload(studioId, gameName, file);
    }

}
