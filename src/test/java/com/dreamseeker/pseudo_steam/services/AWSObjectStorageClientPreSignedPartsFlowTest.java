package com.dreamseeker.pseudo_steam.services;

import com.dreamseeker.pseudo_steam.domains.*;
import com.dreamseeker.pseudo_steam.exceptions.BucketDoesNotExistException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNameExistsException;
import com.dreamseeker.pseudo_steam.utils.S3ClientUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;


@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AWSObjectStorageClientPreSignedPartsFlowTest {

    @Autowired
    private AWSObjectStorageClient awsObjectStorageClient;
    @Autowired
    private S3ClientUtils s3ClientUtils;

    private String studioId;
    private InitiateUploadResponse initiateUploadResponse;
    private byte[] content;
    private RestClient restClient;

    @BeforeAll
    void createBucket() throws BucketNameExistsException, IOException {
        BucketsPage.Bucket studioBucket = awsObjectStorageClient.createBucket("naughty-cat");
        studioId = studioBucket.bucketName();

        restClient = RestClient.builder().build();

        content = FileCopyUtils.copyToByteArray(new ClassPathResource("file_5mb.bin").getInputStream());
    }

    @Test
    @Order(1)
    void preSignedUrlsAreGeneratedSuccessfully() {
        InitiateUploadRequest initiateUploadRequest = new InitiateUploadRequest("the-last-of-us", content.length, "application/zip");
        initiateUploadResponse = awsObjectStorageClient.initiateUpload(studioId, initiateUploadRequest);

        assertThat(initiateUploadResponse.uploadId()).isNotNull();
        assertThat(initiateUploadResponse.presignedUrls()).isNotNull().isNotEmpty();

        System.out.println(initiateUploadResponse.uploadId());
        System.out.println(initiateUploadResponse.presignedUrls());
    }

    @Test
    @Order(2)
    void objectPartsAreUploadedSuccessfully() throws URISyntaxException {
        List<CompleteUploadRequest.CompletedPart> completedParts = new ArrayList<>();

        for (PreSignedPartUrl presignedPartUrl : initiateUploadResponse.presignedUrls()) {
            int partNumber = presignedPartUrl.partNumber();
            long partSize = presignedPartUrl.partSize();
            long startByte = (partNumber - 1) * partSize;
            long endByte = Math.min(startByte + partSize - 1, content.length - 1);

            byte[] partData = Arrays.copyOfRange(content, (int) startByte, (int) (endByte + 1));

            ResponseEntity<Void> response = restClient.put()
                    .uri(new URI(presignedPartUrl.preSignedUrl())) //URI prevents double encoding
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(partData)
                    .retrieve()
                    .toBodilessEntity();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            String etag = response.getHeaders().getETag();
            assertThat(etag).isNotNull();

            completedParts.add(new CompleteUploadRequest.CompletedPart(partNumber, etag));
            System.out.println("Uploaded part " + partNumber + " with ETag: " + etag);
        }

        CompleteUploadRequest completeUploadRequest = new CompleteUploadRequest(
                initiateUploadResponse.uploadId(),
                initiateUploadResponse.gameName(),
                completedParts
        );
        awsObjectStorageClient.completeUpload(studioId, completeUploadRequest);

        assertThat(s3ClientUtils.doesObjectExists(studioId, initiateUploadResponse.gameName())).isTrue();
    }

    @AfterAll
    void deleteBucket() throws BucketDoesNotExistException {
        awsObjectStorageClient.deleteBucket(studioId);
    }
}
