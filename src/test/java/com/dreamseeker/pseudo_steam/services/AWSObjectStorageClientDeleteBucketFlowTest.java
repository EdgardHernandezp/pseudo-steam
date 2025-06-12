package com.dreamseeker.pseudo_steam.services;

import com.dreamseeker.pseudo_steam.domains.BucketsPage;
import com.dreamseeker.pseudo_steam.exceptions.BucketDoesNotExistException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNameExistsException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNotEmptyException;
import com.dreamseeker.pseudo_steam.exceptions.ObjectDoesNotExistsException;
import com.dreamseeker.pseudo_steam.utils.S3ClientUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AWSObjectStorageClientDeleteBucketFlowTest {

    @Autowired
    private AWSObjectStorageClient awsObjectStorageClient;

    @Autowired
    private S3ClientUtils s3ClientUtils;

    private String studioId;
    private static final String gameName1 = "cyberpunk-2025";
    private static final String gameName2 = "the-witcher-10";

    @BeforeAll
    void setup() throws BucketNameExistsException, IOException, BucketDoesNotExistException, ObjectDoesNotExistsException {
        BucketsPage.Bucket studioBucket = awsObjectStorageClient.createBucket("cd-project-blue");
        studioId = studioBucket.bucketName();

        byte[] content = FileCopyUtils.copyToByteArray(new ClassPathResource("file_1mb.bin").getInputStream());
        MultipartFile multipartFile = new MockMultipartFile("file_1mb.bin", "file_1mb.bin", "application/zip", content);
        awsObjectStorageClient.putObjectSinglePartUpload(studioId, gameName1, multipartFile);
        awsObjectStorageClient.putObjectSinglePartUpload(studioId, gameName2, multipartFile);

        awsObjectStorageClient.deleteObject(studioId, gameName2, null);

        ListObjectVersionsResponse listObjectVersionsResponse = s3ClientUtils.fetchListObjectVersions(studioId, null);
        assertThat(listObjectVersionsResponse.versions()).hasSize(2);
        assertThat(listObjectVersionsResponse.deleteMarkers()).hasSize(1);
    }

    @Test
    @Disabled
    void deletingNonEmptyBucketReturnsError() {
        assertThatThrownBy(() -> awsObjectStorageClient.deleteBucket(studioId)).isInstanceOf(BucketNotEmptyException.class);
    }

    @Test
    void deletingBucketSuccessfully() throws BucketDoesNotExistException {
        assertThat(s3ClientUtils.doesBucketExists(studioId)).isTrue();
        awsObjectStorageClient.deleteBucket(studioId);
        assertThat(s3ClientUtils.doesBucketExists(studioId)).isFalse();
    }
}
