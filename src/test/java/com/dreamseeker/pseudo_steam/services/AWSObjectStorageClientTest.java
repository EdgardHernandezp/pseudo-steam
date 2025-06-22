package com.dreamseeker.pseudo_steam.services;

import com.dreamseeker.pseudo_steam.domains.BucketsPage;
import com.dreamseeker.pseudo_steam.domains.ObjectUploadResponse;
import com.dreamseeker.pseudo_steam.exceptions.BucketDoesNotExistException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNameExistsException;
import com.dreamseeker.pseudo_steam.exceptions.ObjectDoesNotExistsException;
import com.dreamseeker.pseudo_steam.utils.S3ClientUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.model.GetBucketLifecycleConfigurationResponse;
import software.amazon.awssdk.services.s3.model.LifecycleRule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AWSObjectStorageClientTest {
    @Autowired
    private AWSObjectStorageClient awsObjectStorageClient;

    @Autowired
    S3ClientUtils s3ClientUtils;

    private String studioId;

    static final String singleUploadGameName = "gta-6";
    private String versionId;

    @Test
    @Order(1)
    void createBucketStudio() throws BucketNameExistsException {
        BucketsPage.Bucket studioBucket = awsObjectStorageClient.createBucket("naughty-cat");
        studioId = studioBucket.bucketName();
        assertThat(studioBucket).isNotNull();
        assertThat(studioBucket.bucketName()).isNotNull().isNotBlank();

        GetBucketLifecycleConfigurationResponse getBucketLifecycleConfigurationResponse = s3ClientUtils.fetchLifecycleConfigurationRules(studioId);
        List<LifecycleRule> rules = getBucketLifecycleConfigurationResponse.rules();
        assertThat(rules).isNotEmpty().hasSize(1);
        LifecycleRule rule = rules.getFirst();
        assertThat(rule.id()).isEqualTo("Non_current_version_rules");
        assertThat(rule.hasNoncurrentVersionTransitions()).isTrue();
        assertThat(rule.noncurrentVersionExpiration().newerNoncurrentVersions()).isEqualTo(5);
    }

    @Test
    @Order(2)
    void putObjectSinglePartUploadSuccessfully() throws IOException, BucketDoesNotExistException {
        Resource resource = new ClassPathResource("file_1mb.bin");
        byte[] content = FileCopyUtils.copyToByteArray(resource.getInputStream());
        MultipartFile multipartFile = new MockMultipartFile("file_1mb.bin", "file_1mb.bin", "application/zip", content);

        ObjectUploadResponse response = awsObjectStorageClient.putObjectSinglePartUpload(studioId, singleUploadGameName, multipartFile);
        versionId = response.versionId();

        assertThat(response).isNotNull();
        assertThat(response.gameName()).isEqualTo(singleUploadGameName);
        assertThat(response.studioId()).isEqualTo(studioId);
        assertThat(response.versionId()).isNotNull();
    }

    @Test
    @Order(3)
    @Disabled
    void putObjectMultipartUploadSuccessfully() throws IOException {
        Resource resource = new ClassPathResource("file_100mb.bin");
        byte[] content = FileCopyUtils.copyToByteArray(resource.getInputStream());
        String gameName = "gta-7";
        ObjectUploadResponse objectUploadResponse = awsObjectStorageClient.putObjectMultiPartUpload(
                studioId,
                gameName,
                new MockMultipartFile("file_100mb.bin", "file_100mb.bin", "application/zip", content)
        );

        assertThat(objectUploadResponse).isNotNull();
        assertThat(objectUploadResponse.gameName()).isEqualTo(gameName);
        assertThat(objectUploadResponse.studioId()).isEqualTo(studioId);
        assertThat(objectUploadResponse.versionId()).isNotNull();
    }

    @Test
    @Order(4)
    void downloadObjectSuccessfully() throws ObjectDoesNotExistsException, BucketDoesNotExistException {
        awsObjectStorageClient.getObject(studioId, singleUploadGameName, versionId);

        Path downloadedFilePath = Path.of("downloads", singleUploadGameName);
        assertThat(Files.exists(downloadedFilePath)).isTrue();
    }

    @Test
    @Order(5)
    void downloadObjectFailsObjectDoesNotExists() {
        assertThatThrownBy(() -> awsObjectStorageClient.getObject(studioId, "non-existing-game", null))
                .isInstanceOf(ObjectDoesNotExistsException.class);
    }

    @Test
    @Order(6)
    void downloadObjectSuccessfullyWithoutVersionId() throws ObjectDoesNotExistsException, BucketDoesNotExistException {
        awsObjectStorageClient.getObject(studioId, singleUploadGameName, null);

        Path downloadedFilePath = Path.of("downloads", singleUploadGameName);
        assertThat(Files.exists(downloadedFilePath)).isTrue();
    }

    @Test
    void clean() throws BucketDoesNotExistException, IOException {
        awsObjectStorageClient.deleteBucket(studioId);

        Files.delete(Path.of("downloads", singleUploadGameName));
    }
}