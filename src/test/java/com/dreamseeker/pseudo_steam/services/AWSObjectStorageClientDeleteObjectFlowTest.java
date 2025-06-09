package com.dreamseeker.pseudo_steam.services;

import com.dreamseeker.pseudo_steam.domains.BucketsPage;
import com.dreamseeker.pseudo_steam.exceptions.BucketDoesNotExistException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNameExistsException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNotEmptyException;
import com.dreamseeker.pseudo_steam.exceptions.ObjectDoesNotExistsException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AWSObjectStorageClientDeleteObjectFlowTest {

    @Autowired
    S3Client s3Client;

    @Autowired
    private AWSObjectStorageClient awsObjectStorageClient;
    private String studioId;
    private static final String gameName = "the-first-of-us";
    private String deleteMarkerVersionId;
    private String versionId;

    @BeforeAll
    void createBucketAndUploadItem() throws BucketNameExistsException, IOException, BucketDoesNotExistException {
        BucketsPage.Bucket studioBucket = awsObjectStorageClient.createBucket("naughty-cat");
        studioId = studioBucket.bucketName();

        byte[] content = FileCopyUtils.copyToByteArray(new ClassPathResource("file_1mb.bin").getInputStream());
        MultipartFile multipartFile = new MockMultipartFile("file_1mb.bin", "file_1mb.bin", "application/zip", content);
        versionId = awsObjectStorageClient.putObjectSinglePartUpload(studioId, gameName, multipartFile).versionId();
    }

    @Test
    @Order(1)
    void deleteObjectSuccessfully() throws BucketDoesNotExistException, ObjectDoesNotExistsException {
        assertThat(doesObjectExists(studioId, gameName)).isTrue();

        deleteMarkerVersionId = awsObjectStorageClient.deleteObject(studioId, gameName, null);

        assertThat(doesObjectExists(studioId, gameName)).isFalse();

        ListObjectVersionsResponse listObjectVersionsResponse = fetchListObjectVersions();
        assertThat(listObjectVersionsResponse.versions()).hasSize(1);
        assertThat(listObjectVersionsResponse.deleteMarkers()).hasSize(1);
        assertThat(listObjectVersionsResponse.hasDeleteMarkers()).isTrue();
    }

    @Test
    @Order(2)
    void removeDeleteMarkerMakesObjectReappear() throws ObjectDoesNotExistsException, BucketDoesNotExistException {
        awsObjectStorageClient.deleteObject(studioId, gameName, deleteMarkerVersionId);

        assertThat(doesObjectExists(studioId, gameName)).isTrue();

        ListObjectVersionsResponse listObjectVersionsResponse = fetchListObjectVersions();
        assertThat(listObjectVersionsResponse.versions()).hasSize(1);
        assertThat(listObjectVersionsResponse.deleteMarkers()).hasSize(0);
        assertThat(listObjectVersionsResponse.hasDeleteMarkers()).isFalse();
    }

    @Test
    @Order(3)
    void deleteObjectByVersionIdRemovesItPermanently() throws ObjectDoesNotExistsException, BucketDoesNotExistException {
        awsObjectStorageClient.deleteObject(studioId, gameName, versionId);

        assertThat(doesObjectExists(studioId, gameName)).isFalse();

        ListObjectVersionsResponse listObjectVersionsResponse = fetchListObjectVersions();
        assertThat(listObjectVersionsResponse.versions()).hasSize(0);
        assertThat(listObjectVersionsResponse.hasVersions()).isFalse();
        assertThat(listObjectVersionsResponse.hasDeleteMarkers()).isFalse();
        assertThat(listObjectVersionsResponse.deleteMarkers()).hasSize(0);
    }

    private ListObjectVersionsResponse fetchListObjectVersions() {
        return s3Client.listObjectVersions(ListObjectVersionsRequest.builder().bucket(studioId).prefix(gameName).build());
    }

    private boolean doesObjectExists(String bucketName, String objectKey) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();
            s3Client.headObject(headRequest);
            return true;
        } catch (NoSuchKeyException | NoSuchBucketException e) {
            return false;
        } catch (S3Exception e) {
            throw new RuntimeException(e);
        }
    }

    @AfterAll
    void deleteBucket() throws BucketNotEmptyException, BucketDoesNotExistException {
        awsObjectStorageClient.deleteBucket(studioId);
    }
}
