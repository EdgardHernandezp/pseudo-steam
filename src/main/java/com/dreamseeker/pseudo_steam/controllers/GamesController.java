package com.dreamseeker.pseudo_steam.controllers;

import com.dreamseeker.pseudo_steam.domains.ObjectUploadResponse;
import com.dreamseeker.pseudo_steam.exceptions.BucketDoesNotExistException;
import com.dreamseeker.pseudo_steam.exceptions.ObjectDoesNotExistsException;
import com.dreamseeker.pseudo_steam.services.GamesService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/studios/{studio-id}/games")
@AllArgsConstructor
public class GamesController {

    private final GamesService gamesService;

    @PostMapping("/{game-name}")
    public ResponseEntity<ObjectUploadResponse> uploadGame(
            @PathVariable("studio-id") String studioId,
            @PathVariable("game-name") String gameName,
            @RequestParam("file") MultipartFile file) throws BucketDoesNotExistException {
        ObjectUploadResponse objectUploadResponse = gamesService.uploadGame(studioId, gameName, file);
        return ResponseEntity.ok().body(objectUploadResponse);
    }

    @DeleteMapping("/{game-name}")
    public ResponseEntity<Void> deleteGame(
            @PathVariable("game-name") String gameName,
            @PathVariable("studio-id") String studioId) throws BucketDoesNotExistException, ObjectDoesNotExistsException {
        gamesService.deleteGame(studioId, gameName);
        return ResponseEntity.ok().build();
    }
}
