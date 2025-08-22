package com.dreamseeker.pseudo_steam.services;

import com.dreamseeker.pseudo_steam.domains.BucketsPage;
import com.dreamseeker.pseudo_steam.domains.ObjectUploadResponse;
import com.dreamseeker.pseudo_steam.exceptions.BucketDoesNotExistException;
import com.dreamseeker.pseudo_steam.exceptions.BucketNameExistsException;
import com.dreamseeker.pseudo_steam.exceptions.ObjectDoesNotExistsException;
import com.dreamseeker.pseudo_steam.utils.AWSObjectStorageClientUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3control.S3ControlClient;
import software.amazon.awssdk.services.s3control.model.*;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AccessPointsTest {
    public static final String ACCESS_POINT_NAME = "pseudo-steam-write-ap";
    @Autowired
    private AWSObjectStorageClientUtils awsObjectStorageIncorrectUser;
    private AWSObjectStorageClientUtils objectStorageClientRightUser;

    @Autowired
    private Environment environment;

    private S3ControlClient s3ControlClient;

    private String studioId;
    private String accessPointAlias;

    static final String singleUploadGameName = "gta-6";

    @BeforeAll
    void setUp() {
        s3ControlClient = S3ControlClient.builder()
                .region(Region.of(environment.getProperty("aws.region")))
                .build();

        StaticCredentialsProvider staticCredentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                        environment.getProperty("access.point.user.access.key"),
                        environment.getProperty("access.point.user.secret.key")
                )
        );
        S3Client s3Client = S3Client.builder().region(Region.of(environment.getProperty("aws.region"))).credentialsProvider(staticCredentialsProvider).build();
        S3Presigner s3Presigner = S3Presigner.builder().region(Region.of(environment.getProperty("aws.region"))).credentialsProvider(staticCredentialsProvider).build();
        objectStorageClientRightUser = new AWSObjectStorageClientUtils(s3Client, s3Presigner);
    }

    @Test
    @Order(1)
    void createBucketWithPolicy() throws BucketNameExistsException, IOException {
        BucketsPage.Bucket studioBucket = awsObjectStorageIncorrectUser.createBucket("naughty-cat");
        studioId = studioBucket.bucketName();
        assertThat(studioBucket).isNotNull();
        assertThat(studioBucket.bucketName()).isNotNull().isNotBlank();

        System.out.println("Bucket created with name: " + studioId);

        //Attach policy to bucket delegating access control to access point
        String policy = createPolicy("bucket-access-point-policy.json", Map.of("{bucket-name}", studioId));
        awsObjectStorageIncorrectUser.assignBucketPolicy(studioId, policy);

    }

    @Test
    @Order(2)
    void createAccessPoint() {
        //create access point
        CreateAccessPointResponse createAccessPointResponse = s3ControlClient.createAccessPoint(request ->
                request.name(ACCESS_POINT_NAME) ////explain naming rules
                        .bucket(studioId)
                        .accountId(environment.getProperty("aws.account"))
        );
        accessPointAlias = createAccessPointResponse.alias();
        System.out.printf("Generated access point alias: %s\n", accessPointAlias);

        //// S3 access point alias format: <access-point-name>-<random-suffix>-s3alias
        assertThat(accessPointAlias)
                .isNotNull()
                .isNotBlank()
                .endsWith("-s3alias");

        //attach policy to access point
        ////only users within an IAM group can do requests
        s3ControlClient.putAccessPointPolicy(request -> {
                    try {
                        Map<String, String> replacements = Map.of("{access-point-name}", ACCESS_POINT_NAME);
                        String policy = createPolicy("access-point-policy.json", replacements);
                        request.accountId(environment.getProperty("aws.account"))
                                .name(ACCESS_POINT_NAME)
                                .policy(policy);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
        );
    }

    @Test
    @Order(3)
    void objectOperations() throws IOException, BucketDoesNotExistException, ObjectDoesNotExistsException {
        //test with right user, upload object
        ObjectUploadResponse objectUploadResponse = uploadObject(objectStorageClientRightUser);
        assertThat(objectUploadResponse).isNotNull();

        //test with right user, download object
        assertThatThrownBy(() -> objectStorageClientRightUser.getObject(accessPointAlias, objectUploadResponse.gameName(), null))
                .isInstanceOf(AwsServiceException.class)
                .extracting(Throwable::getMessage)
                .satisfies(System.out::println);
        ;

        //test with user different from the one in the access point policy
        assertThatThrownBy(() -> uploadObject(awsObjectStorageIncorrectUser))
                .isInstanceOf(AwsServiceException.class)
                .extracting(Throwable::getMessage)
                .satisfies(System.out::println);
    }

    private String createPolicy(String filename, Map<String, String> replacements) throws IOException {
        String policy = new String(new ClassPathResource(filename).getContentAsByteArray());
        for (var entry : replacements.entrySet())
            policy = StringUtils.replace(policy, entry.getKey(), entry.getValue());

        return policy;
    }

    private ObjectUploadResponse uploadObject(AWSObjectStorageClientUtils awsObjectStorage) throws
            IOException, BucketDoesNotExistException {
        Resource resource = new ClassPathResource("file_1mb.bin");
        byte[] content = FileCopyUtils.copyToByteArray(resource.getInputStream());
        MultipartFile multipartFile = new MockMultipartFile("file_1mb.bin", "file_1mb.bin", "application/zip", content);
        return awsObjectStorage.putObjectSinglePartUpload(accessPointAlias, singleUploadGameName, multipartFile);
    }

    @AfterAll
    void afterAll() throws BucketDoesNotExistException {
        DeleteAccessPointResponse deleteAccessPointResponse = s3ControlClient.deleteAccessPoint(request ->
                request.name(ACCESS_POINT_NAME)
                        .accountId(environment.getProperty("aws.account"))
        );
        awsObjectStorageIncorrectUser.deleteBucket(studioId);

        System.out.printf(
                "Deleted access point\n name: %s\n account: %s\n status: %s %s",
                ACCESS_POINT_NAME,
                environment.getProperty("aws.account"),
                deleteAccessPointResponse.sdkHttpResponse().statusCode(),
                deleteAccessPointResponse.sdkHttpResponse().statusText());
    }
}
