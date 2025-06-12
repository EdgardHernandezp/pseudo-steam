package com.dreamseeker.pseudo_steam.controllers;

import com.dreamseeker.pseudo_steam.domains.BucketsPage;
import com.dreamseeker.pseudo_steam.exceptions.BucketDoesNotExistException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNameExistsException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNotEmptyException;
import com.dreamseeker.pseudo_steam.services.StudiosService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/studios")
@AllArgsConstructor
public class StudiosController {

    private final StudiosService studiosService;

    @PostMapping("/{studio-name}")
    public ResponseEntity<BucketsPage.Bucket> createStudio(@PathVariable("studio-name") String studioName) throws BucketNameExistsException {
        BucketsPage.Bucket bucket = studiosService.createStudio(studioName);
        return ResponseEntity.status(HttpStatus.OK).body(bucket);
    }

    @GetMapping
    public ResponseEntity<BucketsPage> listAllStudios(
            @RequestParam(required = false) String continuationToken,
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        return ResponseEntity.ok(studiosService.fetchStudios(limit, continuationToken));
    }

    @DeleteMapping("/{studio-id}")
    public ResponseEntity<Void> deleteStudio(@PathVariable("studio-id") String studioId) throws BucketNotEmptyException, BucketDoesNotExistException {
        studiosService.deleteStudio(studioId);
        return ResponseEntity.status(HttpStatus.OK).build();
    }
}
