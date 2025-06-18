package com.dreamseeker.pseudo_steam.controllers;

import com.dreamseeker.pseudo_steam.domains.CompleteUploadRequest;
import com.dreamseeker.pseudo_steam.domains.InitiateUploadRequest;
import com.dreamseeker.pseudo_steam.domains.InitiateUploadResponse;
import com.dreamseeker.pseudo_steam.exceptions.BucketDoesNotExistException;
import com.dreamseeker.pseudo_steam.exceptions.ObjectDoesNotExistsException;
import com.dreamseeker.pseudo_steam.services.GamesService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/studios/{studio-id}/games")
@AllArgsConstructor
public class GamesController {

    private final GamesService gamesService;

    @PostMapping("/initiate")
    public ResponseEntity<InitiateUploadResponse> initiateGameUpload(@PathVariable("studio-id") String studioId, @RequestBody InitiateUploadRequest initiateUploadRequest) {
        InitiateUploadResponse objectUploadResponse = gamesService.initiateGameUpload(studioId, initiateUploadRequest);
        return ResponseEntity.ok().body(objectUploadResponse);
    }

    @PostMapping("/complete")
    public ResponseEntity<Void> completeGameUpload(@PathVariable("studio-id") String studioId, @RequestBody CompleteUploadRequest completeGameUpload) {
            gamesService.completeGameUpload(studioId, completeGameUpload);
            return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{game-name}")
    public ResponseEntity<Void> deleteGame(
            @PathVariable("game-name") String gameName,
            @PathVariable("studio-id") String studioId) throws BucketDoesNotExistException, ObjectDoesNotExistsException {
        gamesService.deleteGame(studioId, gameName);
        return ResponseEntity.ok().build();
    }
}
