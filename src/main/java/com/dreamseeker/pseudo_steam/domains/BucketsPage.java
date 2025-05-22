package com.dreamseeker.pseudo_steam.domains;

import java.time.Instant;
import java.util.List;

public record BucketsPage(String continuationToken, List<Bucket> buckets) {
    public record Bucket(String bucketName, Instant creationDate) {
    }
}
