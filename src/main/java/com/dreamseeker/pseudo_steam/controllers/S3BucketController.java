package com.dreamseeker.pseudo_steam.controllers;

import com.dreamseeker.pseudo_steam.domains.BucketsPage;
import com.dreamseeker.pseudo_steam.exceptions.BucketDoesNotExistException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNameExistsException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNotEmptyException;
import com.dreamseeker.pseudo_steam.services.BucketService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/buckets")
@AllArgsConstructor
public class S3BucketController {

    private final BucketService bucketService;

    @PostMapping("/{bucketName}")
    public ResponseEntity<String> createBucket(@PathVariable String bucketName) throws BucketNameExistsException {
        BucketsPage.Bucket bucket = bucketService.createBucket(bucketName);
        return ResponseEntity.status(HttpStatus.OK).body("Bucket created with name: " + bucket.bucketName());
    }

    @GetMapping
    public ResponseEntity<BucketsPage> listAllBuckets(
            @RequestParam(required = false) String continuationToken,
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        return ResponseEntity.ok(bucketService.fetchBuckets(limit, continuationToken));
    }

    @DeleteMapping("/{bucketName}")
    public ResponseEntity<Void> deleteBucket(@PathVariable String bucketName) throws BucketNotEmptyException, BucketDoesNotExistException {
        bucketService.deleteBucket(bucketName);
        return ResponseEntity.status(HttpStatus.OK).build();
    }
}
